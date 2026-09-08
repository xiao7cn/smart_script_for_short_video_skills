package com.shanchuang.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.common.util.TextUtil;
import com.shanchuang.harness.Scene;

/**
 * 拆解流水线。
 *
 * 取件与转写交给 Agent 侧的工具（复用 skills 里的 Python 脚本），
 * 这里只负责发起、解析结果，以及识别「需要用户补素材」这个状态。
 */
public class TeardownPipeline {

    /** 口播语速经验值，与 skills 里 transcribe.py 的 CHARS_PER_MINUTE 保持一致 */
    private static final int CHARS_PER_MINUTE = 300;

    private final HarnessPort harness;
    private final PromptBuilder prompts;

    public TeardownPipeline(HarnessPort harness, PromptBuilder prompts) {
        this.harness = harness;
        this.prompts = prompts;
    }

    public Result produce(String url, String pastedText, String videoTitle) {
        String raw = ScriptPipeline.stripFence(harness.run(Scene.VIDEO_EXTRACT,
                prompts.teardownSystem(),
                prompts.teardownUser(url, pastedText, videoTitle)));
        return parse(raw, pastedText, videoTitle);
    }

    /**
     * 解析模型返回的 JSON。
     *
     * 模型偶尔会在 JSON 外面裹一层说明，所以先截取首个 { 到末个 }。
     * 解析不出来也不能整条失败——至少把口播稿留下来，用户还能手动接着洗稿。
     */
    Result parse(String raw, String pastedText, String fallbackTitle) {
        String json = extractJson(raw);
        JsonNode node = null;
        if (json != null) {
            try {
                node = JsonUtil.mapper().readTree(json);
            } catch (Exception ignored) {
                // 落到下面的降级分支
            }
        }

        if (node == null) {
            String transcript = TextUtil.isBlank(pastedText) ? raw : pastedText;
            return new Result(fallbackTitle, "manual", null, null, null,
                    transcript, null, null, false, null,
                    "模型未按 JSON 返回，已保留口播稿原文");
        }

        boolean needFile = node.path("needFile").asBoolean(false);
        String guide = text(node, "fetchGuide");
        String transcript = text(node, "transcript");
        if (TextUtil.isBlank(transcript) && !TextUtil.isBlank(pastedText)) {
            transcript = pastedText;
        }

        Integer durationSec = node.path("durationSec").isNumber() ? node.path("durationSec").asInt() : null;
        Integer words = node.path("words").isNumber() ? node.path("words").asInt() : null;
        Integer speechRate = node.path("speechRate").isNumber() ? node.path("speechRate").asInt() : null;

        // 粘贴进来的稿子没有时长信息，模型只能靠数字数折算，而模型数中文字数并不准
        // （实测把 6656 字数成 3632，时长跟着少算四成）。这三个量是确定性的，用代码算。
        // 走工具链路时模型手里有 transcribe 返回的真实 rhythm，那时的值比折算准，不要覆盖。
        if (!TextUtil.isBlank(pastedText)) {
            words = TextUtil.cnWords(pastedText);
            durationSec = Math.round(words * 60f / CHARS_PER_MINUTE);
            speechRate = CHARS_PER_MINUTE;
        }

        return new Result(
                firstNonBlank(text(node, "videoTitle"), fallbackTitle, "标题未知"),
                firstNonBlank(text(node, "platform"), "manual"),
                durationSec,
                words,
                speechRate,
                transcript,
                node.hasNonNull("teardown") ? node.get("teardown").toString() : null,
                node.hasNonNull("framework") ? node.get("framework").toString() : null,
                needFile,
                guide,
                null);
    }

    private static String extractJson(String raw) {
        if (TextUtil.isBlank(raw)) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!TextUtil.isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * @param needFile   true 表示取件失败、等用户补录屏。这不是失败状态：
     *                   视频号本就只能录屏，抖音也可能因浏览器未登录失败
     * @param fetchGuide 该平台的录屏指引，原样转达用户
     */
    public record Result(
            String videoTitle,
            String platform,
            Integer durationSec,
            Integer words,
            Integer speechRate,
            String transcript,
            String teardownJson,
            String frameworkJson,
            boolean needFile,
            String fetchGuide,
            String warning
    ) {
    }
}
