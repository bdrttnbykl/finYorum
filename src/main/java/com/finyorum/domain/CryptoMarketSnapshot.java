package com.finyorum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "crypto_market_snapshots")
public class CryptoMarketSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "coin_id", nullable = false)
    private String coinId;

    @Column(nullable = false)
    private String name;

    @Column(name = "market_cap_rank")
    private Integer marketCapRank;

    @Column(name = "current_price", nullable = false, precision = 38, scale = 12)
    private BigDecimal currentPrice;

    @Column(name = "price_change_24h", nullable = false, precision = 38, scale = 12)
    private BigDecimal priceChange24h;

    @Column(name = "price_change_percentage_24h", nullable = false, precision = 38, scale = 12)
    private BigDecimal priceChangePercentage24h;

    @Column(name = "low_24h", nullable = false, precision = 38, scale = 12)
    private BigDecimal low24h;

    @Column(name = "high_24h", nullable = false, precision = 38, scale = 12)
    private BigDecimal high24h;

    @Column(name = "market_cap", nullable = false, precision = 38, scale = 2)
    private BigDecimal marketCap;

    @Column(name = "fully_diluted_valuation", nullable = false, precision = 38, scale = 2)
    private BigDecimal fullyDilutedValuation;

    @Column(name = "total_volume", nullable = false, precision = 38, scale = 2)
    private BigDecimal totalVolume;

    @Column(name = "circulating_supply", nullable = false, precision = 38, scale = 2)
    private BigDecimal circulatingSupply;

    @Column(name = "total_supply", nullable = false, precision = 38, scale = 2)
    private BigDecimal totalSupply;

    @Column(name = "max_supply", nullable = false, precision = 38, scale = 2)
    private BigDecimal maxSupply;

    @Column(name = "chart_json", nullable = false, columnDefinition = "text")
    private String chartJson;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal volatility;

    @Column(name = "sharpe_ratio", nullable = false, precision = 18, scale = 8)
    private BigDecimal sharpeRatio;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    protected CryptoMarketSnapshot() {
    }

    public CryptoMarketSnapshot(String symbol,
                                String coinId,
                                String name,
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
                                String chartJson,
                                BigDecimal volatility,
                                BigDecimal sharpeRatio,
                                String riskLevel,
                                Instant fetchedAt) {
        this.symbol = symbol;
        this.coinId = coinId;
        this.name = name;
        this.marketCapRank = marketCapRank;
        this.currentPrice = currentPrice;
        this.priceChange24h = priceChange24h;
        this.priceChangePercentage24h = priceChangePercentage24h;
        this.low24h = low24h;
        this.high24h = high24h;
        this.marketCap = marketCap;
        this.fullyDilutedValuation = fullyDilutedValuation;
        this.totalVolume = totalVolume;
        this.circulatingSupply = circulatingSupply;
        this.totalSupply = totalSupply;
        this.maxSupply = maxSupply;
        this.chartJson = chartJson;
        this.volatility = volatility;
        this.sharpeRatio = sharpeRatio;
        this.riskLevel = riskLevel;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCoinId() {
        return coinId;
    }

    public String getName() {
        return name;
    }

    public Integer getMarketCapRank() {
        return marketCapRank;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getPriceChange24h() {
        return priceChange24h;
    }

    public BigDecimal getPriceChangePercentage24h() {
        return priceChangePercentage24h;
    }

    public BigDecimal getLow24h() {
        return low24h;
    }

    public BigDecimal getHigh24h() {
        return high24h;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public BigDecimal getFullyDilutedValuation() {
        return fullyDilutedValuation;
    }

    public BigDecimal getTotalVolume() {
        return totalVolume;
    }

    public BigDecimal getCirculatingSupply() {
        return circulatingSupply;
    }

    public BigDecimal getTotalSupply() {
        return totalSupply;
    }

    public BigDecimal getMaxSupply() {
        return maxSupply;
    }

    public String getChartJson() {
        return chartJson;
    }

    public BigDecimal getVolatility() {
        return volatility;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
