package com.bugpilot.exception;

import com.bugpilot.security.JwtAccessDeniedHandler;
import com.bugpilot.security.JwtAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExceptionLeakageHardeningTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GlobalExceptionHandler exceptionHandler;
    private MockMvc mockMvc;

    @RestController
    static class CrashTestController {
        @GetMapping("/test/crash/runtime")
        public String crashRuntime() {
            throw new RuntimeException("ERROR: relation \"bugpilot_tbl\" does not exist at position 22 (SELECT * FROM bugpilot_tbl)");
        }

        @GetMapping("/test/crash/npe")
        public String crashNpe() {
            throw new NullPointerException("Cannot invoke com.bugpilot.service.AIAnalysisService.analyze() because prompt is null");
        }

        @GetMapping("/test/crash/filesystem")
        public String crashFilesystem() {
            throw new RuntimeException("java.io.FileNotFoundException: C:\\bugpilot\\config\\secrets.env (Access is denied)");
        }

        @GetMapping("/test/crash/tokens")
        public String crashTokens() {
            throw new RuntimeException("Upstream failed with token ghp_1234567890abcdef and Bearer eyJhbGciOiJIUzI1NiJ9");
        }
    }

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new CrashTestController())
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    // 1 & 2: Unexpected RuntimeException returns HTTP 500 with generic safe message
    @Test
    void unhandledRuntimeException_returnsHttp500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/crash/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    // 3 & 6: Internal exception message and SQL details are NOT present
    @Test
    void unhandledSqlException_doesNotLeakSqlOrInternalDetails() throws Exception {
        String responseContent = mockMvc.perform(get("/test/crash/runtime"))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseContent);
        assertEquals(500, json.get("status").asInt());
        assertEquals("An unexpected error occurred", json.get("message").asText());

        assertFalse(responseContent.contains("relation"), "SQL error text 'relation' must not leak");
        assertFalse(responseContent.contains("bugpilot_tbl"), "Table name must not leak");
        assertFalse(responseContent.contains("SELECT"), "SQL query must not leak");
        assertFalse(responseContent.contains("position 22"), "Internal position must not leak");
    }

    // 4 & 5: Stack trace and class/package names are NOT present
    @Test
    void unhandledNullPointerException_doesNotLeakClassPackageOrStackTrace() throws Exception {
        String responseContent = mockMvc.perform(get("/test/crash/npe"))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseContent);
        assertEquals(500, json.get("status").asInt());
        assertEquals("An unexpected error occurred", json.get("message").asText());

        assertFalse(responseContent.contains("com.bugpilot"), "Package name must not leak");
        assertFalse(responseContent.contains("AIAnalysisService"), "Class name must not leak");
        assertFalse(responseContent.contains("NullPointerException"), "Exception class name must not leak");
        assertFalse(responseContent.contains("stackTrace"), "Stack trace must not leak");
        assertFalse(responseContent.contains("trace"), "Stack trace must not leak");
    }

    // 7: Filesystem paths are NOT present
    @Test
    void unhandledFilesystemException_doesNotLeakPaths() throws Exception {
        String responseContent = mockMvc.perform(get("/test/crash/filesystem"))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseContent);
        assertEquals(500, json.get("status").asInt());
        assertEquals("An unexpected error occurred", json.get("message").asText());

        assertFalse(responseContent.contains("C:\\bugpilot"), "Filesystem path must not leak");
        assertFalse(responseContent.contains("secrets.env"), "File name must not leak");
        assertFalse(responseContent.contains("FileNotFoundException"), "Exception class must not leak");
    }

    // Tokens/credentials are NOT present in response
    @Test
    void unhandledExceptionWithTokens_doesNotLeakTokens() throws Exception {
        String responseContent = mockMvc.perform(get("/test/crash/tokens"))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseContent);
        assertEquals(500, json.get("status").asInt());
        assertEquals("An unexpected error occurred", json.get("message").asText());

        assertFalse(responseContent.contains("ghp_1234567890abcdef"), "GitHub token must not leak");
        assertFalse(responseContent.contains("eyJhbGciOiJIUzI1NiJ9"), "JWT token must not leak");
    }

    // 8: Known validation / resource errors remain intact
    @Test
    void resourceNotFoundException_preservesSafe404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Repository not found with id: 10");
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("Repository not found with id: 10", response.getBody().get("message"));
    }

    @Test
    void userNotFoundException_preservesSafe404() {
        UserNotFoundException ex = new UserNotFoundException(7L);
        ResponseEntity<String> response = exceptionHandler.handleUserNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("User with id 7 not found.", response.getBody());
    }

    @Test
    void emailAlreadyExistsException_preservesSafe409() {
        EmailAlreadyExistsException ex = new EmailAlreadyExistsException("user@example.com");
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleEmailAlreadyExists(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("Email user@example.com is already registered.", response.getBody().get("message"));
    }

    @Test
    void concurrentSyncException_preservesSafe409() {
        ConcurrentSyncException ex = new ConcurrentSyncException(123L);
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleConcurrentSyncException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("A sync job is already in progress for this repository.", response.getBody().get("message"));
        assertEquals(123L, response.getBody().get("activeJobId"));
    }

    @Test
    void dataIntegrityViolationException_preservesSafe409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("A concurrent conflict occurred while processing the request.", response.getBody().get("message"));
    }

    // 9: Authentication remains 401
    @Test
    void authenticationFailure_remains401() throws IOException {
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/repositories");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Bad token"));

        assertEquals(401, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\": 401"));
        assertTrue(response.getContentAsString().contains("Unauthorized"));
    }

    // 10: Authorization remains 403
    @Test
    void authorizationFailure_remains403() throws IOException {
        JwtAccessDeniedHandler accessDeniedHandler = new JwtAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("Access denied"));

        assertEquals(403, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\": 403"));
        assertTrue(response.getContentAsString().contains("Access denied"));

        // Also verify GlobalExceptionHandler.handleAccessDenied
        ResponseEntity<Map<String, Object>> entity = exceptionHandler.handleAccessDenied(new AccessDeniedException("Access denied"));
        assertEquals(HttpStatus.FORBIDDEN, entity.getStatusCode());
        assertEquals(403, entity.getBody().get("status"));
        assertEquals("Access denied: insufficient permissions", entity.getBody().get("message"));
    }

    // 11 & 12: GitHubApiException behavior remains unchanged (502 and 429)
    @Test
    void gitHubApiException_nonRateLimit_remains502() {
        GitHubApiException ex = new GitHubApiException("GitHub API upstream error", 500);
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGitHubApiException(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(502, response.getBody().get("status"));
        assertEquals(500, response.getBody().get("githubStatus"));
        assertEquals("GitHub API upstream error", response.getBody().get("message"));
    }

    @Test
    void gitHubApiException_rateLimit_remains429() {
        GitHubApiException ex = new GitHubApiException("GitHub API rate limit exceeded", 429, true, 0, 1740000000L, 120L);
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGitHubApiException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(429, response.getBody().get("status"));
        assertEquals(429, response.getBody().get("githubStatus"));
        assertEquals("GitHub API rate limit exceeded", response.getBody().get("message"));
        assertEquals(0, response.getBody().get("rateLimitRemaining"));
        assertEquals(1740000000L, response.getBody().get("rateLimitReset"));
        assertEquals(120L, response.getBody().get("retryAfter"));
    }

    // 13: Server-side logging occurs for unexpected exceptions
    @Test
    void unhandledException_triggersServerSideLoggingWithSanitizedMessage() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender =
                new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            RuntimeException ex = new RuntimeException("DB crash with Bearer secret_jwt_token_xyz and password=secret_password_abc");
            exceptionHandler.handleGenericException(ex);

            assertFalse(listAppender.list.isEmpty(), "Server-side log event must be captured");
            ch.qos.logback.classic.spi.ILoggingEvent event = listAppender.list.get(listAppender.list.size() - 1);
            assertEquals(ch.qos.logback.classic.Level.ERROR, event.getLevel(), "Must log at ERROR level");
            assertTrue(event.getFormattedMessage().contains("Unhandled server exception"), "Must indicate unhandled exception");
            assertFalse(event.getFormattedMessage().contains("secret_jwt_token_xyz"), "JWT secret must be sanitized in logs");
            assertFalse(event.getFormattedMessage().contains("secret_password_abc"), "Password must be sanitized in logs");
            assertNotNull(event.getThrowableProxy(), "Logged event must capture throwable stack trace");
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    // 14: /error configuration properties in application.properties are hardened
    @Test
    void applicationProperties_haveErrorHardeningConfigured() throws IOException {
        Properties properties = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(is, "application.properties must be on the classpath");
            properties.load(is);
        }

        assertEquals("never", properties.getProperty("server.error.include-message"));
        assertEquals("never", properties.getProperty("server.error.include-binding-errors"));
        assertEquals("never", properties.getProperty("server.error.include-stacktrace"));
        assertEquals("false", properties.getProperty("server.error.include-exception"));
    }
}
