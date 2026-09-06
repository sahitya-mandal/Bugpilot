package com.bugpilot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiAiServiceTest {

    private GeminiAiService geminiAiService;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        geminiAiService = new GeminiAiService();
        geminiAiService.setGeminiApiKey("AIzaSyFakeTestKey123456");
        geminiAiService.setGeminiApiUrl("https://generativelanguage.googleapis.com/v1beta");
        geminiAiService.setGeminiModel("gemini-1.5-flash");
        geminiAiService.setConnectTimeoutMs(5000);
        geminiAiService.setReadTimeoutMs(10000);

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        geminiAiService.setRestClientBuilder(restClientBuilder);
    }

    @Test
    void isApiKeyConfigured_returnsTrueWhenKeyPresent() {
        assertTrue(geminiAiService.isApiKeyConfigured());
    }

    @Test
    void isApiKeyConfigured_returnsFalseWhenKeyNullOrEmpty() {
        geminiAiService.setGeminiApiKey(null);
        assertFalse(geminiAiService.isApiKeyConfigured());

        geminiAiService.setGeminiApiKey("   ");
        assertFalse(geminiAiService.isApiKeyConfigured());
    }

    @Test
    void generateContent_whenApiKeyMissing_returnsNullWithoutCallingUpstream() {
        geminiAiService.setGeminiApiKey(null);

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenPromptNullOrEmpty_returnsNull() {
        assertNull(geminiAiService.generateContent(null));
        assertNull(geminiAiService.generateContent("   "));
        mockServer.verify();
    }

    @Test
    void generateContent_whenSuccessfulResponse_returnsExtractedText() {
        String mockResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "SUMMARY: Fix null pointer in LoginController"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNotNull(result);
        assertEquals("SUMMARY: Fix null pointer in LoginController", result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenEmptyResponse_returnsNullSafely() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenMalformedMissingCandidates_returnsNullSafely() {
        String mockResponseJson = """
                {
                  "promptFeedback": {
                    "blockReason": "SAFETY"
                  }
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenMalformedMissingPartsOrText_returnsNullSafely() {
        String mockResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": []
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenHttp400BadRequest_returnsNullGracefully() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\": {\"message\": \"Invalid argument\"}}"));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenHttp401Unauthorized_returnsNullGracefully() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\": {\"message\": \"API key not valid\"}}"));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenHttp403Forbidden_returnsNullGracefully() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"error\": {\"message\": \"User location not supported\"}}"));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenHttp404NotFound_returnsNullGracefully() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"error\": {\"message\": \"Model not found\"}}"));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenHttp429TooManyRequests_returnsNullGracefully() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\": {\"message\": \"Resource has been exhausted\"}}"));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenHttp500InternalServerError_returnsNullGracefully() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("{\"error\": {\"message\": \"Internal server error\"}}"));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenHttp503ServiceUnavailable_returnsNullGracefully() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\": {\"message\": \"Service unavailable\"}}"));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void generateContent_whenTimeoutOrNetworkFailure_returnsNullGracefully() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyFakeTestKey123456"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        String result = geminiAiService.generateContent("Analyze this issue");

        assertNull(result);
        mockServer.verify();
    }

    @Test
    void extractTextFromResponse_validatesVariousMalformedInputs() {
        assertNull(geminiAiService.extractTextFromResponse(null));
        assertNull(geminiAiService.extractTextFromResponse(Collections.emptyMap()));
        assertNull(geminiAiService.extractTextFromResponse(Map.of("candidates", List.of())));
        assertNull(geminiAiService.extractTextFromResponse(Map.of("candidates", List.of("not-a-map"))));
        assertNull(geminiAiService.extractTextFromResponse(Map.of("candidates", List.of(Map.of()))));
        assertNull(geminiAiService.extractTextFromResponse(Map.of("candidates", List.of(Map.of("content", "not-a-map")))));
        assertNull(geminiAiService.extractTextFromResponse(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of()))))));
        assertNull(geminiAiService.extractTextFromResponse(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of("not-a-map")))))));
        assertNull(geminiAiService.extractTextFromResponse(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", ""))))))));
        assertNull(geminiAiService.extractTextFromResponse(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", "   "))))))));
    }

    @Test
    void sanitize_redactsSecrets() {
        assertEquals("", geminiAiService.sanitize(null));

        String input1 = "Calling URL with key=AIzaSyFakeTestKey123456 in query string";
        String sanitized1 = geminiAiService.sanitize(input1);
        assertFalse(sanitized1.contains("AIzaSyFakeTestKey123456"));
        assertTrue(sanitized1.contains("[REDACTED]"));

        String input2 = "Header Authorization: Bearer secret_jwt_token_here";
        String sanitized2 = geminiAiService.sanitize(input2);
        assertFalse(sanitized2.contains("secret_jwt_token_here"));
        assertTrue(sanitized2.contains("[REDACTED]"));
    }
}
