package com.shanchuang.workflow.model;

import java.util.List;

/**
 * 向导参数。
 *
 * 每个字段都是数组：原型里所有选项都支持多选，多选时按批次轮流出稿。
 * 字段为空或缺失表示「用户跳过了这一步」，生成时由 ParamPicker 从完整候选池随机补齐。
 */
public record WizardSel(
        List<String> topicType,
        List<String> source,
        List<String> inner,
        List<String> middle,
        List<String> outer,
        List<String> element,
        List<String> scriptType,
        String topicDraft,
        List<String> refs,
        Boolean autoSearch
) {

    public static WizardSel empty() {
        return new WizardSel(null, null, null, null, null, null, null, null, null, null);
    }

    public List<String> refsOrEmpty() {
        if (refs == null) {
            return List.of();
        }
        return refs.stream().filter(r -> r != null && !r.isBlank()).map(String::trim).toList();
    }

    public boolean autoSearchOn() {
        return Boolean.TRUE.equals(autoSearch);
    }

    /** 选到对标类来源时，向导会插入「对标视频」步骤，生成时也要带上拆解语境 */
    public boolean needsRefs(List<String> refSources) {
        if (source == null) {
            return false;
        }
        return source.stream().anyMatch(refSources::contains);
    }
}
