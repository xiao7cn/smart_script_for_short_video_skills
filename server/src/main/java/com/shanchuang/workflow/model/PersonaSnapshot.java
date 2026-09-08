package com.shanchuang.workflow.model;

import java.util.List;

/**
 * 人设快照。
 *
 * 任务创建时冻结当时的人设：人设随时会改，事后要能复现同一批结果。
 * 六要素决定文案的一切表达边界，禁用词是合规红线。
 */
public record PersonaSnapshot(
        String model,
        String modelDesc,
        String identity,
        String value,
        String tone,
        String audience,
        List<String> needs,
        List<String> banned,
        int minWords,
        String ctaStyle,
        String ctaAsset,
        String platform
) {

    public static PersonaSnapshot systemDefault() {
        return new PersonaSnapshot(
                "靠谱顾问",
                "理性沉稳不吹嘘，持续输出专业就职、职场干货，以及亲身实战 Vlog。",
                "20 年大厂 IT/AI 从业者，涉猎财务、企业 ERP、互联网、金融行业相关 IT 技术。",
                "致力于为 IT/AI 从业者提供专业的成长规划、能力评估、技能调整与就业帮扶服务。",
                "客观理性、沉稳中肯、成长励志。",
                "20-35 岁年轻求职者、职场困惑者，以及年轻人的家长。",
                List.of(
                        "不了解学什么才能赚到更多的钱",
                        "不了解目前什么行业吃香、有发展前景",
                        "不了解什么行业适合自己",
                        "不了解子女的专业有没有就业前景",
                        "岗位正在被 AI 取代，裁员焦虑",
                        "不知道子女做什么行业才体面"
                ),
                List.of("保证就业", "包分配", "稳赚", "零风险", "内部名额"),
                500,
                "评论区留关键词领资料",
                "路线图",
                "抖音"
        );
    }

    public List<String> bannedOrEmpty() {
        return banned == null ? List.of() : banned;
    }

    public int minWordsOrDefault() {
        return minWords > 0 ? minWords : 500;
    }
}
