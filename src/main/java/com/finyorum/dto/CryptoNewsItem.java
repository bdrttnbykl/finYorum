package com.finyorum.dto;

import java.time.Instant;

public record CryptoNewsItem(
        String title,
        String source,
        String link,
        Instant publishedAt
) {
}
