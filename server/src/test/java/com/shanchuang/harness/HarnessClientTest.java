package com.shanchuang.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.harness.HarnessContract.Health;
import com.shanchuang.harness.HarnessContract.ModelSpec;
import com.shanchuang.harness.HarnessContract.Msg;
import com.shanchuang.harness.HarnessContract.RunRequest;
import com.shanchuang.harness.HarnessContract.RunResult;
import com.shanchuang.harness.mock.MockHarnessClient;
import com.shanchuang.harness.pi.PiHarnessClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness 契约测试。
 *
 * 重点验证「契约与 harness 实现无关」这件事在代码上成立：
 * 请求体里不该出现任何 pi 专有字段，工具只传名字不传定义。
 */
class HarnessClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private RunRequest request(Scene scene) {
        ModelSpec model = new ModelSpec("deepseek", "deepseek-chat",
                new BigDecimal("0.80"), 4096, "off", null, "DEEPSEEK_API_KEY", 1, 60000);
        return new RunRequest("req-1", scene.name(), model, "你是写手",
                List.of(Msg.user("写一条")), scene.defaultTools(), scene.defaultMaxSteps(), 60000);
    }

    @Test
    @DisplayName("请求体只含契约字段，工具只传名字不传 JSON Schema")
    void requestCarriesOnlyContractFields() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"requestId":"req-1","ok":true,"text":"结果",
                         "usage":{"inputTokens":10,"outputTokens":20,"costUsd":0.001},
                         "steps":1,"harnessName":"pi","harnessVersion":"0.85.0"}
                        """));

        PiHarnessClient client = new PiHarnessClient(server.url("/").toString(), 60000);
        RunResult result = client.run(request(Scene.SCRIPT_REWRITE));

        assertTrue(result.ok());
        assertEquals("结果", result.text());
        assertEquals(10, result.usage().inputTokens());
        assertEquals("pi", result.harnessName());

        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1/harness/run", recorded.getPath());
        JsonNode body = JsonUtil.mapper().readTree(recorded.getBody().readUtf8());

        assertEquals("req-1", body.get("requestId").asText());
        assertEquals("SCRIPT_REWRITE", body.get("scene").asText());
        assertEquals("deepseek", body.get("model").get("provider").asText());
        // 密钥只传变量名，Java 全程不接触密钥本身
        assertEquals("DEEPSEEK_API_KEY", body.get("model").get("apiKeyEnv").asText());
        assertFalse(body.get("model").has("apiKey"), "契约里不该出现密钥本身");
        // 工具是名字数组，不是工具定义
        assertTrue(body.get("tools").isArray());
        assertEquals("check_script", body.get("tools").get(0).asText());
        assertEquals(6, body.get("maxSteps").asInt());
    }

    @Test
    @DisplayName("harness 返回错误码时原样透出，供上层决定重试还是退额度")
    void propagatesErrorCode() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"requestId":"req-1","ok":false,"text":null,
                         "usage":{"inputTokens":0,"outputTokens":0,"costUsd":0},
                         "steps":0,"harnessName":"pi","harnessVersion":"0.85.0",
                         "errorCode":"PROVIDER_AUTH","errorMessage":"DEEPSEEK_API_KEY not configured"}
                        """));

        RunResult result = new PiHarnessClient(server.url("/").toString(), 60000)
                .run(request(Scene.SCRIPT_GENERATE));

        assertFalse(result.ok());
        assertEquals("PROVIDER_AUTH", result.errorCode());
        assertNull(result.text());
    }

    @Test
    @DisplayName("harness 进程不可用时不抛异常，转成 RunResult 失败")
    void unreachableHarnessBecomesFailure() {
        // 指一个没人监听的端口
        RunResult result = new PiHarnessClient("http://127.0.0.1:1", 2000)
                .run(request(Scene.SCRIPT_GENERATE));

        assertFalse(result.ok());
        assertEquals("PROVIDER_ERROR", result.errorCode());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("非 200 响应也要转成失败而不是崩掉")
    void nonOkHttpStatus() {
        server.enqueue(new MockResponse().setResponseCode(502).setBody("bad gateway"));
        RunResult result = new PiHarnessClient(server.url("/").toString(), 60000)
                .run(request(Scene.SCRIPT_GENERATE));
        assertFalse(result.ok());
        assertTrue(result.errorMessage().contains("502"));
    }

    @Test
    @DisplayName("健康检查解析 provider 就绪状态")
    void healthParsesProviders() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"ok":true,"harnessName":"pi","harnessVersion":"0.85.0",
                         "providers":[{"name":"deepseek","ready":true,"models":["deepseek-chat"]},
                                      {"name":"openai","ready":false,"models":["gpt-4o"]}],
                         "tools":["fetch_video","transcribe","check_script"]}
                        """));

        Health health = new PiHarnessClient(server.url("/").toString(), 60000).health();

        assertTrue(health.ok());
        assertEquals("0.85.0", health.harnessVersion());
        assertEquals(2, health.providers().size());
        assertTrue(health.providers().get(0).ready());
        assertFalse(health.providers().get(1).ready());
        assertEquals(3, health.tools().size());
        assertNotNull(health.latencyMs());
    }

    @Test
    @DisplayName("mock harness 各场景都返回结构合法的内容，无密钥也能跑通链路")
    void mockHarnessReturnsUsableShapes() {
        MockHarnessClient mock = new MockHarnessClient();
        assertEquals("mock", mock.name());
        assertTrue(mock.health().ok());

        RunResult topic = mock.run(request(Scene.TOPIC_TITLE));
        assertTrue(topic.text().contains("选题："));
        assertTrue(topic.text().contains("标题1："));

        RunResult body = mock.run(request(Scene.SCRIPT_GENERATE));
        assertTrue(com.shanchuang.common.util.TextUtil.cnWords(body.text()) >= 500,
                "假正文也要够字数，否则过不了质量门、测不出编排");
        assertTrue(body.text().contains("我") && body.text().contains("你"));

        RunResult extract = mock.run(request(Scene.VIDEO_EXTRACT));
        assertTrue(extract.text().contains("\"framework\""));
    }

    @Test
    @DisplayName("场景决定工具白名单与步数：纯生成不给工具，拆解才给")
    void sceneDefaults() {
        assertEquals(List.of(), Scene.SCRIPT_GENERATE.defaultTools());
        assertEquals(1, Scene.SCRIPT_GENERATE.defaultMaxSteps());

        assertEquals(List.of("check_script"), Scene.SCRIPT_REWRITE.defaultTools());
        assertEquals(6, Scene.SCRIPT_REWRITE.defaultMaxSteps());

        assertEquals(List.of("fetch_video", "transcribe"), Scene.VIDEO_EXTRACT.defaultTools());
        assertEquals(8, Scene.VIDEO_EXTRACT.defaultMaxSteps());

        assertEquals(Scene.SCRIPT_DEAI, Scene.of("script_deai"));
        assertNull(Scene.of("NOT_EXIST"));
    }
}
