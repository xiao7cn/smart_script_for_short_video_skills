package com.shanchuang.workflow;

import com.shanchuang.common.util.TextUtil;
import com.shanchuang.harness.Scene;
import com.shanchuang.workflow.model.GateResult;
import com.shanchuang.workflow.model.PersonaSnapshot;
import com.shanchuang.workflow.model.RewriteSections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 洗稿流水线（十一段合同）。
 *
 * 顺序在代码里强制，不给模型选择权：
 * 阶段一二先产出一~八段，阶段三才基于第五段三类划分与第七段方案写第九段。
 * 倒着做（先写正文再补方案）一定像复述原文，这是 Skill 里反复强调的红旗。
 */
public class RewritePipeline {

    private static final Logger log = LoggerFactory.getLogger(RewritePipeline.class);

    private static final int MAX_FIX_ROUNDS = 2;

    /** 段落切分：匹配「一、」到「十一、」的中文序号标题 */
    private static final String[] ORDINALS = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一"};

    private static final Pattern MY_TITLE = Pattern.compile("(?:标题|新标题)\\s*\\d*\\s*[:：]\\s*(.+)");

    private final HarnessPort harness;
    private final PromptBuilder prompts;
    private final QualityGate gate;

    public RewritePipeline(HarnessPort harness, PromptBuilder prompts, QualityGate gate) {
        this.harness = harness;
        this.prompts = prompts;
        this.gate = gate;
    }

    public RewriteSections produce(PromptBuilder.RewriteInput input) {
        PersonaSnapshot persona = input.persona();
        int maxWords = input.targetWords() > 0 ? input.targetWords() : 0;

        // 阶段一 + 二：一~八段。这八段是第九段的设计图
        String stageOne = ScriptPipeline.stripFence(
                harness.run(Scene.SCRIPT_REWRITE, prompts.rewriteSystem(),
                        prompts.rewriteStageOneUser(input)));

        Map<String, String> sections = new LinkedHashMap<>(parseSections(stageOne));
        stageOne = ensureThreeClassSplit(input, stageOne, sections);

        // 阶段三：九~十一段
        String stageTwo = ScriptPipeline.stripFence(
                harness.run(Scene.SCRIPT_REWRITE, prompts.rewriteSystem(),
                        prompts.rewriteStageTwoUser(input, stageOne)));
        sections.putAll(parseSections(stageTwo));

        // 去 AI 味：独立一步，只重写第九段
        String s9 = sections.get("s9");
        if (!TextUtil.isBlank(s9)) {
            String deaiified = ScriptPipeline.stripFence(
                    harness.run(Scene.SCRIPT_DEAI, prompts.deaiSystem(),
                            prompts.deaiUser(s9, persona)));
            if (!TextUtil.isBlank(deaiified)) {
                sections.put("s9", deaiified);
            }
        }

        RewriteSections result = assemble(input, sections);
        GateResult check = gate.checkRewrite(result, input.originalBody(), persona, maxWords);

        // 重合超限 / 低分 / 朗读不通过都只改第九段，不重做前八段
        for (int round = 0; round < MAX_FIX_ROUNDS && !check.passed(); round++) {
            log.debug("洗稿质量门未通过（第 {} 轮）：{}", round + 1, check.report());
            String instruction = check.toRewriteInstruction()
                    + "\n特别注意：与原文连续重合超限时要换论证路径，不是换同义词；"
                    + "自检项低于 8 分要改文案，不是改分数。\n\n以下是需要修订的第九段：\n\n"
                    + sections.get("s9");
            String fixed = ScriptPipeline.stripFence(
                    harness.run(Scene.SCRIPT_REWRITE, prompts.rewriteSystem(), instruction));
            if (TextUtil.isBlank(fixed)) {
                break;
            }
            Map<String, String> patch = parseSections(fixed);
            sections.put("s9", patch.getOrDefault("s9", fixed));
            if (patch.containsKey("s11")) {
                sections.put("s11", patch.get("s11"));
            }
            RewriteSections candidate = assemble(input, sections);
            GateResult after = gate.checkRewrite(candidate, input.originalBody(), persona, maxWords);
            if (after.failures().size() > check.failures().size()) {
                break;
            }
            result = candidate;
            check = after;
        }

        if (!check.passed()) {
            throw new ScriptPipeline.GateFailedException(check);
        }

        return new RewriteSections(result.originalTitle(), result.originalBody(), result.myTitle(),
                result.sections(), result.finalBody(), result.scores(), result.readaloudPass(),
                check.overlapMax(), check.report());
    }

