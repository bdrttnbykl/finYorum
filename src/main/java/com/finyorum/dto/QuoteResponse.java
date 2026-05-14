package com.finyorum.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteResponse(
        String symbol,
        BigDecimal currentPrice,
        BigDecimal change,
        BigDecimal percentChange,
        Instant timestamp,
        String source
) {
}
