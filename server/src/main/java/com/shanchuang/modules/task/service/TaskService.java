package com.shanchuang.modules.task.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.result.PageData;
import com.shanchuang.common.util.IdUtil;
import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.modules.credit.service.CreditService;
import com.shanchuang.modules.task.TaskStatus;
import com.shanchuang.modules.task.dto.TaskVO;
import com.shanchuang.modules.task.entity.Task;
import com.shanchuang.modules.task.entity.TaskItem;
import com.shanchuang.modules.task.mapper.TaskItemMapper;
import com.shanchuang.modules.task.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务的建、查、取消与终态收敛。
 *
 * 任务状态既是执行状态也是前端轮询的数据源，所以一律落库；
 * 首版不引 MQ——任务状态本来就要落库，MQ 会多出一份需要对齐的状态源。
 */
@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskItemMapper itemMapper;
    private final CreditService creditService;

    public TaskService(TaskMapper taskMapper, TaskItemMapper itemMapper, CreditService creditService) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.creditService = creditService;
    }

    /** 幂等：同一个 requestId 重复提交返回首次建的那条 */
    public Task findByRequestId(Long userId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return taskMapper.selectOne(Wrappers.<Task>lambdaQuery()
                .eq(Task::getUserId, userId)
                .eq(Task::getRequestId, requestId)
                .last("limit 1"));
    }

    public long countRunning(Long userId) {
        return taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                .eq(Task::getUserId, userId)
                .in(Task::getStatus, TaskStatus.PENDING, TaskStatus.RUNNING));
    }

    @Transactional
    public Task create(Long userId, String type, String requestId, int total,
                       Object paramSnapshot, int creditsHold) {
        Task task = new Task();
        task.setTaskNo(IdUtil.taskNo());
        task.setRequestId(requestId);
        task.setUserId(userId);
        task.setType(type);
        task.setStatus(TaskStatus.PENDING);
        task.setTotal(total);
        task.setDoneCount(0);
        task.setFailCount(0);
        task.setParamJson(JsonUtil.toJson(paramSnapshot));
        task.setCreditsHold(creditsHold);
        task.setCreditsSettled(0);
        taskMapper.insert(task);
        return task;
    }

    @Transactional
    public void createItems(Long taskId, List<?> perItemParams) {
        for (int i = 0; i < perItemParams.size(); i++) {
            TaskItem item = new TaskItem();
            item.setTaskId(taskId);
            item.setIdx(i);
            item.setStatus(TaskStatus.PENDING);
            item.setParamJson(JsonUtil.toJson(perItemParams.get(i)));
            item.setRetryCount(0);
            itemMapper.insert(item);
        }
    }

    public Task requireOwned(Long userId, String taskNo) {
        Task task = taskMapper.selectOne(Wrappers.<Task>lambdaQuery()
                .eq(Task::getTaskNo, taskNo)
                .last("limit 1"));
        if (task == null) {
            throw BizException.of(3003, "任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw BizException.forbidden("无权访问该任务");
        }
        return task;
    }

    public TaskVO detail(Long userId, String taskNo) {
        Task task = requireOwned(userId, taskNo);
        List<TaskItem> items = itemMapper.selectList(Wrappers.<TaskItem>lambdaQuery()
                .eq(TaskItem::getTaskId, task.getId())
                .orderByAsc(TaskItem::getIdx));
        return TaskVO.of(task, items);
    }

    public PageData<TaskVO> page(Long userId, String type, long page, long size) {
        IPage<Task> result = taskMapper.selectPage(new Page<>(page, size),
                Wrappers.<Task>lambdaQuery()
                        .eq(Task::getUserId, userId)
                        .eq(type != null && !type.isBlank(), Task::getType, type)
                        .orderByDesc(Task::getCreatedAt));
        List<TaskVO> records = result.getRecords().stream()
                .map(t -> TaskVO.of(t, List.of()))
                .toList();
        return PageData.of(records, result.getTotal(), page, size);
    }

    public void markRunning(Long taskId) {
        Task patch = new Task();
        patch.setId(taskId);
        patch.setStatus(TaskStatus.RUNNING);
        patch.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(patch);
    }

    public void markItemRunning(Long itemId, Object param) {
        TaskItem patch = new TaskItem();
        patch.setId(itemId);
        patch.setStatus(TaskStatus.RUNNING);
        if (param != null) {
            patch.setParamJson(JsonUtil.toJson(param));
        }
        itemMapper.updateById(patch);
    }

    public void markItemSuccess(Long itemId, Long taskId, Long scriptId) {
        TaskItem patch = new TaskItem();
        patch.setId(itemId);
        patch.setStatus(TaskStatus.SUCCESS);
        patch.setScriptId(scriptId);
        itemMapper.updateById(patch);
        taskMapper.incrementDone(taskId);
    }

    public void markItemFailed(Long itemId, Long taskId, String errorCode, String errorMsg) {
        TaskItem patch = new TaskItem();
        patch.setId(itemId);
        patch.setStatus(TaskStatus.FAILED);
        patch.setErrorCode(errorCode);
        patch.setErrorMsg(errorMsg == null ? null : errorMsg.substring(0, Math.min(480, errorMsg.length())));
        itemMapper.updateById(patch);
        taskMapper.incrementFail(taskId);
    }

    /**
     * 收敛终态并对账。
     *
     * 全成 SUCCESS、部分成 PARTIAL、全败 FAILED。
     * 残留的 hold 一律退回：额度是预扣的，异常中断时不退就等于吞了用户的钱。
     */
    @Transactional
    public void finish(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || TaskStatus.isTerminal(task.getStatus())) {
            return;
        }
        int done = task.getDoneCount() == null ? 0 : task.getDoneCount();
        int total = task.getTotal() == null ? 0 : task.getTotal();

        String status;
        if (done == 0) {
            status = TaskStatus.FAILED;
        } else if (done < total) {
            status = TaskStatus.PARTIAL;
        } else {
            status = TaskStatus.SUCCESS;
        }

        int hold = task.getCreditsHold() == null ? 0 : task.getCreditsHold();
        int remaining = Math.max(0, hold - done);
        if (remaining > 0) {
            creditService.settle(task.getUserId(), task.getTaskNo(), remaining);
        }

        Task patch = new Task();
        patch.setId(taskId);
        patch.setStatus(status);
        patch.setCreditsSettled(done);
        patch.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(patch);
    }

    @Transactional
    public void fail(Long taskId, String errorCode, String errorMsg) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || TaskStatus.isTerminal(task.getStatus())) {
            return;
        }
        int hold = task.getCreditsHold() == null ? 0 : task.getCreditsHold();
        int done = task.getDoneCount() == null ? 0 : task.getDoneCount();
        int remaining = Math.max(0, hold - done);
        if (remaining > 0) {
            creditService.settle(task.getUserId(), task.getTaskNo(), remaining);
        }

        Task patch = new Task();
        patch.setId(taskId);
        patch.setStatus(done > 0 ? TaskStatus.PARTIAL : TaskStatus.FAILED);
        patch.setErrorCode(errorCode);
        patch.setErrorMsg(errorMsg == null ? null : errorMsg.substring(0, Math.min(480, errorMsg.length())));
        patch.setCreditsSettled(done);
        patch.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(patch);
    }

    @Transactional
    public void cancel(Long userId, String taskNo) {
        Task task = requireOwned(userId, taskNo);
        if (TaskStatus.isTerminal(task.getStatus())) {
            throw BizException.of(3004, "任务已结束，不可重复操作");
        }
        int hold = task.getCreditsHold() == null ? 0 : task.getCreditsHold();
        int done = task.getDoneCount() == null ? 0 : task.getDoneCount();
        int remaining = Math.max(0, hold - done);
        if (remaining > 0) {
            creditService.settle(userId, taskNo, remaining);
        }

        Task patch = new Task();
        patch.setId(task.getId());
        patch.setStatus(TaskStatus.CANCELLED);
        patch.setCreditsSettled(done);
        patch.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(patch);
    }

    public Map<String, Object> paramSnapshot(Task task) {
        return JsonUtil.toMap(task.getParamJson());
    }

    public List<TaskItem> items(Long taskId) {
        return itemMapper.selectList(Wrappers.<TaskItem>lambdaQuery()
                .eq(TaskItem::getTaskId, taskId)
                .orderByAsc(TaskItem::getIdx));
    }
}
