package com.finyorum.dto;

public record CryptoSearchResult(
        String id,
        String symbol,
        String name,
        String thumb,
        Integer marketCapRank
) {
}
