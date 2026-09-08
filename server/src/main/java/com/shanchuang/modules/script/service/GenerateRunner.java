package com.shanchuang.modules.script.service;

import com.shanchuang.modules.aiconfig.service.AiGateway;
import com.shanchuang.modules.credit.service.CreditService;
import com.shanchuang.modules.script.entity.Script;
import com.shanchuang.modules.task.entity.Task;
import com.shanchuang.modules.task.entity.TaskItem;
import com.shanchuang.modules.task.mapper.TaskMapper;
import com.shanchuang.modules.task.service.TaskService;
import com.shanchuang.workflow.HarnessPort;
import com.shanchuang.workflow.PromptBuilder;
import com.shanchuang.workflow.QualityGate;
import com.shanchuang.workflow.ScriptPipeline;
import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.ParamCard;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.ScriptDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * 批量生成的异步执行器。
 *
 * 逐条完成逐条入库并累加进度，前端才能渐进展示，而不是干等十分钟。
 * 单条失败不影响整批：该条退 1 条额度、标失败，其余继续。
 */
@Service
public class GenerateRunner {

    private static final Logger log = LoggerFactory.getLogger(GenerateRunner.class);

    private final TaskExecutor taskExecutor;
    private final TaskExecutor itemExecutor;
    private final TaskService taskService;
    private final TaskMapper taskMapper;
    private final ScriptService scriptService;
    private final CreditService creditService;
    private final AiGateway aiGateway;
    private final PromptBuilder promptBuilder;
    private final QualityGate qualityGate;
    private final int itemConcurrency;

    public GenerateRunner(@Qualifier("taskExecutor") TaskExecutor taskExecutor,
                          @Qualifier("itemExecutor") TaskExecutor itemExecutor,
                          TaskService taskService, TaskMapper taskMapper,
                          ScriptService scriptService, CreditService creditService,
                          AiGateway aiGateway, PromptBuilder promptBuilder, QualityGate qualityGate,
                          @Value("${app.generate.item-concurrency:3}") int itemConcurrency) {
        this.taskExecutor = taskExecutor;
        this.itemExecutor = itemExecutor;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.scriptService = scriptService;
        this.creditService = creditService;
        this.aiGateway = aiGateway;
        this.promptBuilder = promptBuilder;
        this.qualityGate = qualityGate;
        this.itemConcurrency = Math.max(1, itemConcurrency);
    }

    public void runAsync(Long taskId, Long userId, List<ParamCard> cards,
                         PersonaSnapshot persona, OptionCatalog catalog, String promptOverride) {
        taskExecutor.execute(() -> run(taskId, userId, cards, persona, catalog, promptOverride));
    }

    void run(Long taskId, Long userId, List<ParamCard> cards,
             PersonaSnapshot persona, OptionCatalog catalog, String promptOverride) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        taskService.markRunning(taskId);

        HarnessPort port = aiGateway.portFor(userId, taskId);
        ScriptPipeline pipeline = new ScriptPipeline(port, promptBuilder, qualityGate);

        Map<Integer, TaskItem> itemsByIdx = taskService.items(taskId).stream()
                .collect(Collectors.toMap(TaskItem::getIdx, i -> i, (a, b) -> a));

        // 号段一次性占掉：条目并发跑，各自读 max 再 +1 会撞出重复的 NO.xx
        int seqBase = scriptService.allocateSeqRange(userId, cards.size());

        // 一批 20 条全打上去会触发 provider 限流，这里按配置限并发
        Semaphore permits = new Semaphore(itemConcurrency);
        CountDownLatch latch = new CountDownLatch(cards.size());

        for (ParamCard card : cards) {
            TaskItem item = itemsByIdx.get(card.idx());
            if (item == null) {
                latch.countDown();
                continue;
            }
            int seqNo = seqBase + card.idx();
            itemExecutor.execute(() -> {
                try {
                    permits.acquire();
                    produceOne(taskId, userId, task.getTaskNo(), item, card, persona, catalog,
                            promptOverride, pipeline, seqNo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    permits.release();
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        taskService.finish(taskId);
    }

    private void produceOne(Long taskId, Long userId, String taskNo, TaskItem item, ParamCard card,
                            PersonaSnapshot persona, OptionCatalog catalog, String promptOverride,
                            ScriptPipeline pipeline, int seqNo) {
        taskService.markItemRunning(item.getId(), card);
        try {
            ScriptDraft draft = pipeline.produce(card, persona, catalog, promptOverride);
            Script saved = scriptService.save(userId, taskId, draft, seqNo);

            if (card.fromBenchmark()) {
                // 对标来源的文案要能在详情页看到原文与拆解要点
                scriptService.saveBreakdown(saved.getId(), null, card.refs(), card.autoSearch(),
                        null, List.of(), null);
            }

            creditService.consumeOne(userId, taskNo);
            taskService.markItemSuccess(item.getId(), taskId, saved.getId());
        } catch (ScriptPipeline.GateFailedException e) {
            log.info("任务 {} 第 {} 条未过质量门：{}", taskNo, card.idx(), e.getMessage());
            failOne(taskId, userId, taskNo, item, "QUALITY_GATE", e.getMessage());
        } catch (HarnessPort.HarnessCallException e) {
            log.warn("任务 {} 第 {} 条模型调用失败 [{}]：{}", taskNo, card.idx(), e.getErrorCode(), e.getMessage());
            failOne(taskId, userId, taskNo, item, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("任务 {} 第 {} 条异常", taskNo, card.idx(), e);
            failOne(taskId, userId, taskNo, item, "INTERNAL", e.getMessage());
        }
    }

    /** 失败要退额度：额度是预扣的，不退就等于吞了用户的钱 */
    private void failOne(Long taskId, Long userId, String taskNo, TaskItem item,
                         String errorCode, String errorMsg) {
        creditService.refundOne(userId, taskNo);
        taskService.markItemFailed(item.getId(), taskId, errorCode, errorMsg);
    }
}
