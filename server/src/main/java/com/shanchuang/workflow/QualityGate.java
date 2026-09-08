package com.shanchuang.workflow;

import com.shanchuang.common.util.TextUtil;
import com.shanchuang.workflow.model.GateResult;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.RewriteSections;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 质量门。
 *
 * 规则复刻 skills/video-script-rewrite/scripts/check.py，放在 Java 侧确定性执行。
 * 不交给模型自检的原因很直接：Skill 里专门写了「改文案，不改分数」——
 * 模型会给自己放水，把 7 分写成 8 分了事。
 */
public class QualityGate {

    /** 书面腔词表：命中就整句重写 */
    private static final List<String> WRITTEN_MARKERS = List.of(
            "至关重要", "核心竞争力", "不可或缺", "发展趋势表明", "综上所述",
            "首先其次最后", "值得注意的是", "在当今社会", "随着时代的发展",
            "不言而喻", "由此可见", "众所周知"
    );

    /** 语气词：口播必须有温度，至少出现一个 */
    private static final List<String> TONE_MARKERS = List.of(
            "对吧", "其实啊", "你知道吗", "咱们", "说白了", "真的", "是不是", "对不对", "你看"
    );

    private static final Pattern READALOUD = Pattern.compile("朗读测试\\s*[:：]?\\s*(通过|不通过)");
    private static final Pattern SCORE_LINE = Pattern.compile("([\\u4e00-\\u9fa5]{2,8})\\s*[:：]?\\s*(\\d{1,2})\\s*分?");

    /**
     * 校验生成的口播正文。
     *
     * @param maxWords 上限，<=0 表示不限
     */
    public GateResult checkScript(String body, PersonaSnapshot persona, int maxWords) {
        GateResult.Builder builder = new GateResult.Builder();
        int words = TextUtil.cnWords(body);
        builder.words(words);

        if (TextUtil.isBlank(body)) {
            return builder.fail("正文为空").build();
        }

        int minWords = persona.minWordsOrDefault();
        if (words < minWords) {
            builder.fail("正文 " + words + " 字，低于下限 " + minWords
                    + " 字。请补具体细节（场景、过程、代价），不是补回废话");
        }
        if (maxWords > 0 && words > maxWords) {
            builder.fail("正文 " + words + " 字，超过上限 " + maxWords + " 字，请压缩到上限内");
        }

        for (String banned : persona.bannedOrEmpty()) {
            if (!TextUtil.isBlank(banned) && body.contains(banned)) {
                builder.fail("命中禁用词「" + banned + "」，通篇不得出现");
            }
        }

        // 画面与分镜说明：Skill 明确只要纯口播
        if (body.contains("【画面") || body.contains("镜头：") || body.contains("分镜")) {
            builder.fail("出现画面/分镜说明，只要纯口播文案");
        }
        // 骨架标记只用于组织结构，不该留在交付正文里
        if (body.contains("黄金3秒") || body.contains("黄金 3 秒") || body.contains("痛点引入")) {
            builder.fail("正文残留写作骨架标记，交付稿里必须去掉");
        }

        appendOralWarnings(body, builder);
        return builder.build();
    }

