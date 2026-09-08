package com.shanchuang.modules.aiconfig.service;

import com.shanchuang.common.util.IdUtil;
import com.shanchuang.common.util.TextUtil;
import com.shanchuang.harness.HarnessClient;
import com.shanchuang.harness.HarnessContract.ModelSpec;
import com.shanchuang.harness.HarnessContract.Msg;
import com.shanchuang.harness.HarnessContract.RunRequest;
import com.shanchuang.harness.HarnessContract.RunResult;
import com.shanchuang.harness.HarnessRouter;
import com.shanchuang.harness.Scene;
import com.shanchuang.modules.aiconfig.entity.LlmCallLog;
import com.shanchuang.modules.aiconfig.mapper.LlmCallLogMapper;
import com.shanchuang.workflow.HarnessPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * workflow 与 harness 之间的桥。
 *
 * 职责有三件：按场景解析模型配置、把调用交给当前 harness、把用量与成败落库留痕。
 * Pipeline 只看到 HarnessPort 这个窄接口，所以它既不知道 harness 的存在，
 * 也不知道有留痕这回事——这样 Pipeline 才能脱离 Spring 做单测。
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    /** provider 限流值得等一下再试，指数退避 */
    private static final long[] RETRY_BACKOFF_MS = {1_000L, 4_000L, 16_000L};

    private final HarnessRouter router;
    private final ModelConfigService modelConfigService;
    private final LlmCallLogMapper callLogMapper;

    public AiGateway(HarnessRouter router, ModelConfigService modelConfigService,
                     LlmCallLogMapper callLogMapper) {
        this.router = router;
        this.modelConfigService = modelConfigService;
        this.callLogMapper = callLogMapper;
    }

    /** 给某个任务绑定一个 HarnessPort，任务里的每次模型调用都会带上 userId/taskId 留痕 */
    public HarnessPort portFor(Long userId, Long taskId) {
        return (scene, systemPrompt, messages, tools) -> call(scene, systemPrompt, messages, tools, userId, taskId);
    }

    /** 不绑任务的一次性调用（提示词预览、管理端调试） */
    public HarnessPort port() {
        return portFor(null, null);
    }

    private String call(Scene scene, String systemPrompt, List<String> messages,
                        List<String> tools, Long userId, Long taskId) {
        ModelSpec model = modelConfigService.resolve(scene);
        List<String> effectiveTools = tools != null ? tools : scene.defaultTools();
        int maxSteps = model.maxSteps() != null ? model.maxSteps() : scene.defaultMaxSteps();

        RunResult last = null;
        for (int attempt = 0; attempt <= RETRY_BACKOFF_MS.length; attempt++) {
            HarnessClient client = router.current();
            RunRequest request = new RunRequest(
                    IdUtil.requestId(), scene.name(), model, systemPrompt,
                    messages.stream().map(Msg::user).toList(),
                    effectiveTools, maxSteps, model.timeoutMs());

            long startedAt = System.currentTimeMillis();
            RunResult result = client.run(request);
            int latency = (int) (System.currentTimeMillis() - startedAt);

            recordCall(request, result, latency, userId, taskId, client.name());
            last = result;

            if (result.ok()) {
                return result.text();
            }
            if (!HarnessPort.isRetryable(result.errorCode()) || attempt == RETRY_BACKOFF_MS.length) {
                break;
            }
            sleep(RETRY_BACKOFF_MS[attempt]);
            log.warn("场景 {} 触发上游限流，{}ms 后重试（第 {} 次）", scene, RETRY_BACKOFF_MS[attempt], attempt + 1);
        }

        String code = last != null && last.errorCode() != null ? last.errorCode() : "PROVIDER_ERROR";
        String message = last != null ? last.errorMessage() : "harness 无响应";
        throw new HarnessPort.HarnessCallException(code, message);
    }

    /** 留痕失败不能影响业务：模型已经出稿了，不该因为写日志失败把整条判失败 */
    private void recordCall(RunRequest request, RunResult result, int latencyMs,
                            Long userId, Long taskId, String harnessName) {
        try {
            LlmCallLog entry = new LlmCallLog();
            entry.setRequestId(request.requestId());
            entry.setUserId(userId);
            entry.setTaskId(taskId);
            entry.setScene(request.scene());
            entry.setHarnessName(harnessName);
            entry.setHarnessVersion(result.harnessVersion());
            entry.setProvider(request.model().provider());
            entry.setModelId(request.model().modelId());
            if (result.usage() != null) {
                entry.setInputTokens(result.usage().inputTokens());
                entry.setOutputTokens(result.usage().outputTokens());
                entry.setCostUsd(result.usage().costUsd() != null ? result.usage().costUsd() : BigDecimal.ZERO);
            }
            entry.setSteps(result.steps());
            entry.setLatencyMs(latencyMs);
            entry.setOk(result.ok() ? 1 : 0);
            entry.setErrorCode(result.errorCode());
            entry.setErrorMsg(TextUtil.abbreviate(result.errorMessage(), 480));
            callLogMapper.insert(entry);
        } catch (Exception e) {
            log.debug("写调用留痕失败: {}", e.getMessage());
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
