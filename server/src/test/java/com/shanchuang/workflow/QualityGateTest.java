package com.shanchuang.workflow;

import com.shanchuang.workflow.model.GateResult;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.RewriteSections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityGateTest {

    private final QualityGate gate = new QualityGate();
    private final PersonaSnapshot persona = TestFixtures.persona();

    private boolean hasFailureAbout(GateResult result, String keyword) {
        return result.failures().stream().anyMatch(f -> f.contains(keyword));
    }

    private boolean hasWarningAbout(GateResult result, String keyword) {
        return result.warnings().stream().anyMatch(w -> w.contains(keyword));
    }

    /* ==================== 生成正文 ==================== */

    @Test
    @DisplayName("合格正文全部通过")
    void passingBody() {
        GateResult result = gate.checkScript(TestFixtures.passingBody(), persona, 0);
        assertTrue(result.passed(), result.report());
        assertTrue(result.clean(), result.report());
        assertTrue(result.words() >= persona.minWordsOrDefault());
    }

    @Test
    @DisplayName("字数低于下限要打回，并明确要求补细节而不是补废话")
    void wordsBelowMin() {
        GateResult result = gate.checkScript("我跟你说，就这么短，对吧？", persona, 0);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "低于下限"));
        assertTrue(hasFailureAbout(result, "补具体细节"));
    }

    @Test
    @DisplayName("超过字数上限也要打回")
    void wordsAboveMax() {
        GateResult result = gate.checkScript(TestFixtures.passingBody(), persona, 100);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "超过上限"));
    }

    @Test
    @DisplayName("命中人设禁用词直接失败")
    void bannedWord() {
        String body = TestFixtures.passingBody() + "\n\n我们这边保证就业，你放心来。";
        GateResult result = gate.checkScript(body, persona, 0);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "保证就业"));
    }

    @Test
    @DisplayName("残留分镜与画面说明要打回，Skill 明确只要纯口播")
    void rejectsShotDescription() {
        String body = TestFixtures.passingBody() + "\n\n【画面：办公室特写】";
        GateResult result = gate.checkScript(body, persona, 0);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "画面"));
    }

    @Test
    @DisplayName("残留写作骨架标记要打回")
    void rejectsSkeletonMarker() {
        String body = TestFixtures.passingBody() + "\n\n黄金3秒·痛点引入";
        GateResult result = gate.checkScript(body, persona, 0);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "骨架标记"));
    }

    @Test
    @DisplayName("书面腔是警告而非硬伤，但会进重写要求")
    void writtenMarkerWarns() {
        String body = TestFixtures.passingBody() + "\n\n掌握这项技能至关重要。";
        GateResult result = gate.checkScript(body, persona, 0);
        assertTrue(result.passed(), "书面腔不判死");
        assertFalse(result.clean());
        assertTrue(hasWarningAbout(result, "至关重要"));
        assertTrue(result.toRewriteInstruction().contains("至关重要"));
    }

    @Test
    @DisplayName("缺少「我」或「你」要警告：口播得像跟朋友聊天")
    void missingPronounWarns() {
        String noPronoun = "这件事其实很简单。方向对了，剩下的都是时间问题。"
                + "第一步是选一个能落地的小切口。第二步是把它做出结果。"
                + "第三步是让别人能顺着这个结果往下问。真的，就这么回事。"
                + "很多人卡住不是能力问题，是顺序搞反了。先做再学，比先学再做快得多。"
                + "说白了，做出来的东西才是证据，证书只是纸。".repeat(4);
        GateResult result = gate.checkScript(noPronoun, persona, 0);
        assertTrue(hasWarningAbout(result, "我"), result.report());
    }

    @Test
    @DisplayName("没有语气词要警告，文案得有温度")
    void missingToneMarkerWarns() {
        String flat = ("我认为方向比努力更重要。你需要先确认目标岗位的门槛。"
                + "我建议你从一个小切口开始，把它完整做完一遍。"
                + "你手里已有的经验往往比从零开始更值钱。"
                + "我见过很多人把时间花在与目标无关的地方。").repeat(6);
        GateResult result = gate.checkScript(flat, persona, 0);
        assertTrue(hasWarningAbout(result, "语气词"), result.report());
    }

    /* ==================== 洗稿十一段 ==================== */

    private Map<String, String> fullSections() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("s1", "原文讲了转行误区。");
        s.put("s2", "| 原文片段 | 所处位置 | 结构作用 |\n|---|---|---|\n| 开场 | 黄金3秒钩子 | 制造冲突 |");
        s.put("s3", "开头反常识断言，中段两处转折，结尾评论互动。");
        s.put("s4", "内容偏观点，结构前紧后松，状态是过来人劝告。");
        s.put("s5", "可以借鉴的底层逻辑：先否定错误归因。\n"
                + "需要重新论证的观点：门槛到底在哪。\n"
                + "不应该沿用的表达或材料：原作者的朋友逆袭案例。");
        s.put("s6", "补充一条真实咨询高频问题。");
        s.put("s7", "与原文最大的三个差异：论证顺序、案例、信息层。");
        s.put("s8", "实景 / 共情提问 / 反常识各一个。");
        s.put("s9", TestFixtures.passingBody());
        s.put("s10", "开头放慢，重音在方向二字。");
        s.put("s11", "开头吸引力：9 分\n中段留存能力：9 分\n内容价值：9 分\n口语自然度：9 分\n"
                + "身份可信度：8 分\n情绪感染力：8 分\n原创程度：9 分\n转化自然度：8 分\n朗读测试通过");
        return s;
    }

    private RewriteSections sectionsOf(Map<String, String> map) {
        return new RewriteSections("原视频标题", "原文".repeat(40), "我的标题",
                map, map.get("s9"), gate.parseScores(map.get("s11")),
                gate.readaloudPassed(map.get("s11")), 0, null);
    }

    @Test
    @DisplayName("十一段齐全且分数达标时通过")
    void rewritePasses() {
        GateResult result = gate.checkRewrite(sectionsOf(fullSections()), "完全不同的原文内容", persona, 800);
        assertTrue(result.passed(), result.report());
    }

    @Test
    @DisplayName("缺任一段就打回：前八段是质量门不是附录")
    void missingSectionFails() {
        Map<String, String> map = fullSections();
        map.put("s7", null);
        GateResult result = gate.checkRewrite(sectionsOf(map), "别的原文", persona, 800);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "七、重写方案"));
    }

    @Test
    @DisplayName("第五段没做完三类划分就不准写第九段")
    void missingThreeClassSplitFails() {
        Map<String, String> map = fullSections();
        map.put("s5", "原文有些地方可以参考，有些不行。");
        GateResult result = gate.checkRewrite(sectionsOf(map), "别的原文", persona, 800);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "三类划分"));
    }

    @Test
    @DisplayName("与原文连续重合超限要打回，且要求换论证路径而不是换同义词")
    void overlapFails() {
        String original = "计算机专业转人工智能方向的同学最容易在第一个月踩到框架选择这个坑";
        Map<String, String> map = fullSections();
        map.put("s9", TestFixtures.passingBody() + "\n\n" + original);
        GateResult result = gate.checkRewrite(sectionsOf(map), original, persona, 2000);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "连续重合"));
        assertTrue(hasFailureAbout(result, "换论证路径"));
        assertTrue(result.overlapMax() >= 16);
    }

    @Test
    @DisplayName("自检项低于 8 要改文案，不是改分数")
    void lowScoreFails() {
        Map<String, String> map = fullSections();
        map.put("s11", map.get("s11").replace("口语自然度：9 分", "口语自然度：6 分"));
        GateResult result = gate.checkRewrite(sectionsOf(map), "别的原文", persona, 800);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "口语自然度"));
        assertTrue(hasFailureAbout(result, "改文案"));
    }

    @Test
    @DisplayName("朗读测试不通过必须先改第九段")
    void readaloudFailFails() {
        Map<String, String> map = fullSections();
        map.put("s11", map.get("s11").replace("朗读测试通过", "朗读测试不通过"));
        GateResult result = gate.checkRewrite(sectionsOf(map), "别的原文", persona, 800);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "朗读测试"));
    }

    @Test
    @DisplayName("缺朗读测试那一行也要打回")
    void readaloudMissingFails() {
        Map<String, String> map = fullSections();
        map.put("s11", map.get("s11").replace("\n朗读测试通过", ""));
        GateResult result = gate.checkRewrite(sectionsOf(map), "别的原文", persona, 800);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "朗读测试"));
    }

    @Test
    @DisplayName("原视频标题与原文缺失时不算交付")
    void missingOriginalFails() {
        Map<String, String> map = fullSections();
        RewriteSections sections = new RewriteSections(null, "太短", "我的标题",
                map, map.get("s9"), gate.parseScores(map.get("s11")), true, 0, null);
        GateResult result = gate.checkRewrite(sections, "太短", persona, 800);
        assertFalse(result.passed());
        assertTrue(hasFailureAbout(result, "原视频标题"));
        assertTrue(hasFailureAbout(result, "原视频文案"));
    }

    @Test
    @DisplayName("8 项分数能从各种写法里解析出来")
    void parseScores() {
        Map<String, Integer> scores = gate.parseScores(
                "开头吸引力 9\n中段留存能力：8分\n内容价值: 9 分\n口语自然度：10\n"
                        + "身份可信度 8 分\n情绪感染力：8\n原创程度：9 分\n转化自然度 8");
        assertEquals(8, scores.size());
        assertEquals(9, scores.get("开头吸引力"));
        assertEquals(10, scores.get("口语自然度"));
        assertEquals(8, scores.get("转化自然度"));
    }

    @Test
    @DisplayName("朗读测试通过与否要能识别")
    void readaloudParsing() {
        assertTrue(gate.readaloudPassed("……\n朗读测试通过"));
        assertTrue(gate.readaloudPassed("朗读测试：通过"));
        assertFalse(gate.readaloudPassed("朗读测试不通过"));
        assertFalse(gate.readaloudPassed("什么都没写"));
    }
}
