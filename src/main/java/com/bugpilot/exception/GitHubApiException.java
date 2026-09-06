package com.bugpilot.exception;

public class GitHubApiException extends RuntimeException {

    private final int statusCode;
    private final boolean rateLimit;
    private final Integer rateLimitRemaining;
    private final Long rateLimitReset;
    private final Long retryAfter;

    public GitHubApiException(String message) {
        this(message, 502, false, null, null, null, null);
    }

    public GitHubApiException(String message, int statusCode) {
        this(message, statusCode, statusCode == 429, null, null, null, null);
    }

    public GitHubApiException(String message, Throwable cause) {
        this(message, 502, false, null, null, null, cause);
    }

    public GitHubApiException(String message, int statusCode, Throwable cause) {
        this(message, statusCode, statusCode == 429, null, null, null, cause);
    }

    public GitHubApiException(String message, int statusCode, boolean rateLimit,
                              Integer rateLimitRemaining, Long rateLimitReset, Long retryAfter) {
        this(message, statusCode, rateLimit, rateLimitRemaining, rateLimitReset, retryAfter, null);
    }

    public GitHubApiException(String message, int statusCode, boolean rateLimit,
                              Integer rateLimitRemaining, Long rateLimitReset, Long retryAfter, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.rateLimit = rateLimit;
        this.rateLimitRemaining = rateLimitRemaining;
        this.rateLimitReset = rateLimitReset;
        this.retryAfter = retryAfter;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getGithubStatus() {
        return statusCode;
    }

    public boolean isRateLimit() {
        return rateLimit;
    }

    public boolean isRateLimited() {
        return rateLimit;
    }

    public Integer getRateLimitRemaining() {
        return rateLimitRemaining;
    }

    public Long getRateLimitReset() {
        return rateLimitReset;
    }

    public Long getRetryAfter() {
        return retryAfter;
    }

    public boolean isUnauthorized() {
        return statusCode == 401;
    }

    public boolean isForbidden() {
        return statusCode == 403;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }
}
