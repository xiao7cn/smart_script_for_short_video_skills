package com.shanchuang.modules.persona.dto;

import java.util.List;

/** 字段名与 docs/接口设计.md 4.1 完全一致，PUT 与 GET 共用一个形状 */
public record PersonaVO(
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
}
