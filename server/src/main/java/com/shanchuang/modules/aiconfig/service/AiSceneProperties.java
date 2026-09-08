package com.shanchuang.modules.aiconfig.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * application.yml 里的模型默认值。
 * 数据库没有某个场景的配置时用这里兜底，保证「库是空的也能跑」。
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiSceneProperties {

    private Spec defaults = new Spec();
    private Map<String, Spec> scenes = new LinkedHashMap<>();

    /** yml 里的 key 是 default，Java 里不能用这个名字做字段 */
    public Spec getDefault() {
        return defaults;
    }

    public void setDefault(Spec defaults) {
        this.defaults = defaults;
    }

    public Map<String, Spec> getScenes() {
        return scenes;
    }

    public void setScenes(Map<String, Spec> scenes) {
        this.scenes = scenes;
    }

    /** 取某场景的兜底配置：场景级缺的字段回落到全局默认 */
    public Spec resolve(String scene) {
        Spec merged = new Spec();
        merged.copyFrom(defaults);
        Spec sceneSpec = scenes.get(scene);
        if (sceneSpec != null) {
            merged.overrideWith(sceneSpec);
        }
        return merged;
    }

    public static class Spec {
        private String provider;
        private String modelId;
        private BigDecimal temperature;
        private Integer maxTokens;
        private String thinking;
        private String baseUrl;
        private String apiKeyEnv;
        private Integer maxSteps;
        private Integer timeoutMs;

        void copyFrom(Spec other) {
            this.provider = other.provider;
            this.modelId = other.modelId;
            this.temperature = other.temperature;
            this.maxTokens = other.maxTokens;
            this.thinking = other.thinking;
            this.baseUrl = other.baseUrl;
            this.apiKeyEnv = other.apiKeyEnv;
            this.maxSteps = other.maxSteps;
            this.timeoutMs = other.timeoutMs;
        }

        void overrideWith(Spec other) {
            if (other.provider != null) this.provider = other.provider;
            if (other.modelId != null) this.modelId = other.modelId;
            if (other.temperature != null) this.temperature = other.temperature;
            if (other.maxTokens != null) this.maxTokens = other.maxTokens;
            if (other.thinking != null) this.thinking = other.thinking;
            if (other.baseUrl != null) this.baseUrl = other.baseUrl;
            if (other.apiKeyEnv != null) this.apiKeyEnv = other.apiKeyEnv;
            if (other.maxSteps != null) this.maxSteps = other.maxSteps;
            if (other.timeoutMs != null) this.timeoutMs = other.timeoutMs;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public BigDecimal getTemperature() {
            return temperature;
        }

        public void setTemperature(BigDecimal temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getThinking() {
            return thinking;
        }

        public void setThinking(String thinking) {
            this.thinking = thinking;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public Integer getMaxSteps() {
            return maxSteps;
        }

        public void setMaxSteps(Integer maxSteps) {
            this.maxSteps = maxSteps;
        }

        public Integer getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(Integer timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