    /**
     * 第五段没做完三类划分就补一次。
     * Skill 把这条列为硬约束：没有三类划分不准写第九段，否则一定是复述。
     */
    private String ensureThreeClassSplit(PromptBuilder.RewriteInput input, String stageOne,
                                         Map<String, String> sections) {
        String s5 = sections.get("s5");
        boolean complete = !TextUtil.isBlank(s5)
                && s5.contains("可以借鉴") && s5.contains("重新论证") && s5.contains("不应该沿用");
        if (complete) {
            return stageOne;
        }

        log.debug("第五段缺三类划分，补一次");
        String patch = ScriptPipeline.stripFence(harness.run(Scene.SCRIPT_REWRITE, prompts.rewriteSystem(),
                """
                        上一稿的第五段没有做完三类划分。请只重出第五段，必须原样包含这三个小标题：
                        可以借鉴的底层逻辑
                        需要重新论证的观点
                        不应该沿用的表达或材料
                        原作者的个人经历、独有数据、标志性金句、连续句式一律归到第三类。

                        原文：
                        """ + input.originalBody()));
        if (!TextUtil.isBlank(patch)) {
            Map<String, String> parsed = parseSections(patch);
            sections.put("s5", parsed.getOrDefault("s5", patch));
            return rebuildStageOne(sections);
        }
        return stageOne;
    }

    private String rebuildStageOne(Map<String, String> sections) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            String content = sections.get(RewriteSections.KEYS[i]);
            if (!TextUtil.isBlank(content)) {
                sb.append(RewriteSections.TITLES[i]).append('\n').append(content.trim()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private RewriteSections assemble(PromptBuilder.RewriteInput input, Map<String, String> sections) {
        String s9 = sections.get("s9");
        String s11 = sections.get("s11");
        return new RewriteSections(
                TextUtil.isBlank(input.originalTitle()) ? "标题未知" : input.originalTitle(),
                input.originalBody(),
                extractMyTitle(sections),
                new LinkedHashMap<>(sections),
                s9 == null ? null : s9.trim(),
                gate.parseScores(s11),
                gate.readaloudPassed(s11),
                0,
                null
        );
    }

    /** 洗稿后的标题从第八段的三个新开头里取不到，就退而用第七段核心观点的首句 */
    private String extractMyTitle(Map<String, String> sections) {
        for (String key : List.of("s8", "s7")) {
            String content = sections.get(key);
            if (TextUtil.isBlank(content)) {
                continue;
            }
            Matcher m = MY_TITLE.matcher(content);
            if (m.find()) {
                return TextUtil.abbreviate(m.group(1).trim(), 60);
            }
        }
        String s9 = sections.get("s9");
        if (!TextUtil.isBlank(s9)) {
            String firstLine = s9.trim().split("\\R")[0];
            return TextUtil.abbreviate(firstLine.trim(), 40);
        }
        return "未命名";
    }

    /**
     * 把模型输出按「一、」～「十一、」切成段。
     *
     * 先定位每个序号标题的位置再按区间切，而不是逐行判断——
     * 表格与正文里也会出现「一、」这类字样，逐行判断会切错。
     */
    Map<String, String> parseSections(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (TextUtil.isBlank(text)) {
            return sections;
        }

        int[] positions = new int[ORDINALS.length];
        for (int i = 0; i < ORDINALS.length; i++) {
            positions[i] = -1;
        }

        // 「十一」必须先找，否则会被「十」的匹配抢走
        for (int i = ORDINALS.length - 1; i >= 0; i--) {
            Pattern p = Pattern.compile("(?m)^\\s*" + ORDINALS[i] + "\\s*[、.．]\\s*");
            Matcher m = p.matcher(text);
            while (m.find()) {
                int start = m.start();
                if (i == 9 && positions[10] >= 0 && start == positions[10]) {
                    continue;   // 「十、」不能命中「十一、」的起点
                }
                positions[i] = start;
                break;
            }
        }

        for (int i = 0; i < ORDINALS.length; i++) {
            if (positions[i] < 0) {
                continue;
            }
            int end = text.length();
            for (int j = 0; j < ORDINALS.length; j++) {
                if (positions[j] > positions[i] && positions[j] < end) {
                    end = positions[j];
                }
            }
            String body = text.substring(positions[i], end).trim();
            // 去掉「九、完整原创口播文案」这一行标题，只留内容
            int firstBreak = body.indexOf('\n');
            String content = firstBreak > 0 ? body.substring(firstBreak + 1).trim() : "";
            sections.put(RewriteSections.KEYS[i], content.isEmpty() ? body : content);
        }
        return sections;
    }
}
