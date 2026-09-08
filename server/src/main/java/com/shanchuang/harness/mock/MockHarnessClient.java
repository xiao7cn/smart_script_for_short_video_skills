package com.shanchuang.harness.mock;

import com.shanchuang.harness.HarnessClient;
import com.shanchuang.harness.HarnessContract.Health;
import com.shanchuang.harness.HarnessContract.ProviderHealth;
import com.shanchuang.harness.HarnessContract.RunRequest;
import com.shanchuang.harness.HarnessContract.RunResult;
import com.shanchuang.harness.HarnessContract.Usage;
import com.shanchuang.harness.Scene;

import java.math.BigDecimal;
import java.util.List;

/**
 * 离线 harness。
 *
 * 用途有两个：没有模型密钥时也能把前后端链路跑通；单元测试里让 Pipeline 拿到
 * 结构合法的产出，从而验证编排逻辑（调用顺序、质量门、重试）而不是验证模型输出。
 * 返回的假文案刻意做成能通过质量门的样子——有「我」「你」、有语气词、字数够。
 */
public class MockHarnessClient implements HarnessClient {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public RunResult run(RunRequest request) {
        Scene scene = Scene.of(request.scene());
        String text = switch (scene == null ? Scene.SCRIPT_GENERATE : scene) {
            case TOPIC_TITLE -> topicTitle();
            case SCRIPT_GENERATE, SCRIPT_DEAI -> body();
            case SCRIPT_REWRITE -> rewrite(request);
            case VIDEO_EXTRACT -> teardown();
        };
        return new RunResult(request.requestId(), true, text,
                new Usage(1200, 800, new BigDecimal("0.001000")), 1,
                name(), "mock-1.0", List.of(), null, null);
    }

    @Override
    public Health health() {
        return new Health(true, name(), "mock-1.0", 1,
                List.of(new ProviderHealth("mock", true, List.of("mock-model"))),
                List.of("fetch_video", "transcribe", "check_script"), null);
    }

    private String topicTitle() {
        return """
                选题：普通人想在 AI 就业上突围，最该先搞清楚的一件事
                标题1：你以为的 AI 岗位，和真实的差了十万八千里
                标题2：花最少的钱，把这件事搞明白
                标题3：我见过最离谱的一种转行走法
                """;
    }

    /** 刻意写成能过质量门的样子：够字数、有我/你、有语气词、无禁用词 */
    private String body() {
        String para = "你知道吗？我见过太多人卡在同一个地方。不是不够努力，是方向没找对。"
                + "我先说个可能不太中听的判断：这块的真正门槛，跟大家想的不一样。"
                + "第一件事，别再盲目堆时间。我见过有人学了半年，一到实战全崩，问题不是学得少，是学杂了。"
                + "第二件事，找一个你能落地的小切口，把一件重复又烦人的事做出个结果来，对吧？"
                + "它比十张证书都有用，因为别人能顺着它往下问。";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(para).append("\n\n");
        }
        sb.append("其实啊，你现在最想解决的是哪一个？评论区说说你的情况，我帮你看看。");
        return sb.toString();
    }

    private String rewrite(RunRequest request) {
        boolean stageOne = request.messages().stream()
                .anyMatch(m -> m.content() != null && m.content().contains("第一阶段"));
        if (stageOne) {
            return """
                    一、原文一句话总结
                    原文讲了转行路上的常见误区，解决的是方向焦虑，希望观众看完来咨询。

                    二、原文结构拆解表
                    | 原文片段 | 所处位置 | 结构作用 | 使用手法 | 调动的情绪 | 存在的问题 | 可以如何优化 |
                    |---|---|---|---|---|---|---|
                    | 开场断言 | 黄金3秒钩子 | 制造冲突 | 反常识 | 焦虑 | 略贩卖焦虑 | 换成共情提问 |

                    三、开头、中段和结尾钩子分析
                    开头用反常识断言；中段有两处转折；结尾用评论互动，略生硬。

                    四、内容、结构、状态、身份、场景分析
                    内容偏观点，结构前紧后松，状态是过来人劝告，身份靠从业经验背书，场景适合办公室口播。

                    五、原创风险与可借鉴内容
                    可以借鉴的底层逻辑：先否定错误归因，再给可执行切口。
                    需要重新论证的观点：门槛到底在哪。
                    不应该沿用的表达或材料：原作者的朋友逆袭案例、独有数据、标志性金句。

                    六、我的内容补充建议
                    补充一条真实咨询里的高频问题；补充一个可量化的判断标准。

                    七、重写方案
                    核心观点：卡住的是归因方式，不是努力程度。
                    与原文最大的三个差异：一是论证顺序改为先给判断标准再给行动；二是案例换成一般性咨询场景；三是新增「怎么自查」这一层信息。
                    新的黄金3秒钩子：共情提问式开场。
                    中段钩子：两处，分别是「最关键的是」和一个风险警告。
                    结尾转化：顾问式软引导。

                    八、3个不同类型的新开头
                    实景：我桌上这份简历，改到第三版了。
                    共情提问：你有没有过这种感觉，明明很努力，就是没动静？
                    反常识：这事儿最怕的不是不会，是会了说不清。
                    """;
        }
        return """
                九、完整原创口播文案
                """ + body() + """

                十、拍摄时的语气、停顿和重音建议
                开头放慢，第二句加重「方向」二字，中段每个转折前停半拍。

                十一、文案自检评分
                开头吸引力 9；中段留存能力 9；内容价值 9；口语自然度 9；
                身份可信度 8；情绪感染力 8；原创程度 9；转化自然度 8。
                朗读测试通过
                """;
    }

    private String teardown() {
        return """
                {
                  "videoTitle": "标题未知",
                  "platform": "manual",
                  "teardown": {
                    "hook": { "type": "反常识观点", "seconds": 0, "text": "开场断言", "why": "制造冲突", "weakness": "略贩卖焦虑" },
                    "midHooks": [ { "seconds": 27, "text": "最关键的是", "device": "进度提醒" } ],
                    "ending": { "type": "评论互动", "natural": false, "advice": "换成顾问式软引导" },
                    "skeleton": [ { "seq": 1, "role": "黄金3秒钩子", "summary": "反常识断言锁定人群" } ],
                    "grid": { "inner": "AI就业", "middle": "培训", "outer": "待转行" },
                    "element": "最差"
                  },
                  "framework": {
                    "steps": ["用一句反常识断言锁定人群", "承诺纯干货降低戒心", "给三步清单", "结尾引导互动"],
                    "insights": [ { "angle": "把长期学习拆成月度里程碑", "why": "让不可量化的焦虑变得可量化" } ]
                  }
                }
                """;
    }
}
