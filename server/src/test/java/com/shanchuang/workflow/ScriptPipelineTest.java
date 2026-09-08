package com.shanchuang.workflow;

import com.shanchuang.harness.Scene;
import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.ParamCard;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.ScriptDraft;
import com.shanchuang.workflow.model.WizardSel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pipeline 的单测不碰模型也不碰 Spring：HarnessPort 是个 lambda，
 * 所以这里验证的是编排逻辑（调用顺序、去味是否独立、质量门是否重试），
 * 而不是模型输出质量。
 */
class ScriptPipelineTest {

    private final OptionCatalog catalog = TestFixtures.catalog();
    private final PersonaSnapshot persona = TestFixtures.persona();
    private final PromptBuilder prompts = new PromptBuilder();
    private final QualityGate gate = new QualityGate();

    private ParamCard card() {
        return new ParamPicker(catalog).pick(WizardSel.empty(), 1, 1L).get(0);
    }

    /** 记录每次调用的场景与提示词，用来断言编排 */
    private static class RecordingPort implements HarnessPort {
        final List<Scene> scenes = new ArrayList<>();
        final List<String> userMessages = new ArrayList<>();
        private final List<String> responses;
        private int cursor = 0;

        RecordingPort(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String run(Scene scene, String systemPrompt, List<String> messages, List<String> tools) {
            scenes.add(scene);
            userMessages.add(String.join("\n", messages));
            String response = responses.get(Math.min(cursor, responses.size() - 1));
            cursor++;
            return response;
        }
    }

    @Test
    @DisplayName("写作与去 AI 味必须是两次独立调用，不能合并成一次")
    void writeAndDeaiAreSeparateCalls() {
        RecordingPort port = new RecordingPort(List.of(
                "选题：普通人怎么入门\n标题1：你以为的和真实的差很远",
                "初稿正文（会被去味覆盖）",
                TestFixtures.passingBody()
        ));
        ScriptDraft draft = new ScriptPipeline(port, prompts, gate).produce(card(), persona, catalog);

        assertEquals(List.of(Scene.TOPIC_TITLE, Scene.SCRIPT_GENERATE, Scene.SCRIPT_DEAI), port.scenes);
        assertEquals("初稿正文（会被去味覆盖）", draft.draftBody(), "初稿要留着，便于比对");
        assertEquals(TestFixtures.passingBody(), draft.body());
    }

    @Test
    @DisplayName("选题与标题按格式解析，标题取第一个")
    void parsesTopicAndTitle() {
        RecordingPort port = new RecordingPort(List.of(
                "选题：毕业生在薪资上最该搞清楚的一件事\n标题1：花最少的钱把这件事搞明白\n标题2：另一个\n标题3：还有一个",
                "初稿", TestFixtures.passingBody()));
        ScriptDraft draft = new ScriptPipeline(port, prompts, gate).produce(card(), persona, catalog);

        assertEquals("毕业生在薪资上最该搞清楚的一件事", draft.topic());
        assertEquals("花最少的钱把这件事搞明白", draft.title());
    }

    @Test
    @DisplayName("模型没按格式给选题时兜底，不能因为一个格式问题让整条失败")
    void fallsBackWhenFormatBroken() {
        RecordingPort port = new RecordingPort(List.of(
                "我觉得这个选题挺好的，你看着写吧",
                "初稿", TestFixtures.passingBody()));
        ScriptDraft draft = new ScriptPipeline(port, prompts, gate).produce(card(), persona, catalog);

        assertNotNull(draft.topic());
        assertNotNull(draft.title());
        assertTrue(draft.topic().length() > 0);
    }

    @Test
    @DisplayName("质量门不通过时做一次定向重写")
    void retriesOnceWhenGateFails() {
        // 第 3 次返回字数不足的稿，第 4 次（定向重写）返回合格稿
        RecordingPort port = new RecordingPort(new ArrayList<>(List.of(
                "选题：测试选题\n标题1：测试标题",
                "初稿",
                "太短了，过不了字数",
                TestFixtures.passingBody()
        )));
        ScriptDraft draft = new ScriptPipeline(port, prompts, gate).produce(card(), persona, catalog);

        assertEquals(4, port.scenes.size(), "应有一次定向重写");
        assertEquals(Scene.SCRIPT_DEAI, port.scenes.get(3));
        assertTrue(port.userMessages.get(3).contains("低于下限"), "重写要求里要带上失败原因");
        assertEquals(TestFixtures.passingBody(), draft.body());
    }

