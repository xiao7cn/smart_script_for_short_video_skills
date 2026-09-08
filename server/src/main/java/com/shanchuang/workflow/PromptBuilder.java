package com.shanchuang.workflow;

import com.shanchuang.common.util.TextUtil;
import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.ParamCard;
import com.shanchuang.workflow.model.PersonaSnapshot;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 提示词拼装。
 *
 * 七步法与十一段合同的规则原样落进提示词，硬要求逐条列出而不是概述——
 * 概述过的要求模型基本不执行。去 AI 味提示词从资源文件读，与 Skill 保持单一真源。
 */
public class PromptBuilder {

    private static final String DEAI_FALLBACK = """
            请全面化身为顶级语言风格编辑、真人表达校准师与AI痕迹清除专家。
            在不改变原文事实、核心观点和重要信息的前提下，对全文进行深度重写。
            删除空洞开场、正确但没有信息量的废话、机械连接词、过度完整的排比、重复总结、虚假的情绪升华和教科书式表达。
            重新调整句子长短、停顿节奏、观点顺序和用词密度，加入符合语境的真实细节、自然转折和明确立场，允许存在克制的不完美。
            禁止堆砌网络热词、故意口语化、编造个人经历、增加原文没有的事实，以及把文章改成另一种模板化文风。
            """;

    private final String deaiPrompt;

    public PromptBuilder() {
        this.deaiPrompt = loadResource("prompts/deai-prompt.txt", DEAI_FALLBACK);
    }

    public String deaiPrompt() {
        return deaiPrompt;
    }

    /* ==================== 选题与标题 ==================== */

    public String topicTitleSystem() {
        return "你是资深短视频选题策划。选题是贯穿全文的内容主线，看到就知道大致范围；"
                + "标题只为让人停留，可以不透露结论，但不能承诺正文给不了的东西。"
                + "只输出要求的字段，不要解释。";
    }

    /**
     * 选题 = 话题方向 + 爆款元素。
     * 顺序不能颠倒：先用内圈链接中圈/外圈框死范围，再套元素改变呈现角度。
     */
    public String topicTitleUser(ParamCard card, PersonaSnapshot persona, OptionCatalog catalog) {
        String elementHint = catalog.element(card.element())
                .map(OptionCatalog.ViralElement::hint)
                .orElse("");

        StringBuilder sb = new StringBuilder();
        sb.append("【人设】").append(persona.model()).append("｜").append(persona.identity()).append('\n');
        sb.append("【目标人群】").append(persona.audience()).append('\n');
        sb.append("【价值定位】").append(persona.value()).append("\n\n");

        sb.append("【选题类型】").append(card.topicType()).append("类选题\n");
        sb.append("【选题来源】").append(nz(card.source(), "不限")).append('\n');
        sb.append("【25 宫格配对】").append(card.grid()).append('\n');
        sb.append("【爆款元素】").append(card.element());
        if (!elementHint.isBlank()) {
            sb.append("（句式参考：").append(elementHint).append("）");
        }
        sb.append("\n\n");

        if (!TextUtil.isBlank(card.topicDraft())) {
            sb.append("【用户指定的选题方向】").append(card.topicDraft().trim()).append('\n');
        }
        if (!card.refs().isEmpty()) {
            sb.append("【对标参考链接】").append(String.join("  ", card.refs())).append('\n');
        }
        if (card.autoSearch()) {
            sb.append("【自动搜索】开启：优先参考同选题高赞爆款的结构，但只借结构不抄文案。\n");
        }

        sb.append("""

                请按以下步骤输出：
                1. 先用 25 宫格配对得到一个被框死在具体范围里的话题方向；
                2. 再套爆款元素把话题方向变成选题——元素只改变呈现角度，不改变事实；
                3. 套完自查：正文能不能真的把这个角度讲实？讲不实就换个角度，别硬套。

                严格按这个格式输出，不要多余内容：
                选题：<一句话，是概念与话题领域，不是标题>
                标题1：<吸睛标题>
                标题2：<吸睛标题>
                标题3：<吸睛标题>
                """);

        if (!persona.bannedOrEmpty().isEmpty()) {
            sb.append("\n【硬性红线】通篇避开这些词：").append(String.join("、", persona.bannedOrEmpty())).append('\n');
        }
        return sb.toString();
    }

    /* ==================== 口播正文 ==================== */

