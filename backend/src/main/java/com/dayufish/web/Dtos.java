package com.dayufish.web;

import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record Parts(double similarity, double recency, double strength, int ageDays) {
    }

    public record MemoryView(
            Long id,
            Long spaceId,
            String source,
            String occurredAt,
            String question,
            String answer,
            String summary,
            List<String> keywords,
            int importance,
            String status,
            String error,
            int accessCount,
            String lastAccessAt,
            Double score,
            Parts parts,
            Double strength,
            Integer ageDays,
            String embedModel) {
    }

    public record SpaceView(Long id, String name, String note, String createdAt, long total, long pending) {
    }

    public record BundleView(
            String text,
            int count,
            int chars,
            List<Long> ids,
            boolean truncated,
            boolean filtered,
            int estimatedTokens) {
    }

    public record ImportProgress(
            Long id,
            Long spaceId,
            String sourceName,
            int total,
            int ready,
            int failed,
            int done,
            String status,
            String createdAt) {
    }
}
