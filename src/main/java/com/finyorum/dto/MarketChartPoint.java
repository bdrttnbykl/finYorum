package com.finyorum.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketChartPoint(
        Instant timestamp,
        BigDecimal price
) {
}
