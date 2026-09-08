package com.shanchuang.harness;

import java.math.BigDecimal;
import java.util.List;

/**
 * Harness 契约 DTO。
 *
 * 这些类型里刻意不出现任何 pi 专有概念——工具只传名字不传定义，模型只传
 * provider/modelId/采样参数。把 pi 换成 deepseek harness 时，业务代码零改动。
 * 对应 docs/接口设计.md 第 12 章。
 */
public final class HarnessContract {

    private HarnessContract() {
    }

    /** 模型规格。apiKeyEnv 是环境变量名，Java 全程不接触密钥本身 */
    public record ModelSpec(
            String provider,
            String modelId,
            BigDecimal temperature,
            Integer maxTokens,
            String thinking,
            String baseUrl,
            String apiKeyEnv,
            Integer maxSteps,
            Integer timeoutMs
    ) {
        public ModelSpec withMaxSteps(Integer steps) {
            return new ModelSpec(provider, modelId, temperature, maxTokens, thinking,
                    baseUrl, apiKeyEnv, steps, timeoutMs);
        }
    }

    public record Msg(String role, String content) {
        public static Msg user(String content) {
            return new Msg("user", content);
        }

        public static Msg assistant(String content) {
            return new Msg("assistant", content);
        }
    }

    public record Usage(Integer inputTokens, Integer outputTokens, BigDecimal costUsd) {
        public static Usage zero() {
            return new Usage(0, 0, BigDecimal.ZERO);
        }
    }

    public record RunRequest(
            String requestId,
            String scene,
            ModelSpec model,
            String systemPrompt,
            List<Msg> messages,
            List<String> tools,
            Integer maxSteps,
            Integer timeoutMs
    ) {
    }

    public record ToolCallTrace(String name, boolean ok, String detail) {
    }

    public record RunResult(
            String requestId,
            boolean ok,
            String text,
            Usage usage,
            Integer steps,
            String harnessName,
            String harnessVersion,
            List<ToolCallTrace> toolCalls,
            String errorCode,
            String errorMessage
    ) {
        public static RunResult failure(String requestId, String harnessName,
                                        String errorCode, String errorMessage) {
            return new RunResult(requestId, false, null, Usage.zero(), 0,
                    harnessName, null, List.of(), errorCode, errorMessage);
        }
    }

    public record ProviderHealth(String name, boolean ready, List<String> models) {
    }

    public record Health(
            boolean ok,
            String harnessName,
            String harnessVersion,
            Integer latencyMs,
            List<ProviderHealth> providers,
            List<String> tools,
            String errorMessage
    ) {
        public static Health down(String harnessName, String errorMessage) {
            return new Health(false, harnessName, null, null, List.of(), List.of(), errorMessage);
        }
    }
}
