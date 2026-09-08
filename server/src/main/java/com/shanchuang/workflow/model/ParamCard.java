package com.shanchuang.workflow.model;

import java.util.List;

/**
 * 选题参数卡：一条文案对应一张卡，字段都是抽取后的具体值（不再是数组）。
 * 对应 Skill 步骤 1 的产物。
 */
public record ParamCard(
        int idx,
        String topicType,
        String source,
        String inner,
        String middle,
        String outer,
        String element,
        String scriptType,
        String formula,
        String topicDraft,
        List<String> refs,
        boolean autoSearch
) {

    /** 25 宫格展示串，如 `AI就业 × 薪资 × 毕业生` */
    public String grid() {
        List<String> parts = java.util.stream.Stream.of(inner, middle, outer)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        return parts.isEmpty() ? "AI就业" : String.join(" × ", parts);
    }

    /** 批内去重键：参数组合一样就算重复，10 条里两条讲同一件事是失败的批量 */
    public String dedupKey() {
        return String.join("|", nz(topicType), nz(source), nz(inner), nz(middle),
                nz(outer), nz(element), nz(scriptType));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public boolean isVlog() {
        return "Vlog 叙事".equals(scriptType);
    }

    public boolean fromBenchmark() {
        return "对标爆款拆解".equals(source);
    }
}
