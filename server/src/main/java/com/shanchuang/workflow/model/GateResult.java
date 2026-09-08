package com.shanchuang.workflow.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 质量门结果。
 *
 * failures 是硬伤（字数、禁用词、重合、缺章节、低分），必须打回重写；
 * warnings 是软伤（缺语气词、有书面腔），也会拼进重写要求，但不单独判失败。
 * 这套区分照搬 check.py 的语义。
 */
public record GateResult(List<String> failures, List<String> warnings, int words, int overlapMax) {

    public boolean passed() {
        return failures.isEmpty();
    }

    public boolean clean() {
        return failures.isEmpty() && warnings.isEmpty();
    }

    /** 把所有问题拼成给模型的定向重写要求 */
    public String toRewriteInstruction() {
        List<String> all = new ArrayList<>(failures);
        all.addAll(warnings);
        StringBuilder sb = new StringBuilder("上一稿存在以下问题，请针对性重写，不要重新构思选题：\n");
        for (int i = 0; i < all.size(); i++) {
            sb.append(i + 1).append(". ").append(all.get(i)).append('\n');
        }
        return sb.toString();
    }

    public String report() {
        if (clean()) {
            return "通过　口播 " + words + " 字\nOK　字数、禁用词、重合、口语标记均通过";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(passed() ? "通过（有警告）" : "打回").append("　口播 ").append(words).append(" 字\n");
        failures.forEach(f -> sb.append("FAIL　").append(f).append('\n'));
        warnings.forEach(w -> sb.append("WARN　").append(w).append('\n'));
        return sb.toString().trim();
    }

    public static class Builder {
        private final List<String> failures = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private int words;
        private int overlapMax;

        public Builder fail(String message) {
            failures.add(message);
            return this;
        }

        public Builder warn(String message) {
            warnings.add(message);
            return this;
        }

        public Builder words(int w) {
            this.words = w;
            return this;
        }

        public Builder overlapMax(int o) {
            this.overlapMax = o;
            return this;
        }

        public GateResult build() {
            return new GateResult(List.copyOf(failures), List.copyOf(warnings), words, overlapMax);
        }
    }
}
