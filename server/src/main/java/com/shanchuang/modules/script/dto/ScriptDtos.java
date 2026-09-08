package com.shanchuang.modules.script.dto;

import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.modules.script.entity.Script;
import com.shanchuang.modules.script.entity.ScriptBreakdown;
import com.shanchuang.workflow.model.WizardSel;

import java.time.LocalDateTime;
import java.util.List;

/** 文案模块的请求与响应体，字段名与 docs/接口设计.md 第 6、7 章一致 */
public final class ScriptDtos {

    private ScriptDtos() {
    }

    public record GenerateRequest(WizardSel sel, Integer count, String promptOverride) {
    }

    public record GenerateResponse(
            String taskNo, int accepted, int requested,
            int creditsHold, int creditsBalance, String message
    ) {
    }

    /** 列表项：刻意不含 body，大字段只在详情页取，避免拖慢分页 */
    public record ScriptListItem(
            Long id, Integer seqNo, String title, String topic,
            String scriptType, String topicType, String source,
            String grid, String element, Integer words,
            Boolean generated, Boolean hasBreakdown, LocalDateTime createdAt
    ) {
        public static ScriptListItem of(Script s, boolean hasBreakdown) {
            return new ScriptListItem(s.getId(), s.getSeqNo(), s.getTitle(), s.getTopic(),
                    s.getScriptType(), s.getTopicType(), s.getSource(), s.getGrid(), s.getElement(),
                    s.getWords(), s.getGenerated() == null || s.getGenerated() == 1,
                    hasBreakdown, s.getCreatedAt());
        }
    }

    public record BreakdownPoint(String label, String detail) {
    }

    public record BreakdownVO(
            List<String> refs, Boolean autoSearch, String original,
            List<BreakdownPoint> points, String rewrite
    ) {
        public static BreakdownVO of(ScriptBreakdown b) {
            List<BreakdownPoint> points = JsonUtil.fromJson(b.getPointsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<BreakdownPoint>>() {
                    });
            return new BreakdownVO(
                    JsonUtil.toStringList(b.getRefsJson()),
                    b.getAutoSearch() != null && b.getAutoSearch() == 1,
                    b.getOriginal(),
                    points == null ? List.of() : points,
                    b.getRewriteNote());
        }
    }

    public record ScriptDetail(
            Long id, Integer seqNo, String title, String topic,
            String scriptType, String topicType, String source,
            String grid, String element, String structure,
            Integer words, Boolean generated, String needsMaterial,
            String body, LocalDateTime createdAt, BreakdownVO breakdown
    ) {
        public static ScriptDetail of(Script s, ScriptBreakdown b) {
            return new ScriptDetail(s.getId(), s.getSeqNo(), s.getTitle(), s.getTopic(),
                    s.getScriptType(), s.getTopicType(), s.getSource(), s.getGrid(), s.getElement(),
                    s.getStructure(), s.getWords(),
                    s.getGenerated() == null || s.getGenerated() == 1,
                    s.getNeedsMaterial(), s.getBody(), s.getCreatedAt(),
                    b == null ? null : BreakdownVO.of(b));
        }
    }

    public record PromptPreviewRequest(WizardSel sel) {
    }

    public record PromptPreviewResponse(String prompt, String deaiPrompt) {
    }
}
