package com.shanchuang.workflow;

import com.shanchuang.harness.Scene;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.RewriteSections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewritePipelineTest {

    private final PersonaSnapshot persona = TestFixtures.persona();
    private final PromptBuilder prompts = new PromptBuilder();
    private final QualityGate gate = new QualityGate();

    private static final String ORIGINAL =
            "计算机专业转 AI，很多人不是学不会，是一路踩雷。今天这期纯避雷，干到不能再干。"
                    + "AI 是交叉学科，看着什么都要学，结果学着学着方向没了，时间也没了。"
                    + "我直接给你一个三个月学习的路线图，第一个月语言加框架，第二个月分叉口，第三个月找实习。";

    private static class StubPort implements HarnessPort {
        final List<Scene> scenes = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        private final List<String> responses;
        private int cursor = 0;

        StubPort(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String run(Scene scene, String systemPrompt, List<String> msgs, List<String> tools) {
            scenes.add(scene);
            messages.add(String.join("\n", msgs));
            String out = responses.get(Math.min(cursor, responses.size() - 1));
            cursor++;
            return out;
        }
    }

    private String stageOne() {
        return """
                一、原文一句话总结
                原文讲转行踩雷，解决方向焦虑，希望观众来咨询。

                二、原文结构拆解表
                | 原文片段 | 所处位置 | 结构作用 |
                |---|---|---|
                | 开场断言 | 黄金3秒钩子 | 制造冲突 |

                三、开头、中段和结尾钩子分析
                开头反常识断言，中段两处转折，结尾评论互动略生硬。

                四、内容、结构、状态、身份、场景分析
                内容偏观点，结构前紧后松，状态是过来人劝告，场景办公室口播。

                五、原创风险与可借鉴内容
                可以借鉴的底层逻辑：先否定错误归因再给切口。
                需要重新论证的观点：门槛究竟在哪一层。
                不应该沿用的表达或材料：原作者的三个月路线图与独有数据。

                六、我的内容补充建议
                补一条真实咨询高频问题；补一个可量化的自查标准。

                七、重写方案
                核心观点：卡住的是归因方式。与原文最大的三个差异：论证顺序、案例、信息层。
                新钩子用共情提问，中段两处钩子，结尾顾问式软引导。

                八、3个不同类型的新开头
                实景：我桌上这份简历改到第三版了。
                共情提问：你有没有过明明很努力却没动静的感觉？
                反常识：这事最怕的不是不会，是会了说不清。
                """;
    }

    private String stageTwo() {
        return "九、完整原创口播文案\n" + TestFixtures.passingBody() + """

                十、拍摄时的语气、停顿和重音建议
                开头放慢，重音落在方向二字。

                十一、文案自检评分
                开头吸引力：9 分
                中段留存能力：9 分
                内容价值：9 分
                口语自然度：9 分
                身份可信度：8 分
                情绪感染力：8 分
                原创程度：9 分
                转化自然度：8 分
                朗读测试通过
                """;
    }

    private PromptBuilder.RewriteInput input() {
        return new PromptBuilder.RewriteInput(persona, "计算机专业转行学 AI 会踩的雷",
                ORIGINAL, "建立信任", "办公室口播", 800, "请你建议", List.of());
    }

    @Test
    @DisplayName("先拆后写：前八段必须先于第九段产出，倒着做一定像复述")
    void stageOrderIsEnforced() {
        StubPort port = new StubPort(List.of(stageOne(), stageTwo(), TestFixtures.passingBody()));
        RewriteSections sections = new RewritePipeline(port, prompts, gate).produce(input());

        assertEquals(3, port.scenes.size());
        assertEquals(Scene.SCRIPT_REWRITE, port.scenes.get(0));
        assertEquals(Scene.SCRIPT_REWRITE, port.scenes.get(1));
        // 第三次是去 AI 味，独立一步
        assertEquals(Scene.SCRIPT_DEAI, port.scenes.get(2));

        assertTrue(port.messages.get(0).contains("第一阶段"));
        assertTrue(port.messages.get(1).contains("第三阶段"));
        // 阶段三的提示词里必须带上第五段与第七段，它们是第九段的设计图
        assertTrue(port.messages.get(1).contains("可以借鉴的底层逻辑"));
        assertTrue(port.messages.get(1).contains("与原文最大的三个差异"));

        assertNotNull(sections.finalBody());
        assertTrue(sections.readaloudPass());
    }

    @Test
    @DisplayName("十一段都要能解析出来，交付文档带原标题与原文")
    void parsesAllSections() {
        StubPort port = new StubPort(List.of(stageOne(), stageTwo(), TestFixtures.passingBody()));
        RewriteSections sections = new RewritePipeline(port, prompts, gate).produce(input());

        for (String key : RewriteSections.KEYS) {
            assertNotNull(sections.section(key), "缺 " + key);
        }
        String md = sections.toMarkdown();
        assertTrue(md.contains("**原视频标题**：计算机专业转行学 AI 会踩的雷"));
        assertTrue(md.contains("### 原视频文案"));
        assertTrue(md.indexOf("原视频文案") < md.indexOf("一、"), "原文必须在十一段之前");
    }

    @Test
    @DisplayName("第五段缺三类划分时自动补一次，而不是直接往下写")
    void patchesMissingThreeClassSplit() {
        String incomplete = stageOne().replace(
                """
                        可以借鉴的底层逻辑：先否定错误归因再给切口。
                        需要重新论证的观点：门槛究竟在哪一层。
                        不应该沿用的表达或材料：原作者的三个月路线图与独有数据。""",
                "原文有些能参考，有些不行。");

        StubPort port = new StubPort(new ArrayList<>(List.of(
                incomplete,
                "五、原创风险与可借鉴内容\n可以借鉴的底层逻辑：A\n需要重新论证的观点：B\n不应该沿用的表达或材料：C",
                stageTwo(),
                TestFixtures.passingBody())));
        RewriteSections sections = new RewritePipeline(port, prompts, gate).produce(input());

        assertEquals(4, port.scenes.size(), "应插入一次补第五段的调用");
        assertTrue(port.messages.get(1).contains("没有做完三类划分"));
        assertTrue(sections.section("s5").contains("不应该沿用"));
    }

    @Test
    @DisplayName("段落切分不能被表格里的「一、」这类字样带偏")
    void sectionParsingIsRobust() {
        RewritePipeline pipeline = new RewritePipeline(
                new StubPort(List.of("")), prompts, gate);
        Map<String, String> parsed = pipeline.parseSections("""
                一、原文一句话总结
                这里正文提到了一、二、三点，不该被切开。

                十、拍摄建议
                第十段内容。

                十一、文案自检评分
                第十一段内容。
                """);
        assertTrue(parsed.get("s1").contains("不该被切开"));
        assertEquals("第十段内容。", parsed.get("s10").trim());
        assertEquals("第十一段内容。", parsed.get("s11").trim());
    }

    @Test
    @DisplayName("重合超限时重写第九段，并明确要求换论证路径")
    void rewritesWhenOverlapExceeded() {
        // 第一版第九段直接抄了原文一整句
        String plagiarized = "九、完整原创口播文案\n" + TestFixtures.passingBody()
                + "\n" + ORIGINAL.substring(0, 30) + """

                十、拍摄建议
                略。

                十一、文案自检评分
                开头吸引力：9 分
                中段留存能力：9 分
                内容价值：9 分
                口语自然度：9 分
                身份可信度：8 分
                情绪感染力：8 分
                原创程度：9 分
                转化自然度：8 分
                朗读测试通过
                """;

        StubPort port = new StubPort(new ArrayList<>(List.of(
                stageOne(),
                plagiarized,
                plagiarized.substring(plagiarized.indexOf("九、")),   // 去味仍带重合
                stageTwo()                                            // 定向重写后合格
        )));
        RewriteSections sections = new RewritePipeline(port, prompts, gate).produce(input());

        assertTrue(port.messages.stream().anyMatch(m -> m.contains("换论证路径")),
                "重写要求里要写明换论证路径而不是换同义词");
        assertNotNull(sections.finalBody());
    }

    @Test
    @DisplayName("身份卡要把人设与用户要求填进合同，且不许虚构履历")
    void identityCardIsInjected() {
        StubPort port = new StubPort(List.of(stageOne(), stageTwo(), TestFixtures.passingBody()));
        new RewritePipeline(port, prompts, gate).produce(input());

        String contract = port.messages.get(0);
        assertTrue(contract.contains(persona.identity()));
        assertTrue(contract.contains("建立信任"));
        assertTrue(contract.contains("办公室口播"));
        assertTrue(contract.contains("800 字以内"));
        assertTrue(contract.contains("保证就业"), "禁用词要继承人设");
        assertTrue(contract.contains("不要虚构我的履历"));
        assertTrue(contract.contains("**原视频标题**：计算机专业转行学 AI 会踩的雷"));
    }
}
