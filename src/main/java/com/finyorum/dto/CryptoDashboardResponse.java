package com.finyorum.dto;

public record CryptoDashboardResponse(
        QuoteResponse quote,
        CryptoMarketStats market,
        RiskResponse risk,
        AiAnalysisResponse analysis,
        MarketChartResponse chart,
        java.util.List<CryptoNewsItem> news,
        java.util.List<AiAnalysisHistoryItem> history,
        SignalChangeResponse signalChange
) {
}
