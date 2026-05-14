package com.finyorum.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finyorum.domain.AiAnalysis;
import com.finyorum.dto.AiAnalysisResponse;
import com.finyorum.dto.CryptoMarketStats;
import com.finyorum.dto.CryptoNewsItem;
import com.finyorum.dto.MarketChartResponse;
import com.finyorum.dto.QuoteResponse;
import com.finyorum.dto.RiskResponse;
import com.finyorum.repository.AiAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);
    private static final Duration OPENAI_TIMEOUT = Duration.ofSeconds(20);
    private static final Instant TOKENOMICS_NEWS_PROMPT_VERSION = Instant.parse("2026-05-14T07:55:00Z");
    private static final List<String> GEMINI_FALLBACK_MODELS = List.of(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite"
    );

    private final CryptoService cryptoService;
    private final RiskService riskService;
    private final AiAnalysisRepository analyses;
    private final WebClient webClient;
    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper;
    private final String aiProvider;
    private final String openAiApiKey;
    private final String openAiModel;
    private final String geminiApiKey;
    private final String geminiModel;

    public AiAnalysisService(CryptoService cryptoService,
                             RiskService riskService,
                             AiAnalysisRepository analyses,
                             WebClient.Builder webClientBuilder,
                             ObjectMapper objectMapper,
                             @Value("${integrations.ai.provider:openai}") String aiProvider,
                             @Value("${integrations.openai.api-key:}") String openAiApiKey,
                             @Value("${integrations.openai.model:gpt-5.2}") String openAiModel,
                             @Value("${integrations.gemini.api-key:}") String geminiApiKey,
                             @Value("${integrations.gemini.model:gemini-2.5-flash}") String geminiModel) {
        this.cryptoService = cryptoService;
        this.riskService = riskService;
        this.analyses = analyses;
        this.webClient = webClientBuilder.baseUrl("https://api.openai.com").build();
        this.geminiWebClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
        this.aiProvider = aiProvider;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
    }

    public AiAnalysisResponse analyze(String symbol) {
        QuoteResponse quote = cryptoService.quote(symbol);
        MarketChartResponse chart = cryptoService.marketChart(symbol, 30);
        RiskResponse risk = riskService.calculateFromPrices(
                symbol,
                chart.prices().stream().map(point -> point.price()).toList()
        );
        return analyze(quote, risk, chart);
    }

    public AiAnalysisResponse analyze(QuoteResponse quote, RiskResponse risk) {
        return analyze(quote, risk, null);
    }

    public AiAnalysisResponse analyze(QuoteResponse quote, RiskResponse risk, MarketChartResponse chart) {
        AiAnalysisResponse response = aiResponse(quote, null, risk, chart);

        analyses.save(new AiAnalysis(null, response.symbol(), aiProvider, configuredModel(), response.recommendation(), response.summary()));
        return response;
    }

    public AiAnalysisResponse analyzeForSnapshot(Long snapshotId,
                                                 QuoteResponse quote,
                                                 RiskResponse risk,
                                                 MarketChartResponse chart) {
        return analyzeForSnapshot(snapshotId, quote, null, risk, chart);
    }

    public AiAnalysisResponse analyzeForSnapshot(Long snapshotId,
                                                 QuoteResponse quote,
                                                 CryptoMarketStats market,
                                                 RiskResponse risk,
                                                 MarketChartResponse chart) {
        return analyzeForSnapshot(snapshotId, quote, market, risk, chart, List.of());
    }

    public AiAnalysisResponse analyzeForSnapshot(Long snapshotId,
                                                 QuoteResponse quote,
                                                 CryptoMarketStats market,
                                                 RiskResponse risk,
                                                 MarketChartResponse chart,
                                                 List<CryptoNewsItem> news) {
        if (snapshotId != null) {
            return analyses.findTopBySnapshotIdOrderByCreatedAtDesc(snapshotId)
                    .filter(analysis -> analysis.getCreatedAt().isAfter(TOKENOMICS_NEWS_PROMPT_VERSION))
                    .filter(analysis -> aiProvider.equalsIgnoreCase(nullToEmpty(analysis.getProvider())))
                    .filter(analysis -> configuredModel().equals(nullToEmpty(analysis.getModel())))
                    .map(analysis -> new AiAnalysisResponse(
                            analysis.getSymbol(),
                            analysis.getRecommendation(),
                            analysis.getSummary()))
                    .orElseGet(() -> createSnapshotAnalysis(snapshotId, quote, market, risk, chart, news));
        }
        return analyze(quote, risk, chart);
    }

    private AiAnalysisResponse createSnapshotAnalysis(Long snapshotId,
                                                      QuoteResponse quote,
                                                      CryptoMarketStats market,
                                                      RiskResponse risk,
                                                      MarketChartResponse chart,
                                                      List<CryptoNewsItem> news) {
        AiAnalysisResponse response = aiResponse(quote, market, risk, chart, news);
        analyses.save(new AiAnalysis(
                snapshotId,
                response.symbol(),
                aiProvider,
                configuredModel(),
                response.recommendation(),
                response.summary()));
        return response;
    }

    private String configuredModel() {
        if ("gemini".equalsIgnoreCase(aiProvider)) {
            return geminiModel;
        }
        if ("openai".equalsIgnoreCase(aiProvider)) {
            return openAiModel;
        }
        return "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private AiAnalysisResponse aiResponse(QuoteResponse quote,
                                          CryptoMarketStats market,
                                          RiskResponse risk,
                                          MarketChartResponse chart) {
        return aiResponse(quote, market, risk, chart, List.of());
    }

    private AiAnalysisResponse aiResponse(QuoteResponse quote,
                                          CryptoMarketStats market,
                                          RiskResponse risk,
                                          MarketChartResponse chart,
                                          List<CryptoNewsItem> news) {
        if ("gemini".equalsIgnoreCase(aiProvider)) {
            return StringUtils.hasText(geminiApiKey)
                    ? geminiResponse(quote, market, risk, chart, news)
                    : aiUnavailableResponse(quote, risk, "Gemini", "GEMINI_API_KEY tanimli degil.");
        }

        if ("openai".equalsIgnoreCase(aiProvider)) {
            return StringUtils.hasText(openAiApiKey)
                    ? openAiResponse(quote, market, risk, chart, news)
                    : aiUnavailableResponse(quote, risk, "OpenAI", "OPENAI_API_KEY tanimli degil.");
        }

        return aiUnavailableResponse(quote, risk, aiProvider, "Desteklenmeyen AI_PROVIDER: " + aiProvider);
    }

    private AiAnalysisResponse openAiResponse(QuoteResponse quote,
                                              CryptoMarketStats market,
                                              RiskResponse risk,
                                              MarketChartResponse chart,
                                              List<CryptoNewsItem> news) {
        try {
            JsonNode response = webClient.post()
                    .uri("/v1/responses")
                    .headers(headers -> headers.setBearerAuth(openAiApiKey))
                    .bodyValue(openAiRequest(quote, market, risk, chart, news))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(OPENAI_TIMEOUT);

            JsonNode parsed = parseStructuredOutput(response);
            String recommendation = parsed.path("recommendation").asText(recommendation(risk)).toUpperCase();
            if (!List.of("BUY", "HOLD", "WATCH").contains(recommendation)) {
                recommendation = recommendation(risk);
            }

            String summary = parsed.path("summary").asText("");
            if (!StringUtils.hasText(summary)) {
                return aiUnavailableResponse(quote, risk, "OpenAI", "OpenAI bos cevap dondu.");
            }

            return new AiAnalysisResponse(quote.symbol(), recommendation, summary);
        } catch (WebClientResponseException ex) {
            log.warn("OpenAI analysis failed for {} with HTTP {}: {}",
                    quote.symbol(),
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            return aiUnavailableResponse(
                    quote,
                    risk,
                    "OpenAI",
                    "OpenAI HTTP " + ex.getStatusCode().value() + ": " + compactOpenAiError(ex.getResponseBodyAsString())
            );
        } catch (Exception ex) {
            log.warn("OpenAI analysis failed for {}: {}",
                    quote.symbol(),
                    ex.getMessage());
            return aiUnavailableResponse(quote, risk, "OpenAI", ex.getMessage());
        }
    }

    private AiAnalysisResponse geminiResponse(QuoteResponse quote,
                                              CryptoMarketStats market,
                                              RiskResponse risk,
                                              MarketChartResponse chart,
                                              List<CryptoNewsItem> news) {
        String lastError = "";
        List<String> models = GEMINI_FALLBACK_MODELS.contains(geminiModel)
                ? GEMINI_FALLBACK_MODELS
                : List.of(geminiModel, "gemini-2.0-flash");

        for (String model : models) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                GeminiAttempt response = tryGeminiResponse(quote, market, risk, chart, news, model);
                if (response.analysis() != null) {
                    return response.analysis();
                }
                lastError = response.error();
                if (response.quotaExhausted()) {
                    return aiUnavailableResponse(quote, risk, "Gemini", lastError);
                }
                if (!response.retryable()) {
                    break;
                }
                sleepBeforeRetry(attempt);
            }
        }

        return aiUnavailableResponse(quote, risk, "Gemini", lastError);
    }

    private GeminiAttempt tryGeminiResponse(QuoteResponse quote,
                                            CryptoMarketStats market,
                                            RiskResponse risk,
                                            MarketChartResponse chart,
                                            List<CryptoNewsItem> news,
                                            String model) {
        try {
            JsonNode response = geminiWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", geminiApiKey)
                            .build(model))
                    .bodyValue(geminiRequest(quote, market, risk, chart, news))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(OPENAI_TIMEOUT);

            JsonNode parsed = parseGeminiOutput(response);
            String recommendation = parsed.path("recommendation").asText(recommendation(risk)).toUpperCase();
            if (!List.of("BUY", "HOLD", "WATCH").contains(recommendation)) {
                recommendation = recommendation(risk);
            }

            String summary = parsed.path("summary").asText("");
            if (!StringUtils.hasText(summary)) {
                return new GeminiAttempt(null, true, model + " bos cevap dondu.", false);
            }

            return new GeminiAttempt(new AiAnalysisResponse(quote.symbol(), recommendation, summary), false, "", false);
        } catch (WebClientResponseException ex) {
            String error = "Gemini HTTP " + ex.getStatusCode().value() + " (" + model + "): "
                    + compactGeminiError(ex.getResponseBodyAsString());
            log.warn("Gemini analysis failed for {} with HTTP {}: {}",
                    quote.symbol(),
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 429 && error.contains("RESOURCE_EXHAUSTED")) {
                return new GeminiAttempt(null, false, conciseQuotaError(model), true);
            }
            if (ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429) {
                return new GeminiAttempt(null, true, error, false);
            }
            return new GeminiAttempt(aiUnavailableResponse(quote, risk, "Gemini", error), false, error, false);
        } catch (Exception ex) {
            log.warn("Gemini analysis failed for {}: {}", quote.symbol(), ex.getMessage());
            return new GeminiAttempt(null, true, model + ": " + ex.getMessage(), false);
        }
    }

    private String conciseQuotaError(String model) {
        return model + " kotasi dolu veya bu API key icin kota 0. "
                + "Google AI Studio'da billing/kota durumunu kontrol et ya da AI_PROVIDER=openai kullan.";
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> openAiRequest(QuoteResponse quote,
                                              CryptoMarketStats market,
                                              RiskResponse risk,
                                              MarketChartResponse chart,
                                              List<CryptoNewsItem> news) {
        ChartFeatures features = chartFeatures(chart, quote.currentPrice());
        MarketFeatures marketFeatures = marketFeatures(market, quote.currentPrice());

        return Map.of(
                "model", openAiModel,
                "instructions", """
                        You are a crypto market analysis assistant for a dashboard.
                        Respond in Turkish.
                        Use only the supplied market data. Do not invent news, events, targets, or external facts.
                        Prioritize tokenomics, circulating/max supply, total/max supply, FDV/market-cap pressure, volume depth, and recent web news.
                        Treat price-chart trend as supporting evidence only; do not write a chart recap as the main analysis.
                        Explain possible sell-pressure or dilution risk only when supported by supply, FDV, volume, or news data.
                        If news headlines are weak, stale, or unavailable, say that news signal is limited.
                        Do not present the output as financial advice. Keep it concise and risk-aware.
                        """,
                "input", """
                        Asset: %s
                        Current price USD: %s
                        24h absolute change: %s
                        24h percent change: %s%%
                        Market cap rank: %s
                        Market cap USD: %s
                        24h volume USD: %s
                        Volume / market cap: %s%%
                        Circulating supply: %s
                        Total supply: %s
                        Max supply: %s
                        Circulating / max supply: %s%%
                        Total / max supply: %s%%
                        FDV / market cap: %s
                        Supply pressure note: %s
                        24h low: %s
                        24h high: %s
                        Position inside 24h range: %s
                        Price source: %s
                        Chart source: %s
                        30d volatility: %s
                        Sharpe ratio: %s
                        Risk level: %s
                        7d return: %s%%
                        30d return: %s%%
                        30d high: %s
                        30d low: %s
                        Current drawdown from 30d high: %s%%
                        Trend: %s
                        Recent web news headlines:
                        %s

                        Return BUY only when trend and risk-adjusted return are both strong.
                        Return WATCH when dilution risk, weak volume, negative news, risk, drawdown, or negative trend dominates.
                        Return HOLD for mixed or neutral setups.
                        Return a recommendation and a 5 sentence summary.
                        Sentence 1: supply structure and possible unlock/dilution/sell-pressure interpretation.
                        Sentence 2: liquidity using volume/market-cap and market-cap rank.
                        Sentence 3: recent news signal, citing headline sources by name.
                        Sentence 4: risk metrics and price trend only as confirmation or contradiction.
                        Sentence 5: why the recommendation is BUY, HOLD, or WATCH.
                        """.formatted(
                        quote.symbol(),
                        quote.currentPrice(),
                        quote.change(),
                        quote.percentChange(),
                        market == null || market.marketCapRank() == null ? "not-available" : market.marketCapRank(),
                        market == null ? "not-available" : market.marketCap(),
                        market == null ? "not-available" : market.totalVolume(),
                        marketFeatures.volumeToMarketCap(),
                        market == null ? "not-available" : market.circulatingSupply(),
                        market == null ? "not-available" : market.totalSupply(),
                        market == null ? "not-available" : market.maxSupply(),
                        marketFeatures.circulatingToMaxSupply(),
                        marketFeatures.totalToMaxSupply(),
                        marketFeatures.fdvToMarketCap(),
                        marketFeatures.supplyPressureNote(),
                        market == null ? "not-available" : market.low24h(),
                        market == null ? "not-available" : market.high24h(),
                        marketFeatures.positionIn24hRange(),
                        quote.source(),
                        chart == null ? "not-available" : chart.source(),
                        risk.volatility(),
                        risk.sharpeRatio(),
                        risk.riskLevel(),
                        features.return7d(),
                        features.return30d(),
                        features.high30d(),
                        features.low30d(),
                        features.drawdownFromHigh(),
                        features.trend(),
                        newsLines(news)
                ),
                "max_output_tokens", 900,
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "crypto_analysis",
                                "strict", true,
                                "schema", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "properties", Map.of(
                                                "recommendation", Map.of(
                                                        "type", "string",
                                                        "enum", List.of("BUY", "HOLD", "WATCH")
                                                ),
                                                "summary", Map.of("type", "string")
                                        ),
                                        "required", List.of("recommendation", "summary")
                                )
                        )
                )
        );
    }

    private Map<String, Object> geminiRequest(QuoteResponse quote,
                                              CryptoMarketStats market,
                                              RiskResponse risk,
                                              MarketChartResponse chart,
                                              List<CryptoNewsItem> news) {
        return Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", analysisPrompt(quote, market, risk, chart, news)))
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.35,
                        "maxOutputTokens", 2048,
                        "responseMimeType", "application/json",
                        "responseSchema", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "recommendation", Map.of(
                                                "type", "STRING",
                                                "enum", List.of("BUY", "HOLD", "WATCH")
                                        ),
                                        "summary", Map.of("type", "STRING")
                                ),
                                "required", List.of("recommendation", "summary")
                        )
                )
        );
    }

    private String analysisPrompt(QuoteResponse quote,
                                  CryptoMarketStats market,
                                  RiskResponse risk,
                                  MarketChartResponse chart,
                                  List<CryptoNewsItem> news) {
        ChartFeatures features = chartFeatures(chart, quote.currentPrice());
        MarketFeatures marketFeatures = marketFeatures(market, quote.currentPrice());
        return """
                You are a crypto market analysis assistant for a dashboard.
                Respond in Turkish.
                Use only the supplied market data. Do not invent news, events, targets, or external facts.
                Prioritize tokenomics, circulating/max supply, total/max supply, FDV/market-cap pressure, volume depth, and recent web news.
                Treat price-chart trend as supporting evidence only; do not write a chart recap as the main analysis.
                Explain possible sell-pressure or dilution risk only when supported by supply, FDV, volume, or news data.
                If news headlines are weak, stale, or unavailable, say that news signal is limited.
                Do not present the output as financial advice. Keep it concise and risk-aware.

                Asset: %s
                Current price USD: %s
                24h absolute change: %s
                24h percent change: %s%%
                Market cap rank: %s
                Market cap USD: %s
                24h volume USD: %s
                Volume / market cap: %s%%
                Circulating supply: %s
                Total supply: %s
                Max supply: %s
                Circulating / max supply: %s%%
                Total / max supply: %s%%
                FDV / market cap: %s
                Supply pressure note: %s
                24h low: %s
                24h high: %s
                Position inside 24h range: %s
                Price source: %s
                Chart source: %s
                30d volatility: %s
                Sharpe ratio: %s
                Risk level: %s
                7d return: %s%%
                30d return: %s%%
                30d high: %s
                30d low: %s
                Current drawdown from 30d high: %s%%
                Trend: %s
                Recent web news headlines:
                %s

                Return BUY only when trend and risk-adjusted return are both strong.
                Return WATCH when dilution risk, weak volume, negative news, risk, drawdown, or negative trend dominates.
                Return HOLD for mixed or neutral setups.
                Return JSON with recommendation and a 5 sentence summary.
                Sentence 1: supply structure and possible unlock/dilution/sell-pressure interpretation.
                Sentence 2: liquidity using volume/market-cap and market-cap rank.
                Sentence 3: recent news signal, citing headline sources by name.
                Sentence 4: risk metrics and price trend only as confirmation or contradiction.
                Sentence 5: why the recommendation is BUY, HOLD, or WATCH.
                """.formatted(
                quote.symbol(),
                quote.currentPrice(),
                quote.change(),
                quote.percentChange(),
                market == null || market.marketCapRank() == null ? "not-available" : market.marketCapRank(),
                market == null ? "not-available" : market.marketCap(),
                market == null ? "not-available" : market.totalVolume(),
                marketFeatures.volumeToMarketCap(),
                market == null ? "not-available" : market.circulatingSupply(),
                market == null ? "not-available" : market.totalSupply(),
                market == null ? "not-available" : market.maxSupply(),
                marketFeatures.circulatingToMaxSupply(),
                marketFeatures.totalToMaxSupply(),
                marketFeatures.fdvToMarketCap(),
                marketFeatures.supplyPressureNote(),
                market == null ? "not-available" : market.low24h(),
                market == null ? "not-available" : market.high24h(),
                marketFeatures.positionIn24hRange(),
                quote.source(),
                chart == null ? "not-available" : chart.source(),
                risk.volatility(),
                risk.sharpeRatio(),
                risk.riskLevel(),
                features.return7d(),
                features.return30d(),
                features.high30d(),
                features.low30d(),
                features.drawdownFromHigh(),
                features.trend(),
                newsLines(news)
        );
    }

    private MarketFeatures marketFeatures(CryptoMarketStats market, BigDecimal currentPrice) {
        if (market == null) {
            return new MarketFeatures(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "not-available", "not-available");
        }

        BigDecimal volumeToMarketCap = market.marketCap() != null && market.marketCap().compareTo(BigDecimal.ZERO) > 0
                ? market.totalVolume()
                .multiply(BigDecimal.valueOf(100))
                .divide(market.marketCap(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String rangePosition = "not-available";
        if (market.low24h() != null
                && market.high24h() != null
                && market.high24h().compareTo(market.low24h()) > 0) {
            BigDecimal position = currentPrice.subtract(market.low24h())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(market.high24h().subtract(market.low24h()), 2, RoundingMode.HALF_UP);
            rangePosition = position + "% from 24h low toward 24h high";
        }

        BigDecimal circulatingToMax = percentOf(market.circulatingSupply(), market.maxSupply());
        BigDecimal totalToMax = percentOf(market.totalSupply(), market.maxSupply());
        BigDecimal fdvToMarketCap = ratio(market.fullyDilutedValuation(), market.marketCap());
        String supplyPressureNote = supplyPressureNote(circulatingToMax, totalToMax, fdvToMarketCap);

        return new MarketFeatures(
                volumeToMarketCap,
                circulatingToMax,
                totalToMax,
                fdvToMarketCap,
                supplyPressureNote,
                rangePosition);
    }

    private BigDecimal percentOf(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private String supplyPressureNote(BigDecimal circulatingToMax, BigDecimal totalToMax, BigDecimal fdvToMarketCap) {
        if (fdvToMarketCap.doubleValue() >= 2.0 && circulatingToMax.doubleValue() < 60.0) {
            return "high dilution/sell-pressure risk: FDV is much higher than market cap and circulating supply is low";
        }
        if (totalToMax.doubleValue() > circulatingToMax.doubleValue() + 15.0) {
            return "moderate future supply risk: total supply is meaningfully above circulating supply";
        }
        if (circulatingToMax.doubleValue() >= 85.0 && fdvToMarketCap.doubleValue() <= 1.2) {
            return "limited dilution signal: most max supply appears circulating";
        }
        return "mixed or unavailable supply pressure signal";
    }

    private String newsLines(List<CryptoNewsItem> news) {
        if (news == null || news.isEmpty()) {
            return "No recent web news headlines found.";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < news.size(); index++) {
            CryptoNewsItem item = news.get(index);
            builder.append(index + 1)
                    .append(". ")
                    .append(item.title())
                    .append(" | source: ")
                    .append(item.source())
                    .append(" | published: ")
                    .append(item.publishedAt())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private JsonNode parseStructuredOutput(JsonNode response) throws JsonProcessingException {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return objectMapper.createObjectNode();
        }

        for (JsonNode outputItem : response.path("output")) {
            for (JsonNode contentItem : outputItem.path("content")) {
                JsonNode parsed = contentItem.path("parsed");
                if (!parsed.isMissingNode() && parsed.isObject()) {
                    return parsed;
                }

                String text = contentItem.path("text").asText("");
                if (StringUtils.hasText(text)) {
                    return objectMapper.readTree(text);
                }
            }
        }

        String outputText = response.path("output_text").asText("");
        if (StringUtils.hasText(outputText)) {
            return objectMapper.readTree(outputText);
        }

        return objectMapper.createObjectNode();
    }

    private JsonNode parseGeminiOutput(JsonNode response) throws JsonProcessingException {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return objectMapper.createObjectNode();
        }

        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                String text = part.path("text").asText("");
                if (StringUtils.hasText(text)) {
                    return parseJsonObjectText(text);
                }
            }
        }

        return objectMapper.createObjectNode();
    }

    private JsonNode parseJsonObjectText(String text) throws JsonProcessingException {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }

        return objectMapper.readTree(trimmed);
    }

    private AiAnalysisResponse aiUnavailableResponse(QuoteResponse quote, RiskResponse risk, String provider, String reason) {
        return new AiAnalysisResponse(
                quote.symbol(),
                recommendation(risk),
                provider + " analizi alinamadi: " + sanitizeReason(reason)
                        + " Bu nedenle hazir metin veya sahte AI yorumu gosterilmiyor. "
                        + ".env icindeki AI_PROVIDER ve ilgili API key degerini kontrol et."
        );
    }

    private String compactOpenAiError(String body) {
        if (!StringUtils.hasText(body)) {
            return "detay yok";
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            String code = error.path("code").asText("");
            String message = error.path("message").asText(body);
            return (StringUtils.hasText(code) ? code + " - " : "") + message;
        } catch (Exception ignored) {
            return body;
        }
    }

    private String compactGeminiError(String body) {
        if (!StringUtils.hasText(body)) {
            return "detay yok";
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            String status = error.path("status").asText("");
            String message = error.path("message").asText(body);
            return (StringUtils.hasText(status) ? status + " - " : "") + message;
        } catch (Exception ignored) {
            return body;
        }
    }

    private String sanitizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "bilinmeyen hata";
        }
        return reason.replaceAll("\\s+", " ").trim();
    }

    private ChartFeatures chartFeatures(MarketChartResponse chart, BigDecimal fallbackPrice) {
        List<BigDecimal> prices = chart == null
                ? List.of()
                : chart.prices().stream().map(point -> point.price()).toList();

        if (prices.size() < 2) {
            return new ChartFeatures(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    fallbackPrice,
                    fallbackPrice,
                    BigDecimal.ZERO,
                    "not enough chart data"
            );
        }

        BigDecimal first = prices.get(0);
        BigDecimal last = prices.get(prices.size() - 1);
        BigDecimal high = prices.stream().max(BigDecimal::compareTo).orElse(last);
        BigDecimal low = prices.stream().min(BigDecimal::compareTo).orElse(last);
        BigDecimal sevenDaysAgo = prices.get(Math.max(0, prices.size() - Math.max(2, prices.size() / 4)));

        BigDecimal return30d = percentChange(first, last);
        BigDecimal return7d = percentChange(sevenDaysAgo, last);
        BigDecimal drawdown = high.compareTo(BigDecimal.ZERO) > 0
                ? high.subtract(last).multiply(BigDecimal.valueOf(100)).divide(high, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String trend;
        if (return30d.doubleValue() > 8.0 && return7d.doubleValue() > 1.0) {
            trend = "uptrend with recent continuation";
        } else if (return30d.doubleValue() < -8.0 && return7d.doubleValue() < -1.0) {
            trend = "downtrend with recent weakness";
        } else if (return30d.doubleValue() > 5.0 && return7d.doubleValue() < -1.0) {
            trend = "30d uptrend but short-term pullback";
        } else if (return30d.doubleValue() < -5.0 && return7d.doubleValue() > 1.0) {
            trend = "30d downtrend but short-term rebound";
        } else {
            trend = "range-bound or mixed trend";
        }

        return new ChartFeatures(
                return7d,
                return30d,
                high.setScale(6, RoundingMode.HALF_UP),
                low.setScale(6, RoundingMode.HALF_UP),
                drawdown.setScale(4, RoundingMode.HALF_UP),
                trend
        );
    }

    private BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from == null || from.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return to.subtract(from)
                .multiply(BigDecimal.valueOf(100))
                .divide(from, 4, RoundingMode.HALF_UP);
    }

    private AiAnalysisResponse ruleBasedResponse(QuoteResponse quote, RiskResponse risk) {
        double percentChange = quote.percentChange().doubleValue();
        double volatility = risk.volatility().doubleValue();
        double sharpeRatio = risk.sharpeRatio().doubleValue();
        String recommendation = recommendation(risk);
        String momentum = momentumText(percentChange);
        String riskComment = riskComment(risk.riskLevel(), volatility);
        String actionComment = actionComment(recommendation, percentChange, sharpeRatio);

        return new AiAnalysisResponse(
                quote.symbol(),
                recommendation,
                quote.symbol() + " tarafında " + momentum + " "
                        + riskComment + " "
                        + actionComment + " "
                        + "Bu yorum, " + quote.source()
                        + " fiyat verisi, 30 günlük oynaklık ve Sharpe oranı birlikte değerlendirilerek üretildi."
        );
    }

    private String momentumText(double percentChange) {
        if (percentChange >= 5.0) {
            return "son 24 saatte güçlü pozitif momentum var; hareket kısa vadede alıcı ilgisinin arttığını gösteriyor.";
        }
        if (percentChange >= 1.0) {
            return "son 24 saatte pozitif eğilim var; ancak hareket henüz tek başına güçlü trend teyidi sayılmaz.";
        }
        if (percentChange <= -5.0) {
            return "son 24 saatte sert satış baskısı var; kısa vadeli görünüm zayıflamış durumda.";
        }
        if (percentChange <= -1.0) {
            return "son 24 saatte hafif negatif eğilim var; fiyat tarafında temkinli izleme daha uygun.";
        }
        return "son 24 saatte yatay sayılabilecek bir fiyat davranışı var; net momentum sınırlı.";
    }

    private String riskComment(String riskLevel, double volatility) {
        if ("HIGH".equals(riskLevel)) {
            return "Risk seviyesi yüksek olduğu için pozisyon boyutu ve zarar-kes disiplini kritik.";
        }
        if ("MEDIUM".equals(riskLevel)) {
            return "Oynaklık orta bölgede; fiyat hareketi takip edilebilir ama agresif giriş için ek teyit gerekir.";
        }
        if (volatility > 0.10) {
            return "Risk etiketi düşük görünse de volatilite belirgin; ani geri çekilmelere karşı dikkatli olmak gerekir.";
        }
        return "Volatilite görece kontrollü; bu durum sinyalin daha sakin okunmasına yardımcı oluyor.";
    }

    private String actionComment(String recommendation, double percentChange, double sharpeRatio) {
        if ("BUY".equals(recommendation)) {
            return "Sharpe oranı destekleyici olduğu için geri çekilmelerde kademeli alım senaryosu öne çıkıyor.";
        }
        if ("WATCH".equals(recommendation)) {
            return "Mevcut veri alım için acele etmeyi desteklemiyor; fiyatın dengelenmesi ve riskin düşmesi beklenmeli.";
        }
        if (percentChange >= 5.0 && sharpeRatio < 1.0) {
            return "Yükseliş güçlü olsa da risk-getiri kalitesi henüz yeterince ikna edici değil; takip etmek daha sağlıklı.";
        }
        return "Risk-getiri dengesi nötr bölgede kaldığı için mevcut görünümde bekle-gör yaklaşımı daha uygun.";
    }

    private String recommendation(RiskResponse risk) {
        if (risk.sharpeRatio().doubleValue() > 1.0 && !"HIGH".equals(risk.riskLevel())) {
            return "BUY";
        }
        if (risk.sharpeRatio().doubleValue() < -0.5 || "HIGH".equals(risk.riskLevel())) {
            return "WATCH";
        }
        return "HOLD";
    }

    private record ChartFeatures(
            BigDecimal return7d,
            BigDecimal return30d,
            BigDecimal high30d,
            BigDecimal low30d,
            BigDecimal drawdownFromHigh,
            String trend
    ) {
    }

    private record MarketFeatures(
            BigDecimal volumeToMarketCap,
            BigDecimal circulatingToMaxSupply,
            BigDecimal totalToMaxSupply,
            BigDecimal fdvToMarketCap,
            String supplyPressureNote,
            String positionIn24hRange
    ) {
    }

    private record GeminiAttempt(
            AiAnalysisResponse analysis,
            boolean retryable,
            String error,
            boolean quotaExhausted
    ) {
    }
}
