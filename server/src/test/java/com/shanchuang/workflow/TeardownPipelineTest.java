package com.shanchuang.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拆解结果的解析。重点是那三个度量字段该信谁：
 * 粘贴链路信代码，工具链路信模型（它手里有 transcribe 返回的真实 rhythm）。
 */
class TeardownPipelineTest {

    private final TeardownPipeline pipeline = new TeardownPipeline(null, new PromptBuilder());

    private static String json(int durationSec, int words, int speechRate) {
        return """
                {
                  "videoTitle": "标题", "platform": "manual",
                  "durationSec": %d, "words": %d, "speechRate": %d,
                  "transcript": "正文", "needFile": false,
                  "teardown": { "hook": { "type": "共情提问", "seconds": 0 } },
                  "framework": { "steps": ["抛痛点"] }
                }
                """.formatted(durationSec, words, speechRate);
    }

    @Test
    @DisplayName("粘贴链路：模型数错的字数与时长被代码算的值覆盖")
    void pastedTextOverridesModelMetrics() {
        // 300 字，按 300 字/分钟正好是 60 秒。模型给的 3632/726 是实测里它数错的量级。
        String pasted = "计算机毕业找不到工作先别急。".repeat(20) + "补".repeat(20);
        int expectedWords = pasted.length();

        TeardownPipeline.Result r = pipeline.parse(json(726, 3632, 0), pasted, "标题");

        assertEquals(expectedWords, r.words());
        assertEquals(Math.round(expectedWords * 60f / 300), r.durationSec());
        assertEquals(300, r.speechRate());
    }

    @Test
    @DisplayName("粘贴链路：字数按去空白后的码点计，不受换行与空格影响")
    void pastedWordCountIgnoresWhitespace() {
        TeardownPipeline.Result withBreaks = pipeline.parse(json(0, 0, 0), "一句话\n又一句 话\n", "标题");
        TeardownPipeline.Result flat = pipeline.parse(json(0, 0, 0), "一句话又一句话", "标题");

        assertEquals(flat.words(), withBreaks.words());
        assertEquals(7, flat.words());
    }

    @Test
    @DisplayName("工具链路：没有粘贴文本时保留模型返回的度量，那是基于真实 rhythm 的")
    void toolPathKeepsModelMetrics() {
        TeardownPipeline.Result r = pipeline.parse(json(1026, 6656, 389), null, "标题");

        assertEquals(1026, r.durationSec());
        assertEquals(6656, r.words());
        assertEquals(389, r.speechRate());
    }

    @Test
    @DisplayName("模型没按 JSON 返回时不整条失败，口播稿要留住")
    void nonJsonKeepsTranscript() {
        TeardownPipeline.Result r = pipeline.parse("模型开始闲聊，没给 JSON", "口播原文", "标题");

        assertEquals("口播原文", r.transcript());
        assertTrue(r.warning() != null && !r.warning().isBlank());
    }
}
