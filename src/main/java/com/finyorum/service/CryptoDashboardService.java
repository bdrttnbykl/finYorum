package com.finyorum.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finyorum.domain.CryptoMarketSnapshot;
import com.finyorum.dto.AiAnalysisResponse;
import com.finyorum.dto.CryptoDashboardResponse;
import com.finyorum.dto.CryptoMarketStats;
import com.finyorum.dto.CryptoNewsItem;
import com.finyorum.dto.MarketChartPoint;
import com.finyorum.dto.MarketChartResponse;
import com.finyorum.dto.QuoteResponse;
import com.finyorum.dto.RiskResponse;
import com.finyorum.repository.CryptoMarketSnapshotRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class CryptoDashboardService {

    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<MarketChartPoint>> CHART_POINTS =
            new TypeReference<>() {
            };

    private final CryptoService cryptoService;
    private final RiskService riskService;
    private final AiAnalysisService aiAnalysisService;
    private final CryptoNewsService cryptoNewsService;
    private final CryptoMarketSnapshotRepository snapshots;
    private final ObjectMapper objectMapper;

    public CryptoDashboardService(CryptoService cryptoService,
                                  RiskService riskService,
                                  AiAnalysisService aiAnalysisService,
                                  CryptoNewsService cryptoNewsService,
                                  CryptoMarketSnapshotRepository snapshots,
                                  ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.riskService = riskService;
        this.aiAnalysisService = aiAnalysisService;
        this.cryptoNewsService = cryptoNewsService;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
    }

    public synchronized CryptoDashboardResponse dashboard(String asset, int days) {
        String symbol = normalize(asset);
        int normalizedDays = Math.max(1, Math.min(days, 90));

        return snapshots.findTopBySymbolOrderByFetchedAtDesc(symbol)
                .filter(this::isFresh)
                .map(snapshot -> fromSnapshot(snapshot, normalizedDays))
                .orElseGet(() -> createSnapshotDashboard(asset, normalizedDays));
    }

    private boolean isFresh(CryptoMarketSnapshot snapshot) {
        return !snapshot.getFetchedAt().isBefore(Instant.now().minus(SNAPSHOT_TTL));
    }

    private CryptoDashboardResponse createSnapshotDashboard(String asset, int days) {
        CryptoMarketStats market = cryptoService.marketStats(asset);
        QuoteResponse quote = new QuoteResponse(
                market.symbol(),
                market.currentPrice(),
                market.priceChange24h(),
                market.priceChangePercentage24h(),
                Instant.now(),
                market.source()
        );
        MarketChartResponse chart = cryptoService.marketChart(asset, days);
        RiskResponse risk = riskService.calculateFromPrices(
                market.symbol(),
                chart.prices().stream().map(MarketChartPoint::price).toList()
        );

        CryptoMarketSnapshot snapshot = snapshots.save(toSnapshot(market, chart, risk));
        List<CryptoNewsItem> news = cryptoNewsService.recentNews(market);
        AiAnalysisResponse analysis = aiAnalysisService.analyzeForSnapshot(snapshot.getId(), quote, market, risk, chart, news);
        return new CryptoDashboardResponse(quote, market, risk, analysis, chart);
    }

    private CryptoDashboardResponse fromSnapshot(CryptoMarketSnapshot snapshot, int days) {
        List<MarketChartPoint> points = readChart(snapshot);
        QuoteResponse quote = new QuoteResponse(
                snapshot.getSymbol(),
                snapshot.getCurrentPrice(),
                snapshot.getPriceChange24h(),
                snapshot.getPriceChangePercentage24h(),
                snapshot.getFetchedAt(),
                "database-cache"
        );
        CryptoMarketStats market = new CryptoMarketStats(
                snapshot.getCoinId(),
                snapshot.getSymbol(),
                snapshot.getName(),
                "",
                snapshot.getMarketCapRank(),
                snapshot.getCurrentPrice(),
                snapshot.getPriceChange24h(),
                snapshot.getPriceChangePercentage24h(),
                snapshot.getLow24h(),
                snapshot.getHigh24h(),
                snapshot.getMarketCap(),
                snapshot.getFullyDilutedValuation(),
                snapshot.getTotalVolume(),
                snapshot.getCirculatingSupply(),
                snapshot.getTotalSupply(),
                snapshot.getMaxSupply(),
                "database-cache"
        );
        RiskResponse risk = new RiskResponse(
                snapshot.getSymbol(),
                snapshot.getVolatility(),
                snapshot.getSharpeRatio(),
                snapshot.getRiskLevel()
        );
        MarketChartResponse chart = new MarketChartResponse(snapshot.getSymbol(), days, "database-cache", points);
        List<CryptoNewsItem> news = cryptoNewsService.recentNews(market);
        AiAnalysisResponse analysis = aiAnalysisService.analyzeForSnapshot(snapshot.getId(), quote, market, risk, chart, news);
        return new CryptoDashboardResponse(quote, market, risk, analysis, chart);
    }

    private CryptoMarketSnapshot toSnapshot(CryptoMarketStats market,
                                            MarketChartResponse chart,
                                            RiskResponse risk) {
        return new CryptoMarketSnapshot(
                market.symbol(),
                market.id(),
                market.name(),
                market.marketCapRank(),
                value(market.currentPrice()),
                value(market.priceChange24h()),
                value(market.priceChangePercentage24h()),
                value(market.low24h()),
                value(market.high24h()),
                value(market.marketCap()),
                value(market.fullyDilutedValuation()),
                value(market.totalVolume()),
                value(market.circulatingSupply()),
                value(market.totalSupply()),
                value(market.maxSupply()),
                writeChart(chart.prices()),
                value(risk.volatility()),
                value(risk.sharpeRatio()),
                risk.riskLevel(),
                Instant.now()
        );
    }

    private String writeChart(List<MarketChartPoint> prices) {
        try {
            return objectMapper.writeValueAsString(prices);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Chart data could not be serialized", exception);
        }
    }

    private List<MarketChartPoint> readChart(CryptoMarketSnapshot snapshot) {
        try {
            return objectMapper.readValue(snapshot.getChartJson(), CHART_POINTS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Chart data could not be read for snapshot " + snapshot.getId(), exception);
        }
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalize(String asset) {
        return asset.trim().toUpperCase(Locale.ROOT);
    }
}