    @Test
    @DisplayName("重试后仍不合格则抛 GateFailedException，由上层退额度")
    void failsAfterRetryExhausted() {
        RecordingPort port = new RecordingPort(List.of(
                "选题：测试\n标题1：测试", "初稿", "还是太短", "依然太短"));
        ScriptPipeline pipeline = new ScriptPipeline(port, prompts, gate);

        ScriptPipeline.GateFailedException ex = assertThrows(
                ScriptPipeline.GateFailedException.class,
                () -> pipeline.produce(card(), persona, catalog));
        assertTrue(ex.failures().stream().anyMatch(f -> f.contains("低于下限")));
    }

    @Test
    @DisplayName("用户手改的提示词会替换正文那一步的 user 段")
    void promptOverrideReplacesBodyPrompt() {
        RecordingPort port = new RecordingPort(List.of(
                "选题：测试\n标题1：测试", "初稿", TestFixtures.passingBody()));
        new ScriptPipeline(port, prompts, gate)
                .produce(card(), persona, catalog, "我自己改过的提示词");

        assertEquals("我自己改过的提示词", port.userMessages.get(1));
    }

    @Test
    @DisplayName("Vlog 类要提示替换真实素材，其它类型不提示")
    void vlogNeedsMaterialHint() {
        ParamCard vlog = new ParamCard(0, "破圈类", "客户咨询高频提问", "AI就业", "培训",
                "普通人", "怀旧", "Vlog 叙事", "开场引入 + 片段拼接 + 结尾感悟",
                null, List.of(), false);
        RecordingPort port = new RecordingPort(List.of(
                "选题：测试\n标题1：测试", "初稿", TestFixtures.passingBody()));
        ScriptDraft draft = new ScriptPipeline(port, prompts, gate).produce(vlog, persona, catalog);
        assertNotNull(draft.needsMaterial());
        assertTrue(draft.needsMaterial().contains("真实接待过的人"));

        ParamCard normal = card();
        RecordingPort port2 = new RecordingPort(List.of(
                "选题：测试\n标题1：测试", "初稿", TestFixtures.passingBody()));
        ScriptDraft draft2 = new ScriptPipeline(port2, prompts, gate).produce(normal, persona, catalog);
        if (!normal.isVlog()) {
            assertNull(draft2.needsMaterial());
        }
    }

    @Test
    @DisplayName("模型把正文包在代码块里时要剥掉")
    void stripsCodeFence() {
        String fenced = "```markdown\n" + TestFixtures.passingBody() + "\n```";
        RecordingPort port = new RecordingPort(List.of(
                "选题：测试\n标题1：测试", "初稿", fenced));
        ScriptDraft draft = new ScriptPipeline(port, prompts, gate).produce(card(), persona, catalog);
        assertTrue(draft.body().startsWith("你知道吗"), draft.body().substring(0, 20));
    }

    @Test
    @DisplayName("提示词里要注入人设六要素与禁用词，概述过的要求模型不执行")
    void promptCarriesPersonaAndBanned() {
        RecordingPort port = new RecordingPort(List.of(
                "选题：测试\n标题1：测试", "初稿", TestFixtures.passingBody()));
        new ScriptPipeline(port, prompts, gate).produce(card(), persona, catalog);

        String bodyPrompt = port.userMessages.get(1);
        assertTrue(bodyPrompt.contains(persona.identity()));
        assertTrue(bodyPrompt.contains(persona.audience()));
        assertTrue(bodyPrompt.contains("保证就业"), "禁用词要出现在硬性红线里");
        assertTrue(bodyPrompt.contains("不要分镜"));
        assertTrue(bodyPrompt.contains(String.valueOf(persona.minWordsOrDefault())));
    }
}
