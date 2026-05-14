package com.finyorum.dto;

public record CryptoDashboardResponse(
        QuoteResponse quote,
        CryptoMarketStats market,
        RiskResponse risk,
        AiAnalysisResponse analysis,
        MarketChartResponse chart
) {
}
