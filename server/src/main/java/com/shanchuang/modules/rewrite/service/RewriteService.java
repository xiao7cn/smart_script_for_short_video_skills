package com.shanchuang.modules.rewrite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.result.PageData;
import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.common.util.TextUtil;
import com.shanchuang.modules.aiconfig.service.AiGateway;
import com.shanchuang.modules.credit.service.CreditService;
import com.shanchuang.modules.persona.service.PersonaService;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.PublishResponse;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.RewriteListItem;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.RewriteVO;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.SubmitRequest;
import com.shanchuang.modules.rewrite.dto.RewriteDtos.SubmitResponse;
import com.shanchuang.modules.rewrite.entity.Rewrite;
import com.shanchuang.modules.rewrite.mapper.RewriteMapper;
import com.shanchuang.modules.script.entity.Script;
import com.shanchuang.modules.script.mapper.ScriptMapper;
import com.shanchuang.modules.script.service.ScriptService;
import com.shanchuang.modules.task.TaskStatus;
import com.shanchuang.modules.task.entity.Task;
import com.shanchuang.modules.task.service.TaskService;
import com.shanchuang.modules.teardown.entity.Teardown;
import com.shanchuang.modules.teardown.service.TeardownService;
import com.shanchuang.workflow.HarnessPort;
import com.shanchuang.workflow.PromptBuilder;
import com.shanchuang.workflow.QualityGate;
import com.shanchuang.workflow.RewritePipeline;
import com.shanchuang.workflow.ScriptPipeline;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.RewriteSections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 洗稿。严格走十一段合同，先拆后写的顺序在 RewritePipeline 里强制 */
@Service
public class RewriteService {

    private static final Logger log = LoggerFactory.getLogger(RewriteService.class);

    private final RewriteMapper mapper;
    private final ScriptMapper scriptMapper;
    private final ScriptService scriptService;
    private final TaskService taskService;
    private final CreditService creditService;
    private final PersonaService personaService;
    private final TeardownService teardownService;
    private final AiGateway aiGateway;
    private final PromptBuilder promptBuilder;
    private final QualityGate qualityGate;
    private final TaskExecutor taskExecutor;

