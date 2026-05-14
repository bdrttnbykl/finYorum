package com.finyorum.dto;

import java.math.BigDecimal;

public record CryptoMarketStats(
        String id,
        String symbol,
        String name,
        String image,
        Integer marketCapRank,
        BigDecimal currentPrice,
        BigDecimal priceChange24h,
        BigDecimal priceChangePercentage24h,
        BigDecimal low24h,
        BigDecimal high24h,
        BigDecimal marketCap,
        BigDecimal fullyDilutedValuation,
        BigDecimal totalVolume,
        BigDecimal circulatingSupply,
        BigDecimal totalSupply,
        BigDecimal maxSupply,
        String source
) {
}
