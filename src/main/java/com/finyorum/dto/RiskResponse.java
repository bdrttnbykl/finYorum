package com.finyorum.dto;

import java.math.BigDecimal;

public record RiskResponse(
        String symbol,
        BigDecimal volatility,
        BigDecimal sharpeRatio,
        String riskLevel
) {
}
