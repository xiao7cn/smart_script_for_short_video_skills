package com.shanchuang.modules.rewrite.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.modules.rewrite.entity.Rewrite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class RewriteDtos {

    private RewriteDtos() {
    }

    /** teardownId 与 originalBody 至少给一个：直接粘原文时不需要先拆解 */
    public record SubmitRequest(
            Long teardownId,
            String originalTitle,
            String originalBody,
            String purpose,
            String scene,
            Integer targetWords,
            String extraViews,
            List<String> extraBanned
    ) {
    }

    public record SubmitResponse(String taskNo, Long rewriteId) {
    }

    public record RewriteVO(
            Long id, Long teardownId, Long scriptId,
            String originalTitle, String originalBody, String myTitle,
            Map<String, String> sections, String finalBody, Integer words,
            Map<String, Integer> scores, Boolean readaloudPass, Integer overlapMax,
            String checkReport, String status, LocalDateTime createdAt
    ) {
        public static RewriteVO of(Rewrite r) {
            Map<String, String> sections = JsonUtil.fromJson(r.getSectionsJson(),
                    new TypeReference<Map<String, String>>() {
                    });
            Map<String, Integer> scores = JsonUtil.fromJson(r.getScoreJson(),
                    new TypeReference<Map<String, Integer>>() {
                    });
            return new RewriteVO(r.getId(), r.getTeardownId(), r.getScriptId(),
                    r.getOriginalTitle(), r.getOriginalBody(), r.getMyTitle(),
                    sections == null ? Map.of() : sections, r.getFinalBody(), r.getWords(),
                    scores == null ? Map.of() : scores,
                    r.getReadaloudPass() != null && r.getReadaloudPass() == 1,
                    r.getOverlapMax(), r.getCheckReport(), r.getStatus(), r.getCreatedAt());
        }
    }

    public record RewriteListItem(
            Long id, String originalTitle, String myTitle, Integer words,
            String status, LocalDateTime createdAt
    ) {
        public static RewriteListItem of(Rewrite r) {
            return new RewriteListItem(r.getId(), r.getOriginalTitle(), r.getMyTitle(),
                    r.getWords(), r.getStatus(), r.getCreatedAt());
        }
    }

    public record PublishResponse(Long scriptId) {
    }
}
