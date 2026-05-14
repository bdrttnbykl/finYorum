package com.finyorum.service;

import com.finyorum.dto.QuoteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
public class FinanceService {

    private final WebClient webClient;
    private final String finnhubApiKey;

    public FinanceService(WebClient.Builder webClientBuilder,
                          @Value("${integrations.finnhub.api-key:}") String finnhubApiKey) {
        this.webClient = webClientBuilder.baseUrl("https://finnhub.io/api/v1").build();
        this.finnhubApiKey = finnhubApiKey;
    }

    public QuoteResponse quote(String symbol) {
        String normalized = symbol.toUpperCase();
        if (!StringUtils.hasText(finnhubApiKey)) {
            return fallbackQuote(normalized);
        }

        Map response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("symbol", normalized)
                        .queryParam("token", finnhubApiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        BigDecimal current = decimal(response.get("c"));
        BigDecimal change = decimal(response.get("d"));
        BigDecimal percent = decimal(response.get("dp"));
        return new QuoteResponse(normalized, current, change, percent, Instant.now(), "finnhub");
    }

    private QuoteResponse fallbackQuote(String symbol) {
        BigDecimal base = BigDecimal.valueOf(Math.abs(symbol.hashCode() % 300) + 50);
        BigDecimal change = BigDecimal.valueOf((symbol.hashCode() % 40) / 10.0);
        BigDecimal percent = change.multiply(BigDecimal.valueOf(100)).divide(base, 4, java.math.RoundingMode.HALF_UP);
        return new QuoteResponse(symbol, base, change, percent, Instant.now(), "mock");
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }
}