    public String bodySystem(PersonaSnapshot persona) {
        return "你现在是一名资深的短视频文案写手，写文案前会搜罗分析同选题的高赞视频做参考，"
                + "再按脚本结构撰写口播文案。全程口语化，多用「我」和「你」，一句话只讲一件事，"
                + "写完能读出声、你奶奶也听得懂。只要纯文案，不要画面，不低于 "
                + persona.minWordsOrDefault() + " 字。";
    }

    /** 步骤 4 的九条硬要求逐条列出，概述过的要求模型不执行 */
    public String bodyUser(ParamCard card, PersonaSnapshot persona, String topic, String title) {
        int minWords = persona.minWordsOrDefault();
        StringBuilder sb = new StringBuilder();

        sb.append("【人设】").append(persona.model()).append("｜").append(persona.identity()).append('\n');
        sb.append("【价值定位】").append(persona.value()).append('\n');
        sb.append("【目标人群】").append(persona.audience()).append('\n');
        sb.append("【表达风格】").append(persona.tone()).append('\n');
        if (persona.needs() != null && !persona.needs().isEmpty()) {
            sb.append("【受众痛点】").append(String.join("；", persona.needs())).append('\n');
        }
        sb.append('\n');

        sb.append("【选题类型】").append(card.topicType()).append("类选题\n");
        sb.append("【选题来源】").append(nz(card.source(), "不限")).append('\n');
        sb.append("【25 宫格配对】").append(card.grid()).append('\n');
        sb.append("【爆款元素】").append(card.element()).append('\n');
        sb.append("【选题】").append(topic).append('\n');
        sb.append("【标题】").append(title).append("\n\n");

        sb.append("【脚本类型】").append(card.scriptType()).append('\n');
        sb.append("【结构公式】").append(card.formula()).append("\n\n");

        sb.append("【硬性要求】\n");
        sb.append("1. 严格按结构公式走，不要拿痛点科普的编号并列去套其他三类；\n");
        sb.append("2. 正文不低于 ").append(minWords).append(" 字；\n");
        sb.append("3. 只要纯口播文案，不要分镜、不要画面描述、不要运镜说明；\n");
        sb.append("4. 开头 3 秒用实景开场、共情提问、反常识观点之一，且必须与正文强相关；\n");
        sb.append("5. 中段每 50-75 字埋一个钩子点（口播约每分钟 300 字），")
                .append(minWords).append(" 字至少 3 个；\n");
        sb.append("6. 70% 篇幅只讲 1-2 个核心点，其余一笔带过——信息过载比文案长更致命；\n");
        sb.append("7. 通篇「我」和「你」，自然带语气词（对吧？其实啊、你知道吗？），一句话只讲一件事；\n");
        sb.append("8. 软引导按「").append(nz(persona.ctaStyle(), "评论区互动"))
                .append("」只放结尾，不超过全文 10%");
        if (!TextUtil.isBlank(persona.ctaAsset())) {
            sb.append("，钩子资产是「").append(persona.ctaAsset()).append("」");
        }
        sb.append("；\n");
        sb.append("9. 数据给区间或给出处，主动说清代价与门槛（「出差比较多」「数学基础不能太差」")
                .append("这类负面信息反而提升可信度）。\n");

        if (!persona.bannedOrEmpty().isEmpty()) {
            sb.append("\n【硬性红线】通篇避开这些词：")
                    .append(String.join("、", persona.bannedOrEmpty())).append("。\n");
        }
        if (card.isVlog()) {
            sb.append("\n【Vlog 提示】片段里的身份、专业、对话细节写成一般性场景，")
                    .append("不要编造具体的个人经历与客户数据。\n");
        }
        sb.append("\n写作骨架标记（如「黄金 3 秒·痛点引入」）只用于自己组织结构，")
                .append("输出的正文里不保留。直接给正文，段落之间空行，不要任何前言与说明。\n");

        return sb.toString();
    }

    /* ==================== 去 AI 味 ==================== */

    public String deaiSystem() {
        return "你是顶级语言风格编辑与 AI 痕迹清除专家。只输出重写后的正文，不要任何说明。";
    }

