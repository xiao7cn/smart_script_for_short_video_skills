package com.shanchuang.modules.teardown.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.result.PageData;
import com.shanchuang.common.util.TextUtil;
import com.shanchuang.modules.aiconfig.service.AiGateway;
import com.shanchuang.modules.credit.service.CreditService;
import com.shanchuang.modules.file.service.FileStorageService;
import com.shanchuang.modules.task.TaskStatus;
import com.shanchuang.modules.task.entity.Task;
import com.shanchuang.modules.task.service.TaskService;
import com.shanchuang.modules.teardown.dto.TeardownDtos.SubmitRequest;
import com.shanchuang.modules.teardown.dto.TeardownDtos.SubmitResponse;
import com.shanchuang.modules.teardown.dto.TeardownDtos.TeardownListItem;
import com.shanchuang.modules.teardown.dto.TeardownDtos.TeardownVO;
import com.shanchuang.modules.teardown.entity.Teardown;
import com.shanchuang.modules.teardown.mapper.TeardownMapper;
import com.shanchuang.workflow.HarnessPort;
import com.shanchuang.workflow.PromptBuilder;
import com.shanchuang.workflow.TeardownPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 爆款拆解。取件失败时状态是 NEED_FILE 而不是 FAILED——等用户补录屏后能续跑 */
@Service
public class TeardownService {

    private static final Logger log = LoggerFactory.getLogger(TeardownService.class);

    private final TeardownMapper mapper;
    private final TaskService taskService;
    private final CreditService creditService;
    private final AiGateway aiGateway;
    private final PromptBuilder promptBuilder;
    private final FileStorageService fileStorage;
    private final TaskExecutor taskExecutor;

