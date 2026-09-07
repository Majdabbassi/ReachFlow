package com.majd.reachflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class GeocodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final long MIN_INTERVAL_MS = 1200;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, double[]> cache = new ConcurrentHashMap<>();
    private long lastRequestAt = 0;

    public GeocodingService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public double[] geocode(String city, String address) {
        String query = buildQuery(city, address);
        if (query == null) {
            return null;
        }
        String cacheKey = query.toLowerCase().trim();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        rateLimit();

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = NOMINATIM_URL + "?format=json&limit=1&countrycodes=de&q=" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "ReachFlow/1.0 (contact@reachflow)")
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 || response.body().isEmpty()) {
                log.warn("Geocode non-200 for '{}': status={}", query, response.statusCode());
                return null;
            }

            JsonNode arr = objectMapper.readTree(response.body());
            if (arr.isArray() && arr.size() > 0) {
                JsonNode hit = arr.get(0);
                double lat = hit.path("lat").asDouble(0);
                double lon = hit.path("lon").asDouble(0);
                if (lat != 0 && lon != 0) {
                    double[] coords = {lat, lon};
                    cache.put(cacheKey, coords);
                    return coords;
                }
                log.warn("Geocode bad coords for '{}': lat={}, lon={}", query, lat, lon);
            }
        } catch (Exception e) {
            log.warn("Geocoding failed for '{}': {}", query, e.getMessage());
        }
        return null;
    }

    private String buildQuery(String city, String address) {
        String text = null;
        if (address != null && !address.isBlank()) {
            text = address;
        } else if (city != null && !city.isBlank()) {
            text = city;
        }
        if (text == null) {
            return null;
        }
        boolean hasCountry = text.toLowerCase().contains("germany")
                || text.toLowerCase().contains("deutschland")
                || text.matches(".*\\b\\d{5}\\b.*");
        return hasCountry ? text : text + ", Germany";
    }

    private synchronized void rateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestAt;
        if (elapsed < MIN_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_INTERVAL_MS - elapsed);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
