package com.finyorum.dto;

import java.time.Instant;

public record AiAnalysisHistoryItem(
        String recommendation,
        String summary,
        Instant createdAt
) {
}