    /**
     * 校验洗稿十一段。
     *
     * @param maxWords 第九段字数上限，来自用户的期望字数
     */
    public GateResult checkRewrite(RewriteSections sections, String originalBody,
                                   PersonaSnapshot persona, int maxWords) {
        GateResult.Builder builder = new GateResult.Builder();

        if (TextUtil.isBlank(sections.originalTitle())) {
            builder.fail("缺原视频标题。没有就写「标题未知」，不准用洗稿标题顶替");
        }
        if (TextUtil.cnWords(sections.originalBody()) < 50) {
            builder.fail("缺原视频文案或原文过短，洗稿文档必须自带完整原文");
        }

        for (int i = 0; i < RewriteSections.KEYS.length; i++) {
            String content = sections.section(RewriteSections.KEYS[i]);
            if (TextUtil.isBlank(content)) {
                builder.fail("缺「" + RewriteSections.TITLES[i] + "」，十一段是质量门不是附录");
            }
        }

        // 第五段三类划分是第九段的前置条件，Skill 里是硬约束
        String s5 = sections.section("s5");
        if (!TextUtil.isBlank(s5)
                && !(s5.contains("可以借鉴") && s5.contains("重新论证") && s5.contains("不应该沿用"))) {
            builder.fail("第五段缺三类划分（可以借鉴 / 需要重新论证 / 不应该沿用），没做完不准写第九段");
        }

        String s7 = sections.section("s7");
        if (!TextUtil.isBlank(s7) && !s7.contains("差异")) {
            builder.fail("第七段必须写出与原文最大的三个差异，且落在论证顺序、案例和信息上");
        }

        String body = sections.finalBody();
        int words = TextUtil.cnWords(body);
        builder.words(words);

        if (TextUtil.isBlank(body)) {
            builder.fail("第九段正文为空");
        } else {
            if (maxWords > 0 && words > maxWords) {
                builder.fail("第九段 " + words + " 字，超过约定上限 " + maxWords + " 字");
            }
            int minWords = Math.min(persona.minWordsOrDefault(), maxWords > 0 ? maxWords : Integer.MAX_VALUE);
            if (words < minWords) {
                builder.fail("第九段 " + words + " 字，低于下限 " + minWords + " 字");
            }
            for (String banned : persona.bannedOrEmpty()) {
                if (!TextUtil.isBlank(banned) && body.contains(banned)) {
                    builder.fail("第九段命中禁用词「" + banned + "」");
                }
            }
            appendOralWarnings(body, builder);

            int overlap = TextUtil.longestOverlap(body, originalBody);
            builder.overlapMax(overlap);
            if (overlap >= TextUtil.OVERLAP_THRESHOLD) {
                builder.fail("与原文连续重合 " + overlap + " 字（阈值 "
                        + TextUtil.OVERLAP_THRESHOLD + "），还在复述原句。要换论证路径，不是换同义词");
            }
        }

        String s11 = sections.section("s11");
        if (!TextUtil.isBlank(s11)) {
            Matcher m = READALOUD.matcher(s11);
            if (!m.find()) {
                builder.fail("第十一段末尾缺「朗读测试通过 / 不通过」这一行");
            } else if ("不通过".equals(m.group(1))) {
                builder.fail("朗读测试不通过，逐句改拗口处后再交");
            }
            Map<String, Integer> scores = parseScores(s11);
            for (String dim : RewriteSections.SCORE_DIMENSIONS) {
                Integer score = scores.get(dim);
                if (score == null) {
                    builder.warn("第十一段缺「" + dim + "」评分");
                } else if (score < 8) {
                    builder.fail("自检项「" + dim + "」" + score + " 分低于 8，必须改文案（不是改分数）");
                }
            }
        }

        return builder.build();
    }

    /** 口语化检查。缺人称、缺语气词、有书面腔都算警告，会拼进重写要求 */
    private void appendOralWarnings(String body, GateResult.Builder builder) {
        if (TextUtil.isBlank(body)) {
            return;
        }
        for (String marker : WRITTEN_MARKERS) {
            if (body.contains(marker)) {
                builder.warn("出现书面腔「" + marker + "」，整句重写成说话的样子");
            }
        }
        if (!body.contains("我") || !body.contains("你")) {
            builder.warn("通篇缺少「我」或「你」，要像跟朋友面对面聊天");
        }
        if (TONE_MARKERS.stream().noneMatch(body::contains)) {
            builder.warn("没有语气词，给文案加点温度（对吧？其实啊、你知道吗？），但不要每句都加");
        }
    }

    /** 从第十一段里抠出 8 项分数。模型的写法五花八门，用宽松正则兜 */
    public Map<String, Integer> parseScores(String s11) {
        Map<String, Integer> scores = new java.util.LinkedHashMap<>();
        if (TextUtil.isBlank(s11)) {
            return scores;
        }
        Matcher m = SCORE_LINE.matcher(s11);
        while (m.find()) {
            String dim = m.group(1);
            for (String known : RewriteSections.SCORE_DIMENSIONS) {
                if (dim.contains(known) || known.contains(dim)) {
                    scores.putIfAbsent(known, Integer.parseInt(m.group(2)));
                    break;
                }
            }
        }
        return scores;
    }

    public boolean readaloudPassed(String s11) {
        if (TextUtil.isBlank(s11)) {
            return false;
        }
        Matcher m = READALOUD.matcher(s11);
        return m.find() && "通过".equals(m.group(1));
    }
}
