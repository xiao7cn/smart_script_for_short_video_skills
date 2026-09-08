package com.shanchuang.workflow.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 洗稿十一段。
 *
 * 前八段是质量门不是附录：没有第五段的三类划分与第七段的方案，
 * 第九段一定像复述原文。所以段落用有序 Map 存，缺段直接判失败。
 */
public record RewriteSections(
        String originalTitle,
        String originalBody,
        String myTitle,
        Map<String, String> sections,
        String finalBody,
        Map<String, Integer> scores,
        boolean readaloudPass,
        int overlapMax,
        String checkReport
) {

    /** 合同规定的十一段键 */
    public static final String[] KEYS = {"s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11"};

    public static final String[] TITLES = {
            "一、原文一句话总结",
            "二、原文结构拆解表",
            "三、开头、中段和结尾钩子分析",
            "四、内容、结构、状态、身份、场景分析",
            "五、原创风险与可借鉴内容",
            "六、我的内容补充建议",
            "七、重写方案",
            "八、3个不同类型的新开头",
            "九、完整原创口播文案",
            "十、拍摄时的语气、停顿和重音建议",
            "十一、文案自检评分"
    };

    /** 8 项自检维度，任何一项低于 8 都要改文案（不是改分数） */
    public static final String[] SCORE_DIMENSIONS = {
            "开头吸引力", "中段留存能力", "内容价值", "口语自然度",
            "身份可信度", "情绪感染力", "原创程度", "转化自然度"
    };

    public String section(String key) {
        return sections == null ? null : sections.get(key);
    }

    /** 交付文档：原视频标题与原文必须在十一段之前，缺一项不算交付 */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 洗稿：").append(myTitle == null ? "未命名" : myTitle).append("\n\n");
        sb.append("**原视频标题**：").append(originalTitle == null ? "标题未知" : originalTitle).append("\n\n");
        sb.append("### 原视频文案\n\n").append(originalBody == null ? "" : originalBody).append("\n\n---\n\n");
        for (int i = 0; i < KEYS.length; i++) {
            String content = section(KEYS[i]);
            if (content == null || content.isBlank()) {
                continue;
            }
            // 模型通常已经把「一、xxx」写在正文开头了，避免重复写标题
            if (!content.trim().startsWith(TITLES[i].substring(0, 2))) {
                sb.append(TITLES[i]).append('\n');
            }
            sb.append(content.trim()).append("\n\n");
        }
        return sb.toString().trim();
    }

    public static Map<String, String> emptySections() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : KEYS) {
            map.put(key, null);
        }
        return map;
    }
}
