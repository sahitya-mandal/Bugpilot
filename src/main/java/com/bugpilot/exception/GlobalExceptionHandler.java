package com.bugpilot.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("status", HttpStatus.NOT_FOUND.value());
        error.put("message", ex.getMessage());

        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubApiException(GitHubApiException ex) {

        Map<String, Object> error = new LinkedHashMap<>();

        if (ex.isRateLimit()) {
            error.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
            error.put("message", ex.getMessage());
            if (ex.getStatusCode() > 0) {
                error.put("githubStatus", ex.getStatusCode());
            }
            if (ex.getRateLimitRemaining() != null) {
                error.put("rateLimitRemaining", ex.getRateLimitRemaining());
            }
            if (ex.getRateLimitReset() != null) {
                error.put("rateLimitReset", ex.getRateLimitReset());
            }
            if (ex.getRetryAfter() != null) {
                error.put("retryAfter", ex.getRetryAfter());
            }

            return new ResponseEntity<>(error, HttpStatus.TOO_MANY_REQUESTS);
        }

        error.put("status", HttpStatus.BAD_GATEWAY.value());
        error.put("message", ex.getMessage());
        if (ex.getStatusCode() > 0) {
            error.put("githubStatus", ex.getStatusCode());
        }

        return new ResponseEntity<>(error, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {

        return new ResponseEntity<>(
                ex.getMessage(),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("status", HttpStatus.CONFLICT.value());
        error.put("message", ex.getMessage());

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("status", HttpStatus.UNAUTHORIZED.value());
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Unauthorized");

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return handleAuthenticationException(ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("status", HttpStatus.FORBIDDEN.value());
        error.put("message", "Access denied: insufficient permissions");

        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ConcurrentSyncException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentSyncException(ConcurrentSyncException ex) {

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("status", HttpStatus.CONFLICT.value());
        error.put("message", ex.getMessage());
        if (ex.getActiveJobId() != null) {
            error.put("activeJobId", ex.getActiveJobId());
        }

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex) {

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("status", HttpStatus.CONFLICT.value());
        error.put("message", "A concurrent conflict occurred while processing the request.");

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled server exception: {}", sanitize(ex.getMessage()), ex);

        Map<String, Object> error = new HashMap<>();
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("message", "An unexpected error occurred");

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = text;
        sanitized = sanitized.replaceAll("(?i)Bearer\\s+[A-Za-z0-9_\\-\\.]+", "Bearer [REDACTED]");
        sanitized = sanitized.replaceAll("ghp_[A-Za-z0-9]+", "[REDACTED]");
        sanitized = sanitized.replaceAll("github_pat_[A-Za-z0-9_]+", "[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)password\\s*=\\s*[^&\\s\"'>]+", "password=[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)key=[^&\\s\"'>]+", "key=[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)Authorization\\s*:\\s*[^\\r\\n,;]+", "Authorization: [REDACTED]");
        return sanitized;
    }
}
