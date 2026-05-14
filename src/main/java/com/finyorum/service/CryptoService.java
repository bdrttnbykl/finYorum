package com.finyorum.service;

import com.finyorum.dto.QuoteResponse;
import com.finyorum.dto.CryptoSearchResult;
import com.finyorum.dto.CryptoMarketStats;
import com.finyorum.dto.MarketChartPoint;
import com.finyorum.dto.MarketChartResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class CryptoService {

    private static final Map<String, String> COIN_IDS = Map.ofEntries(
            Map.entry("BTC", "bitcoin"),
            Map.entry("BITCOIN", "bitcoin"),
            Map.entry("ETH", "ethereum"),
            Map.entry("ETHEREUM", "ethereum"),
            Map.entry("SOL", "solana"),
            Map.entry("SOLANA", "solana"),
            Map.entry("BNB", "binancecoin"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("XRP", "ripple"),
            Map.entry("ADA", "cardano"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("LINK", "chainlink"),
            Map.entry("DOT", "polkadot"),
            Map.entry("MATIC", "matic-network"),
            Map.entry("POL", "polygon-ecosystem-token"),
            Map.entry("TRX", "tron"),
            Map.entry("TON", "the-open-network"),
            Map.entry("LTC", "litecoin"),
            Map.entry("BCH", "bitcoin-cash"),
            Map.entry("UNI", "uniswap"),
            Map.entry("NEAR", "near"),
            Map.entry("APT", "aptos"),
            Map.entry("ARB", "arbitrum"),
            Map.entry("OP", "optimism"),
            Map.entry("ATOM", "cosmos"),
            Map.entry("ICP", "internet-computer"),
            Map.entry("FIL", "filecoin"),
            Map.entry("ETC", "ethereum-classic"),
            Map.entry("HBAR", "hedera-hashgraph"),
            Map.entry("KAS", "kaspa"),
            Map.entry("INJ", "injective-protocol"),
            Map.entry("RENDER", "render-token"),
            Map.entry("RNDR", "render-token"),
            Map.entry("SUI", "sui"),
            Map.entry("SEI", "sei-network"),
            Map.entry("WLD", "worldcoin-wld"),
            Map.entry("PEPE", "pepe"),
            Map.entry("SHIB", "shiba-inu"),
            Map.entry("FLOKI", "floki"),
            Map.entry("AAVE", "aave"),
            Map.entry("CRV", "curve-dao-token"),
            Map.entry("MKR", "maker"),
            Map.entry("LDO", "lido-dao"),
            Map.entry("ONDO", "ondo-finance"),
            Map.entry("PENDLE", "pendle"),
            Map.entry("SNX", "havven"),
            Map.entry("TAO", "bittensor"),
            Map.entry("TIA", "celestia"),
            Map.entry("JUP", "jupiter-exchange-solana"),
            Map.entry("JST", "just"),
            Map.entry("PYTH", "pyth-network"),
            Map.entry("BONK", "bonk"),
            Map.entry("GRT", "the-graph"),
            Map.entry("ALGO", "algorand"),
            Map.entry("VET", "vechain"),
            Map.entry("XLM", "stellar"),
            Map.entry("XMR", "monero"),
            Map.entry("MINA", "mina-protocol")
    );

    private final WebClient webClient;
    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedMarketStats> marketCache = new ConcurrentHashMap<>();
    private final Map<String, CachedChart> chartCache = new ConcurrentHashMap<>();
    private final Map<String, List<CryptoSearchResult>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, String> resolvedCoinIds = new ConcurrentHashMap<>();

    public CryptoService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.coingecko.com/api/v3").build();
    }

    public synchronized QuoteResponse quote(String asset) {
        String normalized = normalize(asset);
        String coinId = coinId(normalized);
        CachedQuote cached = cache.get(coinId);
        if (cached != null && cached.isFresh()) {
            return cached.quote();
        }

        try {
            Map response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/simple/price")
                            .queryParam("ids", coinId)
                            .queryParam("vs_currencies", "usd")
                            .queryParam("include_24hr_change", "true")
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            Map coin = response == null ? null : (Map) response.get(coinId);
            if (coin == null || coin.get("usd") == null) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "CoinGecko did not return price data for " + normalized);
            }

            BigDecimal current = decimal(coin.get("usd"));
            BigDecimal percent = decimal(coin.get("usd_24h_change")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal change = current.multiply(percent)
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            QuoteResponse quote = new QuoteResponse(
                    normalized,
                    current,
                    change,
                    percent,
                    Instant.now(),
                    "coingecko"
            );
            cache.put(coinId, new CachedQuote(quote, Instant.now()));
            return quote;
        } catch (RuntimeException exception) {
            if (cached != null) {
                return new QuoteResponse(
                        cached.quote().symbol(),
                        cached.quote().currentPrice(),
                        cached.quote().change(),
                        cached.quote().percentChange(),
                        cached.quote().timestamp(),
                        "coingecko-cache"
                );
            }
            return fallbackQuote(normalized);
        }
    }

    public synchronized CryptoMarketStats marketStats(String asset) {
        String normalized = normalize(asset);
        String coinId = coinId(normalized);
        CachedMarketStats cached = marketCache.get(coinId);
        if (cached != null && cached.isFresh()) {
            return cached.stats();
        }

        try {
            List response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/coins/markets")
                            .queryParam("vs_currency", "usd")
                            .queryParam("ids", coinId)
                            .queryParam("price_change_percentage", "24h")
                            .queryParam("precision", "full")
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            Map market = response == null || response.isEmpty() ? null : (Map) response.get(0);
            if (market == null) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "CoinGecko did not return market data for " + normalized);
            }

            CryptoMarketStats stats = new CryptoMarketStats(
                    string(market.get("id")),
                    normalized,
                    string(market.get("name")),
                    string(market.get("image")),
                    integer(market.get("market_cap_rank")),
                    decimal(market.get("current_price")),
                    decimal(market.get("price_change_24h")),
                    decimal(market.get("price_change_percentage_24h")),
                    decimal(market.get("low_24h")),
                    decimal(market.get("high_24h")),
                    decimal(market.get("market_cap")),
                    decimal(market.get("fully_diluted_valuation")),
                    decimal(market.get("total_volume")),
                    decimal(market.get("circulating_supply")),
                    decimal(market.get("total_supply")),
                    decimal(market.get("max_supply")),
                    "coingecko"
            );
            marketCache.put(coinId, new CachedMarketStats(stats, Instant.now()));
            return stats;
        } catch (RuntimeException exception) {
            if (cached != null) {
                CryptoMarketStats stats = cached.stats();
                return new CryptoMarketStats(
                        stats.id(),
                        stats.symbol(),
                        stats.name(),
                        stats.image(),
                        stats.marketCapRank(),
                        stats.currentPrice(),
                        stats.priceChange24h(),
                        stats.priceChangePercentage24h(),
                        stats.low24h(),
                        stats.high24h(),
                        stats.marketCap(),
                        stats.fullyDilutedValuation(),
                        stats.totalVolume(),
                        stats.circulatingSupply(),
                        stats.totalSupply(),
                        stats.maxSupply(),
                        "coingecko-cache"
                );
            }
            return fallbackMarketStats(normalized, coinId);
        }
    }

    public synchronized MarketChartResponse marketChart(String asset, int days) {
        String normalized = normalize(asset);
        String coinId = coinId(normalized);
        int normalizedDays = Math.max(1, Math.min(days, 90));
        String cacheKey = coinId + ":" + normalizedDays;
        CachedChart cached = chartCache.get(cacheKey);
        if (cached != null && cached.isFresh()) {
            return cached.chart();
        }

        try {
            Map response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/coins/{id}/market_chart")
                            .queryParam("vs_currency", "usd")
                            .queryParam("days", normalizedDays)
                            .queryParam("precision", "6")
                            .build(coinId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<?> prices = response == null ? List.of() : (List<?>) response.getOrDefault("prices", List.of());
            List<MarketChartPoint> points = prices.stream()
                    .filter(List.class::isInstance)
                    .map(row -> (List<?>) row)
                    .filter(row -> row.size() >= 2)
                    .map(row -> new MarketChartPoint(
                            Instant.ofEpochMilli(((Number) row.get(0)).longValue()),
                            decimal(row.get(1))))
                    .toList();

            if (points.isEmpty()) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "CoinGecko did not return chart data for " + normalized);
            }

            MarketChartResponse chart = new MarketChartResponse(normalized, normalizedDays, "coingecko", points);
            chartCache.put(cacheKey, new CachedChart(chart, Instant.now()));
            return chart;
        } catch (RuntimeException exception) {
            if (cached != null) {
                return new MarketChartResponse(
                        cached.chart().symbol(),
                        cached.chart().days(),
                        "coingecko-cache",
                        cached.chart().prices()
                );
            }
            return fallbackChart(normalized, normalizedDays);
        }
    }

    public List<CryptoSearchResult> search(String query) {
        String normalized = normalize(query);
        if (normalized.length() < 2) {
            return List.of();
        }
        return searchCache.computeIfAbsent(normalized, this::searchCoins);
    }

    private String coinId(String normalized) {
        if (normalized.contains("-")) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        String mapped = COIN_IDS.get(normalized);
        if (mapped != null) {
            return mapped;
        }
        return resolvedCoinIds.computeIfAbsent(normalized, this::searchCoinId);
    }

    private String searchCoinId(String normalized) {
        return searchCoins(normalized).stream()
                .filter(result -> normalized.equalsIgnoreCase(result.symbol()))
                .min(Comparator.comparingInt(result -> result.marketCapRank() == null ? Integer.MAX_VALUE : result.marketCapRank()))
                .or(() -> searchCoins(normalized).stream().findFirst())
                .map(CryptoSearchResult::id)
                .orElse(normalized.toLowerCase(Locale.ROOT));
    }

    private List<CryptoSearchResult> searchCoins(String normalized) {
        try {
            Map response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("query", normalized)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map> coins = response == null
                    ? List.of()
                    : ((List<?>) response.getOrDefault("coins", List.of())).stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .toList();

            return coins.stream()
                    .filter(coin -> Objects.nonNull(coin.get("id")))
                    .sorted(Comparator.comparingInt(this::marketCapRank))
                    .limit(25)
                    .map(coin -> new CryptoSearchResult(
                            String.valueOf(coin.get("id")),
                            String.valueOf(coin.get("symbol")).toUpperCase(Locale.ROOT),
                            String.valueOf(coin.get("name")),
                            coin.get("thumb") == null ? "" : String.valueOf(coin.get("thumb")),
                            marketCapRank(coin) == Integer.MAX_VALUE ? null : marketCapRank(coin)))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private int marketCapRank(Map coin) {
        Object rank = coin.get("market_cap_rank");
        if (rank instanceof Number number) {
            return number.intValue();
        }
        return Integer.MAX_VALUE;
    }

    private String normalize(String asset) {
        return asset.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private QuoteResponse fallbackQuote(String symbol) {
        BigDecimal current = fallbackPrice(symbol);
        BigDecimal percent = fallbackPercentChange(symbol);
        BigDecimal change = current.multiply(percent)
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return new QuoteResponse(symbol, current, change, percent, Instant.now(), "demo-fallback");
    }

    private CryptoMarketStats fallbackMarketStats(String symbol, String coinId) {
        QuoteResponse quote = fallbackQuote(symbol);
        BigDecimal marketCap = quote.currentPrice().multiply(BigDecimal.valueOf(fallbackSupply(symbol)));
        return new CryptoMarketStats(
                coinId,
                symbol,
                symbol,
                "",
                null,
                quote.currentPrice(),
                quote.change(),
                quote.percentChange(),
                quote.currentPrice().multiply(BigDecimal.valueOf(0.94)).setScale(6, RoundingMode.HALF_UP),
                quote.currentPrice().multiply(BigDecimal.valueOf(1.07)).setScale(6, RoundingMode.HALF_UP),
                marketCap.setScale(0, RoundingMode.HALF_UP),
                marketCap.multiply(BigDecimal.valueOf(1.18)).setScale(0, RoundingMode.HALF_UP),
                marketCap.multiply(BigDecimal.valueOf(0.06)).setScale(0, RoundingMode.HALF_UP),
                BigDecimal.valueOf(fallbackSupply(symbol)).setScale(0, RoundingMode.HALF_UP),
                BigDecimal.valueOf(fallbackSupply(symbol) * 1.15).setScale(0, RoundingMode.HALF_UP),
                BigDecimal.ZERO,
                "demo-fallback"
        );
    }

    private MarketChartResponse fallbackChart(String symbol, int days) {
        BigDecimal base = fallbackPrice(symbol);
        double seed = Math.abs(symbol.hashCode() % 1000) / 1000.0;
        int pointCount = Math.max(24, days * 8);
        Instant start = Instant.now().minus(Duration.ofDays(days));

        List<MarketChartPoint> points = java.util.stream.IntStream.rangeClosed(0, pointCount)
                .mapToObj(index -> {
                    double progress = index / (double) pointCount;
                    double wave = Math.sin((progress * Math.PI * 4) + seed) * 0.045;
                    double trend = (fallbackPercentChange(symbol).doubleValue() / 100.0) * (progress - 0.5);
                    BigDecimal price = base.multiply(BigDecimal.valueOf(1.0 + wave + trend))
                            .max(BigDecimal.valueOf(0.000001))
                            .setScale(6, RoundingMode.HALF_UP);
                    long seconds = Duration.ofDays(days).toSeconds() * index / pointCount;
                    return new MarketChartPoint(start.plusSeconds(seconds), price);
                })
                .toList();

        return new MarketChartResponse(symbol, days, "demo-fallback", points);
    }

    private BigDecimal fallbackPrice(String symbol) {
        Map<String, BigDecimal> known = Map.ofEntries(
                Map.entry("BTC", BigDecimal.valueOf(80000)),
                Map.entry("ETH", BigDecimal.valueOf(4200)),
                Map.entry("SOL", BigDecimal.valueOf(190)),
                Map.entry("BNB", BigDecimal.valueOf(650)),
                Map.entry("XRP", BigDecimal.valueOf(0.62)),
                Map.entry("ADA", BigDecimal.valueOf(0.48)),
                Map.entry("AVAX", BigDecimal.valueOf(38)),
                Map.entry("DOGE", BigDecimal.valueOf(0.18)),
                Map.entry("CRV", BigDecimal.valueOf(0.72)),
                Map.entry("LINK", BigDecimal.valueOf(19))
        );
        BigDecimal knownPrice = known.get(symbol);
        if (knownPrice != null) {
            return knownPrice;
        }
        double value = 0.1 + Math.abs(symbol.hashCode() % 10_000) / 137.0;
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal fallbackPercentChange(String symbol) {
        double value = (symbol.hashCode() % 1800) / 100.0;
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private long fallbackSupply(String symbol) {
        return 10_000_000L + Math.abs(symbol.hashCode() % 900_000_000L);
    }

    private record CachedQuote(QuoteResponse quote, Instant cachedAt) {
        private boolean isFresh() {
            return Duration.between(cachedAt, Instant.now()).toSeconds() < 60;
        }
    }

    private record CachedMarketStats(CryptoMarketStats stats, Instant cachedAt) {
        private boolean isFresh() {
            return Duration.between(cachedAt, Instant.now()).toSeconds() < 60;
        }
    }

    private record CachedChart(MarketChartResponse chart, Instant cachedAt) {
        private boolean isFresh() {
            return Duration.between(cachedAt, Instant.now()).toSeconds() < 300;
        }
    }
}
