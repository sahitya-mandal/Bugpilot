package com.bugpilot.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleUserNotFound() {
        UserNotFoundException ex = new UserNotFoundException(5L);
        ResponseEntity<String> response = handler.handleUserNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("User with id 5 not found.", response.getBody());
    }

    @Test
    void testHandleEmailAlreadyExists() {
        EmailAlreadyExistsException ex = new EmailAlreadyExistsException("duplicate@example.com");
        ResponseEntity<Map<String, Object>> response = handler.handleEmailAlreadyExists(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("Email duplicate@example.com is already registered.", response.getBody().get("message"));
    }

    @Test
    void testHandleBadCredentials() {
        BadCredentialsException ex = new BadCredentialsException("Invalid email or password");
        ResponseEntity<Map<String, Object>> response = handler.handleBadCredentials(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().get("status"));
        assertEquals("Invalid email or password", response.getBody().get("message"));
    }

    @Test
    void testHandleGitHubApiException() {
        GitHubApiException ex = new GitHubApiException("GitHub API authentication failed: Invalid or expired GitHub credentials", 401);
        ResponseEntity<Map<String, Object>> response = handler.handleGitHubApiException(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(502, response.getBody().get("status"));
        assertEquals(401, response.getBody().get("githubStatus"));
        assertEquals("GitHub API authentication failed: Invalid or expired GitHub credentials", response.getBody().get("message"));
    }

    @Test
    void testHandleGitHubApiException_whenRateLimit429WithAllHeaders() {
        GitHubApiException ex = new GitHubApiException("GitHub API rate limit exceeded", 429, true, 0, 1741234567L, 60L);
        ResponseEntity<Map<String, Object>> response = handler.handleGitHubApiException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(429, response.getBody().get("status"));
        assertEquals("GitHub API rate limit exceeded", response.getBody().get("message"));
        assertEquals(429, response.getBody().get("githubStatus"));
        assertEquals(0, response.getBody().get("rateLimitRemaining"));
        assertEquals(1741234567L, response.getBody().get("rateLimitReset"));
        assertEquals(60L, response.getBody().get("retryAfter"));
    }

    @Test
    void testHandleGitHubApiException_whenRateLimit403() {
        GitHubApiException ex = new GitHubApiException("GitHub API rate limit exceeded", 403, true, 0, 1741234567L, null);
        ResponseEntity<Map<String, Object>> response = handler.handleGitHubApiException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(429, response.getBody().get("status"));
        assertEquals("GitHub API rate limit exceeded", response.getBody().get("message"));
        assertEquals(403, response.getBody().get("githubStatus"));
        assertEquals(0, response.getBody().get("rateLimitRemaining"));
        assertEquals(1741234567L, response.getBody().get("rateLimitReset"));
        assertNull(response.getBody().get("retryAfter"));
    }

    @Test
    void testHandleGitHubApiException_whenNormal403PermissionError() {
        GitHubApiException ex = new GitHubApiException("GitHub API access forbidden: Insufficient permissions", 403);
        ResponseEntity<Map<String, Object>> response = handler.handleGitHubApiException(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(502, response.getBody().get("status"));
        assertEquals(403, response.getBody().get("githubStatus"));
        assertEquals("GitHub API access forbidden: Insufficient permissions", response.getBody().get("message"));
        assertNull(response.getBody().get("rateLimitRemaining"));
        assertNull(response.getBody().get("rateLimitReset"));
        assertNull(response.getBody().get("retryAfter"));
    }

    @Test
    void testHandleGitHubApiException_whenRateLimitMissingOptionalHeaders() {
        GitHubApiException ex = new GitHubApiException("GitHub API rate limit exceeded", 429, true, null, null, null);
        ResponseEntity<Map<String, Object>> response = handler.handleGitHubApiException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(429, response.getBody().get("status"));
        assertEquals(429, response.getBody().get("githubStatus"));
        assertNull(response.getBody().get("rateLimitRemaining"));
        assertNull(response.getBody().get("rateLimitReset"));
        assertNull(response.getBody().get("retryAfter"));
    }

    @Test
    void testHandleDataIntegrityViolation() {
        org.springframework.dao.DataIntegrityViolationException ex =
                new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint");
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("A concurrent conflict occurred while processing the request.", response.getBody().get("message"));
    }

    @Test
    void testHandleAccessDenied() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Access denied: You do not own this repository");
        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().get("status"));
        assertEquals("Access denied: insufficient permissions", response.getBody().get("message"));
    }

    @Test
    void testHandleConcurrentSyncException() {
        ConcurrentSyncException ex = new ConcurrentSyncException(42L);
        ResponseEntity<Map<String, Object>> response = handler.handleConcurrentSyncException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("A sync job is already in progress for this repository.", response.getBody().get("message"));
        assertEquals(42L, response.getBody().get("activeJobId"));
    }

    @Test
    void testHandleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Repository not found with id: 99");
        ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("Repository not found with id: 99", response.getBody().get("message"));
    }

    @Test
    void testHandleGenericException_withRuntimeException() {
        RuntimeException ex = new RuntimeException("Simulated unexpected crash: Null pointer in IssueService at line 42");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("An unexpected error occurred", response.getBody().get("message"));

        String bodyString = response.getBody().toString();
        assertFalse(bodyString.contains("Simulated unexpected crash"), "Internal message must not leak");
        assertFalse(bodyString.contains("IssueService"), "Class name must not leak");
        assertFalse(bodyString.contains("Null pointer"), "Internal details must not leak");
        assertFalse(bodyString.contains("trace"), "Stack trace must not leak");
        assertFalse(bodyString.contains("stackTrace"), "Stack trace must not leak");
        assertFalse(bodyString.contains("exception"), "Exception class must not leak");
    }

    @Test
    void testHandleGenericException_withSqlDatabaseError() {
        RuntimeException ex = new RuntimeException(
                "org.postgresql.util.PSQLException: ERROR: relation \"users\" does not exist\nPosition: 15\nSELECT * FROM users WHERE id=1");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("An unexpected error occurred", response.getBody().get("message"));

        String bodyString = response.getBody().toString();
        assertFalse(bodyString.contains("PSQLException"), "Database driver class must not leak");
        assertFalse(bodyString.contains("users"), "Table name must not leak");
        assertFalse(bodyString.contains("SELECT"), "SQL query must not leak");
        assertFalse(bodyString.contains("relation"), "Database error string must not leak");
    }

    @Test
    void testHandleGenericException_withFilesystemPath() {
        RuntimeException ex = new RuntimeException(
                "java.io.FileNotFoundException: /var/secrets/jwt_private_key.pem (Permission denied)");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("An unexpected error occurred", response.getBody().get("message"));

        String bodyString = response.getBody().toString();
        assertFalse(bodyString.contains("/var/secrets"), "Filesystem path must not leak");
        assertFalse(bodyString.contains("jwt_private_key"), "Secret path must not leak");
        assertFalse(bodyString.contains("FileNotFoundException"), "Exception class must not leak");
    }

    @Test
    void testHandleGenericException_withClassAndPackageName() {
        NullPointerException ex = new NullPointerException(
                "Cannot invoke \"com.bugpilot.entity.Repository.getOwner()\" because \"repo\" is null");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("An unexpected error occurred", response.getBody().get("message"));

        String bodyString = response.getBody().toString();
        assertFalse(bodyString.contains("com.bugpilot"), "Package name must not leak");
        assertFalse(bodyString.contains("Repository"), "Entity name must not leak");
        assertFalse(bodyString.contains("getOwner"), "Method name must not leak");
        assertFalse(bodyString.contains("NullPointerException"), "Exception class must not leak");
    }

    @Test
    void testHandleGenericException_withNullMessage() {
        NullPointerException ex = new NullPointerException(null);
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("An unexpected error occurred", response.getBody().get("message"));
    }

    @Test
    void testHandleGenericException_withSecretsInMessage() {
        RuntimeException ex = new RuntimeException(
                "Failed connecting to upstream with Bearer secret_jwt_12345 and ghp_abcdef1234567890 and key=secret_key");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("An unexpected error occurred", response.getBody().get("message"));

        String bodyString = response.getBody().toString();
        assertFalse(bodyString.contains("secret_jwt_12345"), "JWT token must not leak");
        assertFalse(bodyString.contains("ghp_abcdef1234567890"), "GitHub token must not leak");
        assertFalse(bodyString.contains("secret_key"), "API key must not leak");
    }
}