    /**
     * 去 AI 味必须是独立一次调用。
     * 同一次生成里既写又改，模型会保留自己的表达习惯——这是 Skill 里写死的约束。
     */
    public String deaiUser(String draft, PersonaSnapshot persona) {
        StringBuilder sb = new StringBuilder(deaiPrompt);
        sb.append('\n');
        sb.append("另外要守住三条：字数仍不低于 ").append(persona.minWordsOrDefault())
                .append(" 字；事实与数据不许改动；软引导仍在结尾。\n");
        if (!persona.bannedOrEmpty().isEmpty()) {
            sb.append("通篇仍要避开这些词：").append(String.join("、", persona.bannedOrEmpty())).append("。\n");
        }
        sb.append("跌破字数下限时补具体细节，不是补回废话。\n\n");
        sb.append("以下是需要重写的原文：\n\n").append(draft);
        return sb.toString();
    }

    /* ==================== 洗稿十一段 ==================== */

    public String rewriteSystem() {
        return "你是一名资深短视频内容策划和爆款文案编剧，擅长分析短视频的传播结构、情绪节奏、"
                + "用户痛点、人物身份和转化逻辑。你的任务不是逐句改写、替换同义词，也不是复刻原作者的独特表达。"
                + "找对标不等于抄文案：拆解同选题的文案结构，搭建属于用户自己的逻辑架构。"
                + "严格按要求的段落顺序输出，不要跳过、不要合并、不要精简表格。";
    }

    /** 阶段一 + 二：产出一~八段。前八段是第九段的设计图，必须先落定 */
    public String rewriteStageOneUser(RewriteInput input) {
        StringBuilder sb = new StringBuilder();
        appendIdentityCard(sb, input);
        sb.append("\n# 原短视频文案\n\n").append(input.originalBody()).append("\n\n");
        sb.append("""
                # 第一阶段：拆解原短视频

                请先分析原文，不要马上改写。

                1. 一句话总结：这条视频讲了什么、解决受众什么问题、希望观众产生什么行动。
                2. 内容结构拆解表，按这个表头逐段分析：
                   | 原文片段 | 所处位置 | 结构作用 | 使用手法 | 调动的情绪 | 存在的问题 | 可以如何优化 |
                   「所处位置」可取：黄金3秒钩子、痛点放大、身份背书、场景代入、制造冲突、颠覆认知、
                   中段钩子、核心干货、案例或证据、情绪递进、观点总结、结尾转化。
                3. 钩子拆解：分开写开头黄金3秒钩子、中段钩子、结尾钩子。
                   开头要写清用了什么钩子、钩住哪类人、利用什么心理、为什么会继续看、有什么不足；
                   中段要找出防止划走的句子（抛出新问题/制造悬念/观点转折/利益承诺/风险警告/反常识/进度提醒），
                   原文缺中段钩子就明确指出，并设计 2~3 个可加入的；
                   结尾要判断是否生硬并给优化方向。
                4. 五个维度拆解：内容、结构、状态、身份、场景，各自单独成段。
                   身份那一段要判断哪些身份信息不适合直接沿用。
                5. 原创风险检查：标记不能照搬的部分，然后把原文分成三类，
                   必须原样出现这三个小标题——「可以借鉴的底层逻辑」「需要重新论证的观点」「不应该沿用的表达或材料」。
                   原作者的个人经历、独有数据、标志性金句、连续句式一律归到第三类。
                6. 我的内容补充建议：给 2~4 条。

                # 第二阶段：制定重写方案

                7. 重写方案，必须包含：新文案的核心观点；与原文最大的三个差异
                   （差异必须落在论证顺序、案例和信息上，不能只换同义词）；新的内容结构；
                   新的黄金3秒钩子；至少两个中段钩子；新增的观点或解释；
                   适合我身份的可信度表达；结尾的转化方式；如何降低与原文的表达相似度。
                8. 3 个不同类型的新开头：实景、共情提问、反常识各一个，不要三个同一种。

                不要虚构我的履历、客户数量、收入、效果数据或成功案例。缺事实就用一般性场景，
                或明确写「需要我补充」。

                严格按下面的标题输出这八段，不要写第九段及以后：
                一、原文一句话总结
                二、原文结构拆解表
                三、开头、中段和结尾钩子分析
                四、内容、结构、状态、身份、场景分析
                五、原创风险与可借鉴内容
                六、我的内容补充建议
                七、重写方案
                八、3个不同类型的新开头
                """);
        return sb.toString();
    }

