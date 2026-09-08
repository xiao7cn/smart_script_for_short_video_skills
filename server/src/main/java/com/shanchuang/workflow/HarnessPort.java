package com.shanchuang.workflow;

import com.shanchuang.harness.Scene;

import java.util.List;

/**
 * workflow 层看到的模型调用出口。
 *
 * 刻意定义成一个窄接口：Pipeline 只需要「给定场景、提示词、消息，拿回文本」，
 * 不需要知道 harness、模型配置、调用留痕的存在。
 * 于是 Pipeline 的单元测试只要传一个返回固定字符串的 lambda，
 * 不用起 Spring、不用连网、不用真实模型。
 */
@FunctionalInterface
public interface HarnessPort {

    /**
     * 跑一次模型调用。
     *
     * @param scene         场景，决定用哪套模型配置与工具白名单
     * @param systemPrompt  系统提示词
     * @param messages      user/assistant 交替的消息
     * @param tools         工具名白名单，null 表示用场景默认
     * @return 最终文本
     * @throws HarnessCallException 调用失败（含鉴权、限流、超时），由 Pipeline 决定是否重试
     */
    String run(Scene scene, String systemPrompt, List<String> messages, List<String> tools);

    default String run(Scene scene, String systemPrompt, String userMessage) {
        return run(scene, systemPrompt, List.of(userMessage), null);
    }

    /** harness 调用失败。errorCode 取值见 docs/接口设计.md 12.1 */
    class HarnessCallException extends RuntimeException {
        private final String errorCode;

        public HarnessCallException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public boolean retryable() {
            return isRetryable(errorCode);
        }
    }

    /**
     * 重试策略的唯一定义，调用方与异常都走这里。
     *
     * 只有限流值得重试。特别注意 PROVIDER_QUOTA（余额耗尽）也返回 429，
     * 但重试再多次都不会成功，所以 harness 侧把它单独分了一个码。
     * 鉴权、模型不存在、参数错误重试同样是白费。
     */
    static boolean isRetryable(String errorCode) {
        return "PROVIDER_RATE_LIMIT".equals(errorCode);
    }
}
