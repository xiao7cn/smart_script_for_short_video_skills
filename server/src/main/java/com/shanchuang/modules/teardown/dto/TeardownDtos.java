package com.shanchuang.modules.teardown.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.modules.teardown.entity.Teardown;

import java.time.LocalDateTime;

public final class TeardownDtos {

    private TeardownDtos() {
    }

    /** url / fileId / text 三种输入互斥，至少给一种 */
    public record SubmitRequest(String url, String browser, String fileId, String text, String videoTitle) {
    }

    public record SubmitResponse(String taskNo, Long teardownId) {
    }

    public record TeardownVO(
            Long id, String platform, String sourceUrl, String videoTitle,
            Integer durationSec, Integer words, Integer speechRate, String asrBackend,
            String transcript, JsonNode teardown, JsonNode framework,
            String status, String fetchGuide, LocalDateTime createdAt
    ) {
        public static TeardownVO of(Teardown t) {
            return new TeardownVO(t.getId(), t.getPlatform(), t.getSourceUrl(), t.getVideoTitle(),
                    t.getDurationSec(), t.getWords(), t.getSpeechRate(), t.getAsrBackend(),
                    t.getTranscript(), tree(t.getTeardownJson()), tree(t.getFrameworkJson()),
                    t.getStatus(), t.getFetchGuide(), t.getCreatedAt());
        }

        private static JsonNode tree(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                return JsonUtil.mapper().readTree(json);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** 列表不返回 transcript 与拆解 JSON，避免拖慢分页 */
    public record TeardownListItem(
            Long id, String platform, String videoTitle, Integer durationSec,
            Integer words, String status, LocalDateTime createdAt
    ) {
        public static TeardownListItem of(Teardown t) {
            return new TeardownListItem(t.getId(), t.getPlatform(), t.getVideoTitle(),
                    t.getDurationSec(), t.getWords(), t.getStatus(), t.getCreatedAt());
        }
    }

    public record MaterialRequest(String fileId) {
    }
}
