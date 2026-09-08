package com.shanchuang.modules.aiconfig.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanchuang.harness.HarnessContract.ModelSpec;
import com.shanchuang.harness.Scene;
import com.shanchuang.modules.aiconfig.entity.AiModelConfig;
import com.shanchuang.modules.aiconfig.mapper.AiModelConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按场景解析模型配置。
 *
 * 解析优先级：数据库 sv_ai_model_config（enabled=1）→ application.yml 的 ai.scenes.<scene>
 * → ai.default。带 60 秒本地缓存，改配置下一次调用即生效，不用重启也不给数据库压力。
 *
 * 场景是配置粒度，所以「脚本生成」与「文案重写」天然是两套独立配置，
 * 可以指向不同 provider 与不同模型。
 */
@Service
public class ModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigService.class);

    private static final long CACHE_TTL_MS = 60_000L;

    private final AiModelConfigMapper mapper;
    private final AiSceneProperties properties;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ModelConfigService(AiModelConfigMapper mapper, AiSceneProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public ModelSpec resolve(Scene scene) {
        Cached cached = cache.get(scene.name());
        if (cached != null && !cached.expired()) {
            return cached.spec();
        }
        ModelSpec spec = load(scene);
        cache.put(scene.name(), new Cached(spec, System.currentTimeMillis() + CACHE_TTL_MS));
        return spec;
    }

    private ModelSpec load(Scene scene) {
        AiModelConfig row = null;
        try {
            row = mapper.selectOne(Wrappers.<AiModelConfig>lambdaQuery()
                    .eq(AiModelConfig::getScene, scene.name())
                    .eq(AiModelConfig::getEnabled, 1)
                    .last("limit 1"));
        } catch (Exception e) {
            log.warn("读取场景 {} 的模型配置失败，回落到配置文件: {}", scene, e.getMessage());
        }

        AiSceneProperties.Spec fallback = properties.resolve(scene.name());
        if (row == null) {
            return new ModelSpec(
                    fallback.getProvider(), fallback.getModelId(), fallback.getTemperature(),
                    fallback.getMaxTokens(), fallback.getThinking(), fallback.getBaseUrl(),
                    fallback.getApiKeyEnv(),
                    fallback.getMaxSteps() != null ? fallback.getMaxSteps() : scene.defaultMaxSteps(),
                    fallback.getTimeoutMs());
        }

        return new ModelSpec(
                row.getProvider() != null ? row.getProvider() : fallback.getProvider(),
                row.getModelId() != null ? row.getModelId() : fallback.getModelId(),
                row.getTemperature() != null ? row.getTemperature() : fallback.getTemperature(),
                row.getMaxTokens() != null ? row.getMaxTokens() : fallback.getMaxTokens(),
                row.getThinking() != null ? row.getThinking() : fallback.getThinking(),
                row.getBaseUrl() != null ? row.getBaseUrl() : fallback.getBaseUrl(),
                row.getApiKeyEnv() != null ? row.getApiKeyEnv() : fallback.getApiKeyEnv(),
                row.getMaxSteps() != null ? row.getMaxSteps() : scene.defaultMaxSteps(),
                row.getTimeoutMs() != null ? row.getTimeoutMs() : fallback.getTimeoutMs());
    }

    public List<AiModelConfig> listAll() {
        return mapper.selectList(Wrappers.<AiModelConfig>lambdaQuery()
                .orderByAsc(AiModelConfig::getId));
    }

    public AiModelConfig findByScene(Scene scene) {
        return mapper.selectOne(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getScene, scene.name())
                .last("limit 1"));
    }

    /** 更新某场景配置。改完立即清缓存，下一次调用就用新配置 */
    public AiModelConfig save(Scene scene, AiModelConfig patch) {
        AiModelConfig existing = findByScene(scene);
        patch.setScene(scene.name());
        if (existing == null) {
            mapper.insert(patch);
        } else {
            patch.setId(existing.getId());
            mapper.updateById(patch);
        }
        cache.remove(scene.name());
        return findByScene(scene);
    }

    public void invalidate() {
        cache.clear();
    }

    private record Cached(ModelSpec spec, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
