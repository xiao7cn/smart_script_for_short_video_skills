package com.shanchuang.harness;

import com.shanchuang.harness.mock.MockHarnessClient;
import com.shanchuang.harness.pi.PiHarnessClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Harness 路由。
 *
 * 决定这次调用交给哪个 harness 实现。优先读库里 is_default=1 的记录，
 * 库不可用或没配就回落到 application.yml 的 harness.provider。
 * 这样切换 harness（pi → deepseek）不用重启也不用发版。
 */
@Component
public class HarnessRouter {

    private static final Logger log = LoggerFactory.getLogger(HarnessRouter.class);

    /** 缓存已构建的客户端：HttpClient 复用连接池，不该每次调用都新建 */
    private final Map<String, HarnessClient> clients = new ConcurrentHashMap<>();

    private final ObjectProvider<HarnessSelectionSource> selectionSource;
    private final String fallbackProvider;
    private final String fallbackEndpoint;
    private final int timeoutMs;

    public HarnessRouter(ObjectProvider<HarnessSelectionSource> selectionSource,
                         @Value("${harness.provider:pi}") String fallbackProvider,
                         @Value("${harness.endpoint:http://127.0.0.1:8790}") String fallbackEndpoint,
                         @Value("${harness.timeout-ms:300000}") int timeoutMs) {
        this.selectionSource = selectionSource;
        this.fallbackProvider = fallbackProvider;
        this.fallbackEndpoint = fallbackEndpoint;
        this.timeoutMs = timeoutMs;
    }

    /** 当前生效的 harness */
    public HarnessClient current() {
        return byName(currentName());
    }

    public String currentName() {
        HarnessSelectionSource source = selectionSource.getIfAvailable();
        if (source != null) {
            try {
                Optional<String> name = source.defaultHarnessName();
                if (name.isPresent()) {
                    return name.get();
                }
            } catch (Exception e) {
                log.warn("读取 harness 配置失败，回落到配置文件 {}: {}", fallbackProvider, e.getMessage());
            }
        }
        return fallbackProvider;
    }

    public HarnessClient byName(String name) {
        String key = name == null || name.isBlank() ? fallbackProvider : name;
        return clients.computeIfAbsent(key, this::build);
    }

    /** 管理端要展示每个 harness 的健康状态，所以按名字全给出来 */
    public Map<String, HarnessClient> all() {
        Map<String, HarnessClient> map = new LinkedHashMap<>();
        for (String name : new String[]{"pi", "mock"}) {
            map.put(name, byName(name));
        }
        return map;
    }

    private HarnessClient build(String name) {
        return switch (name) {
            case "mock" -> new MockHarnessClient();
            case "pi", "deepseek" -> new PiHarnessClient(endpointFor(name), timeoutMs);
            default -> {
                log.warn("未知 harness [{}]，按 pi 处理", name);
                yield new PiHarnessClient(endpointFor("pi"), timeoutMs);
            }
        };
    }

    private String endpointFor(String name) {
        HarnessSelectionSource source = selectionSource.getIfAvailable();
        if (source != null) {
            try {
                Optional<String> endpoint = source.endpointOf(name);
                if (endpoint.isPresent() && !endpoint.get().isBlank()) {
                    return endpoint.get();
                }
            } catch (Exception e) {
                log.warn("读取 harness 端点失败，回落到配置文件: {}", e.getMessage());
            }
        }
        return fallbackEndpoint;
    }

    /** 配置变更后清缓存，让下一次调用用新端点 */
    public void invalidate() {
        clients.clear();
    }
}
