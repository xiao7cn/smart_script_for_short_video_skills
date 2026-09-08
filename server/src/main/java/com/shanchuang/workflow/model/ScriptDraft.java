package com.shanchuang.workflow.model;

import com.shanchuang.common.util.TextUtil;

/**
 * 一条文案的产出。
 *
 * draftBody 是去 AI 味之前的初稿，刻意留着：一是去味后跌破字数下限时要能比对，
 * 二是排查「去味把事实改坏了」这类问题时没有初稿就无从下手。
 */
public record ScriptDraft(
        ParamCard card,
        String topic,
        String title,
        String draftBody,
        String body,
        String needsMaterial
) {

    public int words() {
        return TextUtil.cnWords(body);
    }

    public String structure() {
        return card.formula();
    }
}