    public TeardownService(TeardownMapper mapper, TaskService taskService, CreditService creditService,
                           AiGateway aiGateway, PromptBuilder promptBuilder,
                           FileStorageService fileStorage,
                           @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.mapper = mapper;
        this.taskService = taskService;
        this.creditService = creditService;
        this.aiGateway = aiGateway;
        this.promptBuilder = promptBuilder;
        this.fileStorage = fileStorage;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public SubmitResponse submit(Long userId, SubmitRequest request, String requestId) {
        boolean hasUrl = !TextUtil.isBlank(request.url());
        boolean hasText = !TextUtil.isBlank(request.text());
        boolean hasFile = !TextUtil.isBlank(request.fileId());
        if (!hasUrl && !hasText && !hasFile) {
            throw BizException.badRequest("请提供视频链接、录屏文件或口播原文");
        }
        if (hasText && TextUtil.cnWords(request.text()) < 50) {
            throw BizException.of(4004, "原文过短，无法拆解");
        }

        Task existing = taskService.findByRequestId(userId, requestId);
        if (existing != null) {
            Teardown row = mapper.selectOne(Wrappers.<Teardown>lambdaQuery()
                    .eq(Teardown::getTaskId, existing.getId()).last("limit 1"));
            return new SubmitResponse(existing.getTaskNo(), row == null ? null : row.getId());
        }

        int accepted = creditService.hold(userId, 1, "pending");
        if (accepted <= 0) {
            throw BizException.of(3001, "额度不足，请先充值");
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("url", request.url());
        snapshot.put("fileId", request.fileId());
        snapshot.put("hasText", hasText);

        Task task = taskService.create(userId, TaskStatus.TYPE_EXTRACT, requestId, 1, snapshot, 1);
        taskService.createItems(task.getId(), List.of(Map.of("idx", 0)));

        Teardown row = new Teardown();
        row.setUserId(userId);
        row.setTaskId(task.getId());
        row.setSourceUrl(request.url());
        row.setSourceType(hasText ? "text" : hasFile ? "file" : "url");
        row.setVideoTitle(request.videoTitle());
        row.setStatus(TaskStatus.PENDING);
        if (hasText) {
            row.setTranscript(request.text());
            row.setPlatform("manual");
        }
        mapper.insert(row);

        String input = hasFile ? fileStorage.absolutePath(userId, request.fileId()) : request.url();
        taskExecutor.execute(() -> run(task.getId(), userId, task.getTaskNo(), row.getId(),
                input, request.text(), request.videoTitle()));

        return new SubmitResponse(task.getTaskNo(), row.getId());
    }

    void run(Long taskId, Long userId, String taskNo, Long teardownId,
             String input, String pastedText, String videoTitle) {
        taskService.markRunning(taskId);
        var items = taskService.items(taskId);
        Long itemId = items.isEmpty() ? null : items.get(0).getId();

        try {
            HarnessPort port = aiGateway.portFor(userId, taskId);
            TeardownPipeline pipeline = new TeardownPipeline(port, promptBuilder);
            TeardownPipeline.Result result = pipeline.produce(input, pastedText, videoTitle);

            Teardown patch = new Teardown();
            patch.setId(teardownId);
            patch.setVideoTitle(result.videoTitle());
            patch.setPlatform(result.platform());
            patch.setDurationSec(result.durationSec());
            patch.setWords(result.words() != null ? result.words() : TextUtil.cnWords(result.transcript()));
            patch.setSpeechRate(result.speechRate());
            patch.setTranscript(result.transcript());
            patch.setTeardownJson(result.teardownJson());
            patch.setFrameworkJson(result.frameworkJson());

            if (result.needFile()) {
                // 不算失败：等用户补素材后从转写续跑，额度也先退回
                patch.setStatus(TaskStatus.NEED_FILE);
                patch.setFetchGuide(result.fetchGuide());
                mapper.updateById(patch);
                creditService.refundOne(userId, taskNo);
                if (itemId != null) {
                    taskService.markItemFailed(itemId, taskId, "NEED_FILE", "需要用户提供录屏文件");
                }
                taskService.fail(taskId, "NEED_FILE", result.fetchGuide());
                return;
            }

            patch.setStatus(TaskStatus.SUCCESS);
            mapper.updateById(patch);
            creditService.consumeOne(userId, taskNo);
            if (itemId != null) {
                taskService.markItemSuccess(itemId, taskId, null);
            }
            taskService.finish(taskId);
        } catch (Exception e) {
            log.warn("拆解任务 {} 失败: {}", taskNo, e.getMessage());
            Teardown patch = new Teardown();
            patch.setId(teardownId);
            patch.setStatus(TaskStatus.FAILED);
            mapper.updateById(patch);
            creditService.refundOne(userId, taskNo);
            if (itemId != null) {
                taskService.markItemFailed(itemId, taskId, "INTERNAL", e.getMessage());
            }
            taskService.fail(taskId, "INTERNAL", e.getMessage());
        }
    }

    /** 用户补了素材，从转写这一步续跑，不重复取件 */
    public SubmitResponse continueWithMaterial(Long userId, Long teardownId, String fileId) {
        Teardown row = requireOwned(userId, teardownId);
        if (!TaskStatus.NEED_FILE.equals(row.getStatus())) {
            throw BizException.of(3004, "当前状态不需要补充素材");
        }
        if (TextUtil.isBlank(fileId)) {
            throw BizException.badRequest("请提供已上传的文件 id");
        }

        int accepted = creditService.hold(userId, 1, "pending");
        if (accepted <= 0) {
            throw BizException.of(3001, "额度不足，请先充值");
        }

        Task task = taskService.create(userId, TaskStatus.TYPE_EXTRACT, null, 1,
                Map.of("teardownId", teardownId, "fileId", fileId), 1);
        taskService.createItems(task.getId(), List.of(Map.of("idx", 0)));

        Teardown patch = new Teardown();
        patch.setId(teardownId);
        patch.setStatus(TaskStatus.PENDING);
        patch.setSourceType("file");
        patch.setTaskId(task.getId());
        mapper.updateById(patch);

        String path = fileStorage.absolutePath(userId, fileId);
        taskExecutor.execute(() -> run(task.getId(), userId, task.getTaskNo(), teardownId,
                path, null, row.getVideoTitle()));
        return new SubmitResponse(task.getTaskNo(), teardownId);
    }

    public TeardownVO detail(Long userId, Long id) {
        return TeardownVO.of(requireOwned(userId, id));
    }

    public PageData<TeardownListItem> page(Long userId, long page, long size) {
        IPage<Teardown> result = mapper.selectPage(new Page<>(page, size),
                Wrappers.<Teardown>lambdaQuery()
                        .select(Teardown::getId, Teardown::getPlatform, Teardown::getVideoTitle,
                                Teardown::getDurationSec, Teardown::getWords, Teardown::getStatus,
                                Teardown::getCreatedAt)
                        .eq(Teardown::getUserId, userId)
                        .orderByDesc(Teardown::getCreatedAt));
        return PageData.of(result.getRecords().stream().map(TeardownListItem::of).toList(),
                result.getTotal(), page, size);
    }

    public Teardown requireOwned(Long userId, Long id) {
        Teardown row = mapper.selectById(id);
        if (row == null) {
            throw BizException.notFound("拆解记录不存在");
        }
        if (!row.getUserId().equals(userId)) {
            throw BizException.forbidden("无权访问该拆解记录");
        }
        return row;
    }
}
