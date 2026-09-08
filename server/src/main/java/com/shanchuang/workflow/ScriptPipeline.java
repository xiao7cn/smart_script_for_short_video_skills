package com.shanchuang.workflow;

import com.shanchuang.common.util.TextUtil;
import com.shanchuang.harness.Scene;
import com.shanchuang.workflow.model.GateResult;
import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.ParamCard;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.ScriptDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单条文案的生产流水线。
 *
 * 对应 Skill 的步骤 2→3→4→5→6：配对话题方向 → 套元素成选题 → 标题 → 正文 → 去 AI 味 → 自检。
 *
 * 这里有一条不能动的约束：写作与去 AI 味必须是两次独立的模型调用。
 * 同一次生成里既写又改，模型会保留自己的表达习惯，去味等于没做。
 */
public class ScriptPipeline {

    private static final Logger log = LoggerFactory.getLogger(ScriptPipeline.class);

    /** 质量门失败后的定向重写次数。再多就是浪费额度，不如判失败退钱 */
    private static final int MAX_FIX_ROUNDS = 1;

    private static final Pattern TOPIC_LINE = Pattern.compile("选题\\s*[:：]\\s*(.+)");
    private static final Pattern TITLE_LINE = Pattern.compile("标题\\s*\\d*\\s*[:：]\\s*(.+)");

    private final HarnessPort harness;
    private final PromptBuilder prompts;
    private final QualityGate gate;

    public ScriptPipeline(HarnessPort harness, PromptBuilder prompts, QualityGate gate) {
        this.harness = harness;
        this.prompts = prompts;
        this.gate = gate;
    }

    public ScriptDraft produce(ParamCard card, PersonaSnapshot persona, OptionCatalog catalog) {
        return produce(card, persona, catalog, null);
    }

    /**
     * @param promptOverride 用户在向导里手改过的提示词。有值时替换正文那一步的 user 段，
     *                       其余步骤（选题标题、去味）仍按标准流程走
     */
    public ScriptDraft produce(ParamCard card, PersonaSnapshot persona, OptionCatalog catalog,
                               String promptOverride) {
        // 步骤 2-3：选题与标题
        TopicTitle topicTitle = resolveTopicTitle(card, persona, catalog);

        // 步骤 4：写正文
        String bodyUser = TextUtil.isBlank(promptOverride)
                ? prompts.bodyUser(card, persona, topicTitle.topic(), topicTitle.title())
                : promptOverride;
        String draft = harness.run(Scene.SCRIPT_GENERATE, prompts.bodySystem(persona), bodyUser);
        draft = stripFence(draft);

        // 步骤 5：去 AI 味。独立一次调用，不与写作合并
        String body = stripFence(harness.run(Scene.SCRIPT_DEAI, prompts.deaiSystem(),
                prompts.deaiUser(draft, persona)));

        // 步骤 6：确定性自检 + 定向重写
        GateResult result = gate.checkScript(body, persona, 0);
        for (int round = 0; round < MAX_FIX_ROUNDS && !result.clean(); round++) {
            log.debug("card#{} 质量门未通过，定向重写：{}", card.idx(), result.report());
            String fixed = stripFence(harness.run(Scene.SCRIPT_DEAI, prompts.deaiSystem(),
                    result.toRewriteInstruction() + "\n\n以下是需要修订的正文：\n\n" + body));
            GateResult after = gate.checkScript(fixed, persona, 0);
            // 修订稿更差就不要，保留原稿再判
            if (after.failures().size() <= result.failures().size()) {
                body = fixed;
                result = after;
            } else {
                break;
            }
        }

        if (!result.passed()) {
            throw new GateFailedException(result);
        }

        return new ScriptDraft(card, topicTitle.topic(), topicTitle.title(), draft, body,
                card.isVlog()
                        ? "Vlog 三个片段的身份、专业、对话细节，请换成你真实接待过的人。结构可照用，具体信息不要沿用。"
                        : null);
    }

    private TopicTitle resolveTopicTitle(ParamCard card, PersonaSnapshot persona, OptionCatalog catalog) {
        String raw = harness.run(Scene.TOPIC_TITLE, prompts.topicTitleSystem(),
                prompts.topicTitleUser(card, persona, catalog));
        return parseTopicTitle(raw, card);
    }

    /**
     * 解析选题与标题。
     *
     * 模型的格式偶尔会飘（少了「选题：」前缀、标题不编号），所以逐行兜：
     * 抓不到就用参数卡兜一个可用的，不能让整条因为一个格式问题失败。
     */
    TopicTitle parseTopicTitle(String raw, ParamCard card) {
        String topic = null;
        String title = null;

        if (raw != null) {
            for (String line : raw.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (topic == null) {
                    Matcher m = TOPIC_LINE.matcher(trimmed);
                    if (m.find()) {
                        topic = m.group(1).trim();
                        continue;
                    }
                }
                if (title == null) {
                    Matcher m = TITLE_LINE.matcher(trimmed);
                    if (m.find()) {
                        title = m.group(1).trim();
                    }
                }
            }
        }

        if (TextUtil.isBlank(topic)) {
            topic = TextUtil.isBlank(card.topicDraft())
                    ? card.grid() + " 这块，最该先搞清楚的一件事"
                    : card.topicDraft().trim();
        }
        if (TextUtil.isBlank(title)) {
            title = topic;
        }
        return new TopicTitle(trimQuotes(topic), trimQuotes(title));
    }

    /** 模型爱把正文包在 ``` 里，入库前剥掉 */
    static String stripFence(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstBreak = t.indexOf('\n');
            if (firstBreak > 0) {
                t = t.substring(firstBreak + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }

    private static String trimQuotes(String s) {
        String t = s.trim();
        if (t.length() > 1 && (t.startsWith("「") && t.endsWith("」") || t.startsWith("\"") && t.endsWith("\""))) {
            return t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    public record TopicTitle(String topic, String title) {
    }

    /** 质量门最终未通过。上层据此把该条标失败并退额度 */
    public static class GateFailedException extends RuntimeException {
        private final GateResult result;

        public GateFailedException(GateResult result) {
            super(String.join("；", result.failures()));
            this.result = result;
        }

        public GateResult getResult() {
            return result;
        }

        public List<String> failures() {
            return result.failures();
        }
    }
}
