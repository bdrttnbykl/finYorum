package com.finyorum.service;

import com.finyorum.dto.CryptoMarketStats;
import com.finyorum.dto.CryptoNewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

@Service
public class CryptoNewsService {

    private static final Logger log = LoggerFactory.getLogger(CryptoNewsService.class);
    private static final Duration NEWS_TTL = Duration.ofMinutes(30);

    private final WebClient webClient;
    private final Map<String, CachedNews> cache = new ConcurrentHashMap<>();

    public CryptoNewsService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://news.google.com").build();
    }

    public List<CryptoNewsItem> recentNews(CryptoMarketStats market) {
        String query = newsQuery(market);
        CachedNews cached = cache.get(query);
        if (cached != null && cached.isFresh()) {
            return cached.items();
        }

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rss/search")
                            .queryParam("q", query)
                            .queryParam("hl", "en-US")
                            .queryParam("gl", "US")
                            .queryParam("ceid", "US:en")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(8));

            List<CryptoNewsItem> items = parse(response);
            cache.put(query, new CachedNews(items, Instant.now()));
            return items;
        } catch (Exception exception) {
            log.warn("Crypto news lookup failed for {}: {}", market.symbol(), exception.getMessage());
            return cached == null ? List.of() : cached.items();
        }
    }

    private String newsQuery(CryptoMarketStats market) {
        String name = market.name() == null || market.name().isBlank() ? market.symbol() : market.name();
        return "\"" + name + "\" " + market.symbol() + " crypto OR token";
    }

    private List<CryptoNewsItem> parse(String xml) throws Exception {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList items = document.getElementsByTagName("item");

        return IntStream.range(0, Math.min(items.getLength(), 5))
                .mapToObj(index -> (Element) items.item(index))
                .map(item -> new CryptoNewsItem(
                        text(item, "title"),
                        source(item),
                        text(item, "link"),
                        publishedAt(text(item, "pubDate"))))
                .toList();
    }

    private String text(Element item, String tagName) {
        NodeList nodes = item.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private String source(Element item) {
        NodeList nodes = item.getElementsByTagName("source");
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "unknown";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private Instant publishedAt(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }

    private record CachedNews(List<CryptoNewsItem> items, Instant cachedAt) {
        private boolean isFresh() {
            return Duration.between(cachedAt, Instant.now()).compareTo(NEWS_TTL) < 0;
        }
    }
}
