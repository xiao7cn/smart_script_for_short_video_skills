package com.shanchuang.harness.pi;

import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.harness.HarnessClient;
import com.shanchuang.harness.HarnessContract.Health;
import com.shanchuang.harness.HarnessContract.RunRequest;
import com.shanchuang.harness.HarnessContract.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * pi harness 的客户端。
 *
 * pi 是 TypeScript 实现，没法在 JVM 内嵌，所以它是一个独立的 Node 进程（agent/），
 * 两者之间走 docs/接口设计.md 12 章那份与 harness 无关的 HTTP 契约。
 * 独立进程反而是优点：harness 能单独重启、单独扩容、整体换掉。
 */
public class PiHarnessClient implements HarnessClient {

    private static final Logger log = LoggerFactory.getLogger(PiHarnessClient.class);

    private final String endpoint;
    private final int defaultTimeoutMs;
    private final HttpClient http;

    public PiHarnessClient(String endpoint, int defaultTimeoutMs) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String name() {
        return "pi";
    }

    @Override
    public RunResult run(RunRequest request) {
        // 读超时留出余量：agent 侧自己也会在 timeoutMs 到点时中断，
        // 如果这边先断，就拿不到 agent 返回的错误码了
        int timeout = request.timeoutMs() != null ? request.timeoutMs() : defaultTimeoutMs;
        Duration readTimeout = Duration.ofMillis(timeout + 15_000L);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/v1/harness/run"))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(request), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return RunResult.failure(request.requestId(), name(), "PROVIDER_ERROR",
                        "harness returned HTTP " + response.statusCode());
            }
            RunResult result = JsonUtil.fromJson(response.body(), RunResult.class);
            if (result == null) {
                return RunResult.failure(request.requestId(), name(), "PROVIDER_ERROR",
                        "harness returned empty body");
            }
            return result;
        } catch (java.net.http.HttpTimeoutException e) {
            return RunResult.failure(request.requestId(), name(), "TIMEOUT",
                    "harness call timed out after " + readTimeout.toMillis() + "ms");
        } catch (java.net.ConnectException e) {
            log.warn("harness unreachable at {}: {}", endpoint, e.getMessage());
            return RunResult.failure(request.requestId(), name(), "PROVIDER_ERROR",
                    "Harness 不可用：" + endpoint);
        } catch (Exception e) {
            log.warn("harness call failed: {}", e.getMessage());
            return RunResult.failure(request.requestId(), name(), "PROVIDER_ERROR", e.getMessage());
        }
    }

    @Override
    public Health health() {
        long startedAt = System.currentTimeMillis();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/v1/harness/health"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Health.down(name(), "HTTP " + response.statusCode());
            }
            Health parsed = JsonUtil.fromJson(response.body(), Health.class);
            if (parsed == null) {
                return Health.down(name(), "empty health body");
            }
            int latency = (int) (System.currentTimeMillis() - startedAt);
            return new Health(parsed.ok(), name(), parsed.harnessVersion(), latency,
                    parsed.providers(), parsed.tools(), null);
        } catch (Exception e) {
            return Health.down(name(), e.getMessage());
        }
    }
}
