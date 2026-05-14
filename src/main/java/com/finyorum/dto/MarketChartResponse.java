package com.finyorum.dto;

import java.util.List;

public record MarketChartResponse(
        String symbol,
        int days,
        String source,
        List<MarketChartPoint> prices
) {
}
