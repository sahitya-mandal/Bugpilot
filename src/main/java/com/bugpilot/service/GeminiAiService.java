package com.bugpilot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiApiUrl;

    @Value("${gemini.api.model:gemini-1.5-flash}")
    private String geminiModel = "gemini-1.5-flash";

    @Value("${gemini.api.connect-timeout:10000}")
    private int connectTimeoutMs = 10000;

    @Value("${gemini.api.read-timeout:30000}")
    private int readTimeoutMs = 30000;

    private RestClient.Builder restClientBuilder;

    void setRestClientBuilder(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    void setGeminiApiUrl(String geminiApiUrl) {
        this.geminiApiUrl = geminiApiUrl;
    }

    void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isApiKeyConfigured() {
        return geminiApiKey != null && !geminiApiKey.trim().isEmpty();
    }

    public String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = text;
        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
            sanitized = sanitized.replace(geminiApiKey.trim(), "[REDACTED]");
        }
        sanitized = sanitized.replaceAll("(?i)key=[^&\\s\"'>]+", "key=[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)Bearer\\s+[A-Za-z0-9_\\-\\.]+", "Bearer [REDACTED]");
        sanitized = sanitized.replaceAll("(?i)Authorization\\s*:\\s*[^\\r\\n,;]+", "Authorization: [REDACTED]");
        return sanitized;
    }

    private ClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    private RestClient createRestClient() {
        RestClient.Builder builder = (this.restClientBuilder != null)
                ? this.restClientBuilder
                : RestClient.builder().requestFactory(createRequestFactory());

        String baseUrl = (geminiApiUrl != null && !geminiApiUrl.trim().isEmpty())
                ? geminiApiUrl.trim()
                : "https://generativelanguage.googleapis.com/v1beta";

        return builder.baseUrl(baseUrl).build();
    }

    public String generateContent(String prompt) {
        if (!isApiKeyConfigured()) {
            log.info("Google Gemini API key not configured. Using intelligent heuristic analysis.");
            return null;
        }

        if (prompt == null || prompt.trim().isEmpty()) {
            log.warn("Prompt is null or empty. Skipping Gemini AI analysis.");
            return null;
        }

        String model = (geminiModel != null && !geminiModel.trim().isEmpty())
                ? geminiModel.trim()
                : "gemini-1.5-flash";

        try {
            RestClient client = createRestClient();

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            Map<String, Object> response = client.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + model + ":generateContent")
                            .queryParam("key", geminiApiKey.trim())
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            return extractTextFromResponse(response);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 400) {
                log.warn("Gemini API request error (400 Bad Request). Check request parameters or prompt.");
            } else if (status == 401) {
                log.error("Gemini API authentication failed (401 Unauthorized). The configured API key is invalid or expired.");
            } else if (status == 403) {
                log.warn("Gemini API access forbidden (403 Forbidden). API key may have quota, location, or permission restrictions.");
            } else if (status == 404) {
                log.warn("Gemini API endpoint or model not found (404 Not Found). Model: {}", sanitize(model));
            } else if (status == 429) {
                log.warn("Gemini API rate limit exceeded (429 Too Many Requests). Upstream quota exhausted. Falling back to heuristic analysis.");
            } else {
                log.warn("Gemini API client error ({}). Falling back to heuristic analysis.", status);
            }
            return null;
        } catch (HttpServerErrorException e) {
            log.error("Gemini API server error ({}). Upstream service is temporarily unavailable.", e.getStatusCode().value());
            return null;
        } catch (ResourceAccessException e) {
            log.warn("Gemini API network or timeout failure: {}. Falling back to heuristic analysis.", sanitize(e.getMessage()));
            return null;
        } catch (Exception e) {
            log.warn("Gemini API call failed unexpectedly: {}. Falling back to heuristic analysis.", sanitize(e.getMessage()));
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    String extractTextFromResponse(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            log.warn("Gemini API returned null or empty response body.");
            return null;
        }

        try {
            Object candidatesObj = response.get("candidates");
            if (!(candidatesObj instanceof List<?> candidatesList) || candidatesList.isEmpty()) {
                log.warn("Gemini response contains no candidates. Candidates missing or empty.");
                return null;
            }

            Object firstCandidateObj = candidatesList.get(0);
            if (!(firstCandidateObj instanceof Map<?, ?> candidateMap)) {
                log.warn("Gemini candidate element is not an object.");
                return null;
            }

            Object contentObj = candidateMap.get("content");
            if (!(contentObj instanceof Map<?, ?> contentMap)) {
                log.warn("Gemini candidate has no valid content object.");
                return null;
            }

            Object partsObj = contentMap.get("parts");
            if (!(partsObj instanceof List<?> partsList) || partsList.isEmpty()) {
                log.warn("Gemini content contains no parts.");
                return null;
            }

            Object firstPartObj = partsList.get(0);
            if (!(firstPartObj instanceof Map<?, ?> partMap)) {
                log.warn("Gemini part element is not an object.");
                return null;
            }

            Object textObj = partMap.get("text");
            if (!(textObj instanceof String text) || text.trim().isEmpty()) {
                log.warn("Gemini part contains no valid text content.");
                return null;
            }

            return text;
        } catch (Exception e) {
            log.warn("Error extracting text from Gemini response: {}", sanitize(e.getMessage()));
            return null;
        }
    }
}