    public RewriteService(RewriteMapper mapper, ScriptMapper scriptMapper, ScriptService scriptService,
                          TaskService taskService,
                          CreditService creditService, PersonaService personaService,
                          TeardownService teardownService, AiGateway aiGateway,
                          PromptBuilder promptBuilder, QualityGate qualityGate,
                          @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.mapper = mapper;
        this.scriptMapper = scriptMapper;
        this.scriptService = scriptService;
        this.taskService = taskService;
        this.creditService = creditService;
        this.personaService = personaService;
        this.teardownService = teardownService;
        this.aiGateway = aiGateway;
        this.promptBuilder = promptBuilder;
        this.qualityGate = qualityGate;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public SubmitResponse submit(Long userId, SubmitRequest request, String requestId) {
        String title = request.originalTitle();
        String body = request.originalBody();

        // 拆解稿里已经有标题和口播原文，优先用它，避免用户重复粘一遍
        if (request.teardownId() != null) {
            Teardown teardown = teardownService.requireOwned(userId, request.teardownId());
            if (TextUtil.isBlank(title)) {
                title = teardown.getVideoTitle();
            }
            if (TextUtil.isBlank(body)) {
                body = teardown.getTranscript();
            }
        }
        if (TextUtil.cnWords(body) < 50) {
            throw BizException.of(4004, "原文过短，无法拆解。摘要不够拆钩子，请提供完整口播原文");
        }

        Task existing = taskService.findByRequestId(userId, requestId);
        if (existing != null) {
            Rewrite row = mapper.selectOne(Wrappers.<Rewrite>lambdaQuery()
                    .eq(Rewrite::getTaskId, existing.getId()).last("limit 1"));
            return new SubmitResponse(existing.getTaskNo(), row == null ? null : row.getId());
        }

        int accepted = creditService.hold(userId, 1, "pending");
        if (accepted <= 0) {
            throw BizException.of(3001, "额度不足，请先充值");
        }

        PersonaSnapshot persona = personaService.snapshot(userId);
        Task task = taskService.create(userId, TaskStatus.TYPE_REWRITE, requestId, 1,
                Map.of("teardownId", String.valueOf(request.teardownId()),
                        "purpose", String.valueOf(request.purpose())), 1);
        taskService.createItems(task.getId(), List.of(Map.of("idx", 0)));

        Rewrite row = new Rewrite();
        row.setUserId(userId);
        row.setTaskId(task.getId());
        row.setTeardownId(request.teardownId());
        row.setOriginalTitle(TextUtil.isBlank(title) ? "标题未知" : title);
        row.setOriginalBody(body);
        row.setStatus(TaskStatus.PENDING);
        mapper.insert(row);

        PromptBuilder.RewriteInput input = new PromptBuilder.RewriteInput(
                persona, row.getOriginalTitle(), body,
                request.purpose(), request.scene(),
                request.targetWords() == null ? 0 : request.targetWords(),
                request.extraViews(), request.extraBanned());

        taskExecutor.execute(() -> run(task.getId(), userId, task.getTaskNo(), row.getId(), input, persona));
        return new SubmitResponse(task.getTaskNo(), row.getId());
    }

    void run(Long taskId, Long userId, String taskNo, Long rewriteId,
             PromptBuilder.RewriteInput input, PersonaSnapshot persona) {
        taskService.markRunning(taskId);
        var items = taskService.items(taskId);
        Long itemId = items.isEmpty() ? null : items.get(0).getId();

        try {
            HarnessPort port = aiGateway.portFor(userId, taskId);
            RewritePipeline pipeline = new RewritePipeline(port, promptBuilder, qualityGate);
            RewriteSections sections = pipeline.produce(input);

            Rewrite patch = new Rewrite();
            patch.setId(rewriteId);
            patch.setMyTitle(sections.myTitle());
            patch.setSectionsJson(JsonUtil.toJson(sections.sections()));
            patch.setFinalBody(sections.finalBody());
            patch.setWords(TextUtil.cnWords(sections.finalBody()));
            patch.setScoreJson(JsonUtil.toJson(sections.scores()));
            patch.setReadaloudPass(sections.readaloudPass() ? 1 : 0);
            patch.setOverlapMax(sections.overlapMax());
            patch.setCheckReport(sections.checkReport());
            patch.setStatus(TaskStatus.SUCCESS);
            mapper.updateById(patch);

            creditService.consumeOne(userId, taskNo);
            if (itemId != null) {
                taskService.markItemSuccess(itemId, taskId, null);
            }
            taskService.finish(taskId);
        } catch (Exception e) {
            String code = e instanceof ScriptPipeline.GateFailedException ? "QUALITY_GATE"
                    : e instanceof HarnessPort.HarnessCallException hce ? hce.getErrorCode() : "INTERNAL";
            log.info("洗稿任务 {} 失败 [{}]：{}", taskNo, code, e.getMessage());

            Rewrite patch = new Rewrite();
            patch.setId(rewriteId);
            patch.setStatus(TaskStatus.FAILED);
            patch.setCheckReport(e.getMessage());
            mapper.updateById(patch);

            creditService.refundOne(userId, taskNo);
            if (itemId != null) {
                taskService.markItemFailed(itemId, taskId, code, e.getMessage());
            }
            taskService.fail(taskId, code, e.getMessage());
        }
    }

    public RewriteVO detail(Long userId, Long id) {
        return RewriteVO.of(requireOwned(userId, id));
    }

    public PageData<RewriteListItem> page(Long userId, long page, long size) {
        IPage<Rewrite> result = mapper.selectPage(new Page<>(page, size),
                Wrappers.<Rewrite>lambdaQuery()
                        .select(Rewrite::getId, Rewrite::getOriginalTitle, Rewrite::getMyTitle,
                                Rewrite::getWords, Rewrite::getStatus, Rewrite::getCreatedAt)
                        .eq(Rewrite::getUserId, userId)
                        .orderByDesc(Rewrite::getCreatedAt));
        return PageData.of(result.getRecords().stream().map(RewriteListItem::of).toList(),
                result.getTotal(), page, size);
    }

    /** 把第九段作为一条文案写入文案库 */
    @Transactional
    public PublishResponse publish(Long userId, Long id) {
        Rewrite row = requireOwned(userId, id);
        if (!TaskStatus.SUCCESS.equals(row.getStatus())) {
            throw BizException.of(3004, "洗稿尚未完成，无法定稿");
        }
        if (row.getScriptId() != null) {
            return new PublishResponse(row.getScriptId());
        }
        if (TextUtil.isBlank(row.getFinalBody())) {
            throw BizException.of(5003, "缺第九段正文，无法定稿");
        }

        Script script = new Script();
        script.setUserId(userId);
        script.setSeqNo(scriptService.allocateSeqRange(userId, 1));
        script.setTitle(TextUtil.isBlank(row.getMyTitle()) ? row.getOriginalTitle() : row.getMyTitle());
        script.setTopic("洗稿自：" + row.getOriginalTitle());
        script.setScriptType("痛点科普");
        script.setTopicType("破圈类");
        script.setSource("对标爆款拆解");
        script.setGrid("对标洗稿");
        script.setElement("反差");
        script.setStructure("按十一段合同重写");
        script.setWords(TextUtil.cnWords(row.getFinalBody()));
        script.setBody(row.getFinalBody());
        script.setGenerated(1);
        scriptMapper.insert(script);

        Rewrite patch = new Rewrite();
        patch.setId(id);
        patch.setScriptId(script.getId());
        mapper.updateById(patch);

        return new PublishResponse(script.getId());
    }

    public Rewrite requireOwned(Long userId, Long id) {
        Rewrite row = mapper.selectById(id);
        if (row == null) {
            throw BizException.notFound("洗稿记录不存在");
        }
        if (!row.getUserId().equals(userId)) {
            throw BizException.forbidden("无权访问该洗稿记录");
        }
        return row;
    }
}