    /** 阶段三：基于第五段三类划分与第七段方案写第九段。倒着做一定像复述 */
    public String rewriteStageTwoUser(RewriteInput input, String stageOne) {
        StringBuilder sb = new StringBuilder();
        appendIdentityCard(sb, input);
        sb.append("\n# 原短视频文案（只作为选题参考，不许复述其句子）\n\n")
                .append(input.originalBody()).append("\n\n");
        sb.append("# 已完成的拆解与方案（第一~八段）\n\n").append(stageOne).append("\n\n");
        sb.append("""
                # 第三阶段：原创重写

                根据上面第五段的三类划分和第七段的方案写正文。必须满足：

                1. 不逐句对应原文，不做同义词替换；
                2. 调整观点展开顺序和论证路径，搭我自己的逻辑架构，不复述原作者的思路顺序；
                3. 加入新的信息、解释、案例或视角；
                4. 通篇用「我」和「你」，像和朋友面对面聊天；
                5. 开头前两句必须快速锁定目标人群并制造继续观看的理由，采用第八段三个开头中的一个，不要拼三个；
                6. 每隔约 15~25 秒安排一次中段钩子；
                7. 一句话只讲一件事，拒绝复杂长句和生僻词；
                8. 全程口语化，拒绝书面背诵。
                   反面教材：「人工智能技术的发展趋势表明，掌握相关技能对于未来职业发展至关重要，
                   是提升个人核心竞争力不可或缺的关键要素」。
                   正面教材：「你知道吗？未来的工作，不懂点 AI 真的不行了！
                   这早就不是什么加分项，而是咱们每个人都得掌握的基础技能了」；
                9. 自然加入语气词（对吧？其实啊、你知道吗？），不要每句都加，也不要故意堆网络热词；
                10. 不虚构数据和个人经历；
                11. 与原文选题相关，但在表达、结构和信息上具有明显原创性；
                12. 结尾用自然的软引导，不要突然硬推销；
                13. 第九段只要纯口播，不要分镜、画面、骨架标记。

                写完第九段后必须做朗读测试：出声读一遍，拗口、不顺耳立刻改，直到像平时说话。

                严格按下面的标题输出这三段：
                九、完整原创口播文案
                十、拍摄时的语气、停顿和重音建议
                十一、文案自检评分

                第十一段按 10 分制给这 8 项打分，每项单独一行「维度：N 分」：
                开头吸引力、中段留存能力、内容价值、口语自然度、身份可信度、情绪感染力、原创程度、转化自然度。
                「口语自然度」低于 8 的典型情况：通篇没有「我 / 你」、没有语气词、读着像文章。
                任何一项低于 8 分都要先改文案再输出，不能只改分数。
                第十一段最后单独一行写：朗读测试通过　或　朗读测试不通过。
                """);
        return sb.toString();
    }

    private void appendIdentityCard(StringBuilder sb, RewriteInput input) {
        PersonaSnapshot p = input.persona();
        sb.append("# 我的信息\n\n");
        sb.append("我的行业/赛道：").append(nz(p.model(), "未指定")).append('\n');
        sb.append("我的身份：").append(nz(p.identity(), "未指定")).append('\n');
        sb.append("目标受众：").append(nz(p.audience(), "未指定")).append('\n');
        sb.append("拍摄场景：").append(nz(input.scene(), "坐在办公室对镜头口播")).append('\n');
        sb.append("人物状态：").append(nz(p.tone(), "客观理性、沉稳中肯")).append('\n');
        sb.append("内容目的：").append(nz(input.purpose(), "建立信任")).append('\n');
        sb.append("发布平台：").append(nz(p.platform(), "抖音")).append('\n');
        int words = input.targetWords() > 0 ? input.targetWords() : p.minWordsOrDefault();
        sb.append("期望字数：").append(words).append(" 字以内（这也是上限）\n");
        sb.append("期望时长：约 ").append(Math.max(1, Math.round(words / 300f))).append(" 分钟（按 300 字/分钟）\n");
        sb.append("表达风格：大白话、口语化、节奏快、不要太像讲课\n");
        sb.append("我希望补充的观点或内容：").append(nz(input.extraViews(), "无")).append('\n');

        List<String> banned = new java.util.ArrayList<>(p.bannedOrEmpty());
        if (input.extraBanned() != null) {
            banned.addAll(input.extraBanned());
        }
        sb.append("禁止出现：").append(banned.isEmpty() ? "无" : String.join("、", banned)).append('\n');
        sb.append("\n**原视频标题**：").append(nz(input.originalTitle(), "标题未知")).append('\n');
    }

