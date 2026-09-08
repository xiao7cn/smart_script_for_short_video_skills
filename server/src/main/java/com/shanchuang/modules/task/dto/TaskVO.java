package com.shanchuang.modules.task.dto;

import com.shanchuang.modules.task.entity.Task;
import com.shanchuang.modules.task.entity.TaskItem;

import java.time.LocalDateTime;
import java.util.List;

/** 任务详情，供前端 2 秒轮询。字段名与 docs/接口设计.md 6.2 一致 */
public record TaskVO(
        String taskNo,
        String type,
        String status,
        Integer total,
        Integer doneCount,
        Integer failCount,
        List<ItemVO> items,
        Integer creditsSettled,
        String errorCode,
        String errorMsg,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {

    public record ItemVO(Integer idx, String status, Long scriptId, String errorMsg) {
    }

    public static TaskVO of(Task task, List<TaskItem> items) {
        List<ItemVO> itemVOs = items.stream()
                .map(i -> new ItemVO(i.getIdx(), i.getStatus(), i.getScriptId(), i.getErrorMsg()))
                .toList();
        return new TaskVO(task.getTaskNo(), task.getType(), task.getStatus(),
                task.getTotal(), task.getDoneCount(), task.getFailCount(), itemVOs,
                task.getCreditsSettled(), task.getErrorCode(), task.getErrorMsg(),
                task.getStartedAt(), task.getFinishedAt());
    }
}
