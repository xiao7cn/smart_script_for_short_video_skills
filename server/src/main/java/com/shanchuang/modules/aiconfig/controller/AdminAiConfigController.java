package com.shanchuang.modules.aiconfig.controller;

import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.result.R;
import com.shanchuang.harness.HarnessClient;
import com.shanchuang.harness.HarnessContract.Health;
import com.shanchuang.harness.HarnessContract.ProviderHealth;
import com.shanchuang.harness.HarnessRouter;
import com.shanchuang.harness.Scene;
import com.shanchuang.modules.aiconfig.entity.AiModelConfig;
import com.shanchuang.modules.aiconfig.entity.HarnessConfig;
import com.shanchuang.modules.aiconfig.service.HarnessConfigService;
import com.shanchuang.modules.aiconfig.service.ModelConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 平台配置。
 * 对应 docs/接口设计.md 第 11 章：模型按场景独立配置 + harness 切换 + 健康检查。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAiConfigController {

    private final ModelConfigService modelConfigService;
    private final HarnessConfigService harnessConfigService;
    private final HarnessRouter router;

    public AdminAiConfigController(ModelConfigService modelConfigService,
                                   HarnessConfigService harnessConfigService,
                                   HarnessRouter router) {
        this.modelConfigService = modelConfigService;
        this.harnessConfigService = harnessConfigService;
        this.router = router;
    }

    @GetMapping("/ai-models")
    public R<List<ModelConfigVO>> listModels() {
        Map<String, AiModelConfig> byScene = new java.util.HashMap<>();
        modelConfigService.listAll().forEach(row -> byScene.put(row.getScene(), row));

        List<ModelConfigVO> out = new ArrayList<>();
        for (Scene scene : Scene.values()) {
            AiModelConfig row = byScene.get(scene.name());
            if (row != null) {
                out.add(ModelConfigVO.of(scene, row));
            } else {
                // 库里没配也要展示：把 yml 兜底后的有效值给出来，运营才知道现在实际在用什么
                var spec = modelConfigService.resolve(scene);
                out.add(new ModelConfigVO(scene.name(), scene.label(), spec.provider(), spec.modelId(),
                        spec.temperature(), spec.maxTokens(), spec.thinking(), spec.baseUrl(),
                        spec.apiKeyEnv(), spec.maxSteps(), spec.timeoutMs(), true, "未入库，来自配置文件兜底"));
            }
        }
        return R.ok(out);
    }

    @PutMapping("/ai-models/{scene}")
    public R<ModelConfigVO> updateModel(@PathVariable String scene, @RequestBody ModelConfigVO body) {
        Scene target = Scene.of(scene);
        if (target == null) {
            throw BizException.of(7001, "模型场景不存在: " + scene);
        }
        AiModelConfig patch = new AiModelConfig();
        patch.setProvider(body.provider());
        patch.setModelId(body.modelId());
        patch.setTemperature(body.temperature());
        patch.setMaxTokens(body.maxTokens());
        patch.setThinking(body.thinking() == null ? "off" : body.thinking());
        patch.setBaseUrl(body.baseUrl());
        patch.setApiKeyEnv(body.apiKeyEnv());
        patch.setMaxSteps(body.maxSteps() == null ? target.defaultMaxSteps() : body.maxSteps());
        patch.setTimeoutMs(body.timeoutMs());
        patch.setEnabled(body.enabled() == null || body.enabled() ? 1 : 0);
        patch.setRemark(body.remark());

        AiModelConfig saved = modelConfigService.save(target, patch);
        return R.ok(ModelConfigVO.of(target, saved));
    }

    /** 可用 provider 与模型清单，透传当前 harness 上报的内容，供前端做下拉 */
    @GetMapping("/ai-models/catalog")
    public R<List<ProviderHealth>> catalog() {
        return R.ok(List.copyOf(router.current().health().providers()));
    }

    @GetMapping("/harness")
    public R<List<HarnessVO>> listHarness() {
        String current = router.currentName();
        return R.ok(harnessConfigService.listAll().stream()
                .map(row -> HarnessVO.of(row, row.getName().equals(current)))
                .toList());
    }

    @PutMapping("/harness/default")
    public R<Void> switchHarness(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || harnessConfigService.findByName(name) == null) {
            throw BizException.of(7002, "harness 不存在: " + name);
        }
        harnessConfigService.switchDefault(name);
        return R.ok();
    }

    @GetMapping("/harness/health")
    public R<Health> health() {
        HarnessClient client = router.current();
        Health health = client.health();
        harnessConfigService.recordHealth(client.name(), health.ok(), health.harnessVersion());
        if (!health.ok()) {
            throw BizException.of(7002, "Harness 不可用：" + health.errorMessage());
        }
        return R.ok(health);
    }

    public record ModelConfigVO(
            String scene, String sceneName, String provider, String modelId,
            BigDecimal temperature, Integer maxTokens, String thinking, String baseUrl,
            String apiKeyEnv, Integer maxSteps, Integer timeoutMs, Boolean enabled, String remark
    ) {
        static ModelConfigVO of(Scene scene, AiModelConfig row) {
            return new ModelConfigVO(scene.name(), scene.label(), row.getProvider(), row.getModelId(),
                    row.getTemperature(), row.getMaxTokens(), row.getThinking(), row.getBaseUrl(),
                    row.getApiKeyEnv(), row.getMaxSteps(), row.getTimeoutMs(),
                    row.getEnabled() == null || row.getEnabled() == 1, row.getRemark());
        }
    }

    public record HarnessVO(
            String name, String endpoint, Boolean enabled, Boolean isDefault,
            String version, Boolean lastHealthOk, String lastHealthAt
    ) {
        static HarnessVO of(HarnessConfig row, boolean isCurrent) {
            return new HarnessVO(row.getName(), row.getEndpoint(),
                    row.getEnabled() == null || row.getEnabled() == 1, isCurrent,
                    row.getVersion(),
                    row.getLastHealthOk() == null ? null : row.getLastHealthOk() == 1,
                    row.getLastHealthAt() == null ? null : row.getLastHealthAt().toString());
        }
    }
}