    /** 洗稿的输入：身份卡 + 原文 + 用户附加要求 */
    public record RewriteInput(
            PersonaSnapshot persona,
            String originalTitle,
            String originalBody,
            String purpose,
            String scene,
            int targetWords,
            String extraViews,
            List<String> extraBanned
    ) {
    }

    /* ==================== 拆解 ==================== */

    public String teardownSystem() {
        return "你是短视频结构拆解专家。核心是拆出对方的思考逻辑，不是抄文案："
                + "看对方怎么论证观点、挖掘我们想不到的角度，产出可原创复用的逻辑框架。"
                + "ASR 会出错，专有名词、数字、岗位名尤其容易错，拿不准的地方标注「此处 ASR 可能有误」，"
                + "不要当成原文事实去分析。只输出 JSON，不要任何解释。";
    }

    public String teardownUser(String url, String pastedText, String videoTitle) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtil.isBlank(pastedText)) {
            sb.append("用户已提供口播原文，直接拆解，不要调用取件与转写工具。\n\n");
            sb.append("【原视频标题】").append(nz(videoTitle, "标题未知")).append('\n');
            sb.append("【口播原文】\n").append(pastedText).append("\n\n");
            // 粘贴来的稿子没有时间戳，但「钩子在第几秒」是拆解的核心指标。
            // 不给折算规则的话模型会把所有 seconds 填 0，中段钩子间隔那一栏就废了。
            sb.append("""
                    这份原文没有时间戳。钩子落在第几秒、两个钩子间隔多少秒是拆解的核心指标，
                    不要因为拿不到时间戳就把 seconds 填 0，按口播语速折算出估算值：
                    - 语速按 300 字/分钟，即每秒 5 字
                    - 某句的 seconds = 该句首字之前的累计字数 ÷ 5，向下取整
                    - durationSec = 全文字数 ÷ 5，words 填全文字数，speechRate 填 300
                    折算值是估算，不必纠结个位数误差，量级对就能支撑「黄金 3 秒说了什么」
                    和「中段钩子间隔是否超过 20 秒」这两个判断。

                    """);
        } else {
            sb.append("请先用 fetch_video 取件，再用 transcribe 转写，然后拆解。\n");
            sb.append("取件失败时不要重试、不要建议第三方解析站，").append("直接把工具返回的录屏指引原样告诉我。\n\n");
            sb.append("【视频链接】").append(url).append("\n\n");
        }
        sb.append("""
                请输出如下结构的 JSON（不要 markdown 代码块包裹）：
                {
                  "videoTitle": "平台上的完整标题，未知写 标题未知",
                  "platform": "douyin | kuaishou | xiaohongshu | wechat_channel | manual",
                  "durationSec": 0,
                  "words": 0,
                  "speechRate": 0,
                  "transcript": "口播稿，一句一行，不要时间戳，ASR 存疑处标注",
                  "needFile": false,
                  "fetchGuide": null,
                  "teardown": {
                    "hook": { "type": "钩子类型", "seconds": 0, "text": "原句", "why": "为什么有效", "weakness": "不足" },
                    "midHooks": [ { "seconds": 0, "text": "原句", "device": "手法" } ],
                    "ending": { "type": "结尾引导方式", "natural": true, "advice": "优化方向" },
                    "skeleton": [ { "seq": 1, "role": "结构作用", "summary": "这一段干了什么" } ],
                    "grid": { "inner": "内圈", "middle": "中圈", "outer": "外圈" },
                    "element": "反推出的爆款元素"
                  },
                  "framework": {
                    "steps": ["骨架第一段的功能，不含具体内容", "..."],
                    "insights": [ { "angle": "对方想到而我们没想到的角度", "why": "为什么有效" } ]
                  }
                }
                取件失败时把 needFile 设为 true、fetchGuide 填工具给的录屏指引，其余字段可为空。
                """);
        return sb.toString();
    }

    /* ---------- 内部工具 ---------- */

    private static String nz(String value, String fallback) {
        return TextUtil.isBlank(value) ? fallback : value;
    }

    private static String loadResource(String path, String fallback) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return fallback;
            }
            try (var in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return fallback;
        }
    }
}
