package com.bugpilot.service;

import com.bugpilot.dto.RepositoryResponse;
import com.bugpilot.entity.*;
import com.bugpilot.enums.ActivityType;
import com.bugpilot.enums.IssueState;
import com.bugpilot.enums.PullRequestState;
import com.bugpilot.exception.GitHubApiException;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    @Value("${github.api.url:https://api.github.com}")
    private String githubApiUrl;

    @Value("${github.token:}")
    private String githubToken;

    @Value("${github.api.connect-timeout:10000}")
    private int connectTimeoutMs = 10000;

    @Value("${github.api.read-timeout:30000}")
    private int readTimeoutMs = 30000;

    @Value("${github.api.max-retries:2}")
    private int maxRetries = 2;

    @Value("${github.api.retry-backoff-ms:500}")
    private long retryBackoffMs = 500;

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private Sleeper sleeper = Thread::sleep;

    private final RepoRepository repoRepository;
    private final IssueRepository issueRepository;
    private final PullRequestRepository pullRequestRepository;
    private final CommitRepository commitRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final RepositoryService repositoryService;

    private RestClient.Builder restClientBuilder;
    private RestClient restClient;

    public GitHubService(RepoRepository repoRepository,
                         IssueRepository issueRepository,
                         PullRequestRepository pullRequestRepository,
                         CommitRepository commitRepository,
                         ActivityRepository activityRepository,
                         UserRepository userRepository,
                         RepositoryService repositoryService) {
        this.repoRepository = repoRepository;
        this.issueRepository = issueRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.commitRepository = commitRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.repositoryService = repositoryService;
    }

    void setRestClientBuilder(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
        this.restClient = null;
    }

    void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
        this.restClient = null;
    }

    void setGithubApiUrl(String githubApiUrl) {
        this.githubApiUrl = githubApiUrl;
        this.restClient = null;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.restClient = null;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
        this.restClient = null;
    }

    private static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGES = 100;

    private int pageSize = DEFAULT_PAGE_SIZE;

    void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getPageSize() {
        return this.pageSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    void setSleeper(Sleeper sleeper) {
        this.sleeper = sleeper != null ? sleeper : Thread::sleep;
    }

    long calculateBackoff(int retriesDone) {
        long base = retryBackoffMs * (1L << retriesDone);
        long jitter = ThreadLocalRandom.current().nextLong(0, 101);
        return Math.min(base + jitter, 2000L);
    }

    boolean isRetryableStatus(GitHubApiException e) {
        if (e == null) {
            return false;
        }
        int status = e.getStatusCode();
        if (status == 502 || status == 503 || status == 504) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof ResourceAccessException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    <T> T executeWithRetry(String operationDescription, Supplier<T> operation) {
        int attempt = 0;
        int rateLimitRetries = 0;
        GitHubApiException lastException = null;

        while (true) {
            attempt++;
            try {
                return operation.get();
            } catch (GitHubApiException e) {
                lastException = e;

                // Special bounded secondary rate-limit retry
                if (e.isRateLimit()) {
                    Long retryAfter = e.getRetryAfter();
                    if (rateLimitRetries == 0 && retryAfter != null && retryAfter > 0 && retryAfter <= 3) {
                        rateLimitRetries++;
                        long sleepMs = retryAfter * 1000L;
                        log.warn("Secondary rate limit hit for {}. Backing off for {}s according to Retry-After header (attempt {}/2)",
                                operationDescription, retryAfter, attempt);
                        try {
                            sleeper.sleep(sleepMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Retry sleep interrupted for {}: aborting retries", operationDescription);
                            throw lastException;
                        }
                        continue;
                    }
                    // Primary rate limit or retryAfter missing / > 3s or already retried once -> fail immediately
                    throw e;
                }

                // Permanent non-retryable errors (400, 401, 403 permissions, 404, etc.)
                if (!isRetryableStatus(e)) {
                    throw e;
                }

                // Transient failure (502, 503, 504 or network timeout)
                int retriesDone = attempt - 1;
                if (retriesDone >= maxRetries) {
                    log.error("Exhausted retries ({} attempts) for {}: {}", attempt, operationDescription, sanitize(e.getMessage()));
                    throw e;
                }

                long backoffMs = calculateBackoff(retriesDone);
                log.warn("Transient failure (status {}) on attempt {} for {}. Retrying in {}ms... Error: {}",
                        e.getStatusCode(), attempt, operationDescription, backoffMs, sanitize(e.getMessage()));
                try {
                    sleeper.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry sleep interrupted for {}: aborting retries", operationDescription);
                    throw lastException;
                }
            } catch (Exception e) {
                // Non-GitHubApiException must NEVER be retried
                throw e;
            }
        }
    }

    public String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = text;
        if (githubToken != null && !githubToken.trim().isEmpty()) {
            sanitized = sanitized.replace(githubToken.trim(), "[REDACTED]");
        }
        sanitized = sanitized.replaceAll("(?i)Bearer\\s+[A-Za-z0-9_\\-\\.]+", "Bearer [REDACTED]");
        sanitized = sanitized.replaceAll("ghp_[A-Za-z0-9]+", "[REDACTED]");
        sanitized = sanitized.replaceAll("github_pat_[A-Za-z0-9_]+", "[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)Authorization\\s*:\\s*[^\\r\\n,;]+", "Authorization: [REDACTED]");
        return sanitized;
    }

    private Integer parseIntegerHeader(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongHeader(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readBodySafely(ClientHttpResponse response) {
        try {
            if (response.getBody() != null) {
                return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    void handleStatusError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int rawStatus = response.getStatusCode().value();
        String path = request.getURI() != null ? request.getURI().getPath() : "unknown";
        String safePath = sanitize(path);

        HttpHeaders headers = response.getHeaders();
        Integer rateLimitRemaining = parseIntegerHeader(headers.getFirst("X-RateLimit-Remaining"));
        Long rateLimitReset = parseLongHeader(headers.getFirst("X-RateLimit-Reset"));
        Long retryAfter = parseLongHeader(headers.getFirst("Retry-After"));

        boolean isRateLimit = false;
        if (rawStatus == 429) {
            isRateLimit = true;
        } else if (rawStatus == 403) {
            if (rateLimitRemaining != null && rateLimitRemaining == 0) {
                isRateLimit = true;
            } else if (retryAfter != null && retryAfter > 0) {
                isRateLimit = true;
            } else if (rateLimitRemaining == null) {
                String body = readBodySafely(response);
                if (body != null && (body.toLowerCase().contains("rate limit") || body.toLowerCase().contains("rate_limit"))) {
                    isRateLimit = true;
                }
            }
        }

        if (isRateLimit) {
            log.error("GitHub API rate limit exceeded ({}) for path: {}. Remaining: {}, Reset: {}, Retry-After: {}",
                    rawStatus, safePath, rateLimitRemaining, rateLimitReset, retryAfter);
            throw new GitHubApiException("GitHub API rate limit exceeded", rawStatus, true,
                    rateLimitRemaining, rateLimitReset, retryAfter);
        }

        if (rawStatus == 401) {
            log.error("GitHub API authentication failed (401) for path: {}", safePath);
            throw new GitHubApiException("GitHub API authentication failed: Invalid or expired GitHub credentials", 401);
        } else if (rawStatus == 403) {
            log.error("GitHub API access forbidden (403) for path: {}", safePath);
            throw new GitHubApiException("GitHub API access forbidden: Insufficient permissions", 403);
        } else if (rawStatus == 404) {
            log.error("GitHub resource not found (404) for path: {}", safePath);
            throw new GitHubApiException("GitHub repository or resource not found: " + safePath, 404);
        } else {
            String statusText = sanitize(response.getStatusText());
            log.error("GitHub API error ({}) for path: {}", rawStatus, safePath);
            throw new GitHubApiException("GitHub API error (" + rawStatus + "): " + statusText, rawStatus);
        }
    }

    ClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    synchronized RestClient getRestClient() {
        if (this.restClient == null) {
            this.restClient = buildRestClient();
        }
        return this.restClient;
    }

    private RestClient buildRestClient() {
        RestClient.Builder builder = (this.restClientBuilder != null)
                ? this.restClientBuilder
                : RestClient.builder().requestFactory(createRequestFactory());

        builder.baseUrl(githubApiUrl != null && !githubApiUrl.trim().isEmpty() ? githubApiUrl : "https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("User-Agent", "BugPilot-Application");

        if (githubToken != null && !githubToken.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken.trim());
        }

        builder.defaultStatusHandler(HttpStatusCode::isError, (req, res) -> handleStatusError(req, res));

        return builder.build();
    }

    RestClient createRestClient() {
        return getRestClient();
    }

    public Map<String, Object> fetchRepositoryMetadata(String owner, String repoName) {
        return executeWithRetry("fetchRepositoryMetadata " + owner + "/" + repoName, () -> {
            RestClient client = getRestClient();

            Map<String, Object> repoData;
            try {
                repoData = client.get()
                        .uri("/repos/{owner}/{repo}", owner, repoName)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (GitHubApiException e) {
                throw e;
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                log.error("Failed to connect to GitHub repository {}/{}: {}", owner, repoName, safeMsg);
                throw new GitHubApiException("Failed to connect to GitHub repository " + owner + "/" + repoName + ": " + safeMsg, 502, e);
            }

            if (repoData == null) {
                log.error("Empty response from GitHub for repository {}/{}", owner, repoName);
                throw new GitHubApiException("Empty response from GitHub for repository " + owner + "/" + repoName, 502);
            }
            return repoData;
        });
    }

    public RepositoryResponse importRepository(String owner, String repoName, String userEmail) {
        Map<String, Object> repoData = fetchRepositoryMetadata(owner, repoName);

        User user = null;
        if (userEmail != null) {
            user = userRepository.findFirstByEmailOrderByIdDesc(userEmail)
                    .or(() -> userRepository.findByEmail(userEmail))
                    .orElse(null);
        }

        Optional<Repository> existing = repoRepository.findByOwnerAndName(owner, repoName);
        if (existing.isPresent()) {
            Repository existingRepo = existing.get();
            if (existingRepo.getUser() != null && (user == null || !existingRepo.getUser().getId().equals(user.getId()))) {
                throw new AccessDeniedException("Access denied: Repository is already registered by another user");
            }
        }

        Repository repository = existing.orElse(new Repository());

        repository.setOwner(owner);
        repository.setName(repoName);
        repository.setFullName((String) repoData.get("full_name"));
        repository.setDescription((String) repoData.get("description"));
        repository.setHtmlUrl((String) repoData.get("html_url"));
        repository.setDefaultBranch((String) repoData.get("default_branch"));

        if (repoData.get("id") != null) {
            repository.setGithubId(((Number) repoData.get("id")).longValue());
        }
        if (repoData.get("open_issues_count") != null) {
            repository.setOpenIssuesCount(((Number) repoData.get("open_issues_count")).intValue());
        }
        if (repoData.get("forks_count") != null) {
            repository.setForksCount(((Number) repoData.get("forks_count")).intValue());
        }
        if (repoData.get("stargazers_count") != null) {
            repository.setStargazersCount(((Number) repoData.get("stargazers_count")).intValue());
        }

        repository.setSyncedAt(LocalDateTime.now());
        if (user != null) {
            repository.setUser(user);
        }

        Repository savedRepo = repoRepository.save(repository);

        // Import issues, PRs, and commits
        importIssues(savedRepo);
        importPullRequests(savedRepo);
        importCommits(savedRepo);

        String actor = user != null ? user.getName() : "System";
        activityRepository.save(new Activity(savedRepo, ActivityType.ISSUE_CREATED,
                "Imported GitHub repository " + savedRepo.getFullName(), actor));

        return repositoryService.mapToResponse(savedRepo);
    }

    public RepositoryResponse syncRepository(Long repositoryId, String userEmail) {
        Repository repository = repositoryService.getRepositoryEntityForUser(repositoryId, userEmail);

        return importRepository(repository.getOwner(), repository.getName(), userEmail);
    }

    public List<Map<String, Object>> fetchIssuesPage(String owner, String repoName, int page, int perPage) {
        return executeWithRetry("fetchIssuesPage " + owner + "/" + repoName + " (page " + page + ")", () -> {
            RestClient client = getRestClient();
            try {
                return client.get()
                        .uri("/repos/{owner}/{repo}/issues?state=all&per_page={perPage}&page={page}",
                                owner, repoName, perPage, page)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            } catch (GitHubApiException e) {
                log.error("GitHub API error while importing issues page {} for {}/{}: {}",
                        page, owner, repoName, e.getMessage());
                throw e;
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                log.error("Failed to retrieve issues from GitHub for {}/{} (page {}): {}",
                        owner, repoName, page, safeMsg);
                throw new GitHubApiException("Failed to retrieve issues from GitHub for " + owner + "/" + repoName + ": " + safeMsg, 502, e);
            }
        });
    }

    private void importIssues(Repository repository) {
        int page = 1;
        int totalImported = 0;

        while (page <= MAX_PAGES) {
            log.info("Fetching issues page {} for {}/{}", page, repository.getOwner(), repository.getName());

            List<Map<String, Object>> items = fetchIssuesPage(repository.getOwner(), repository.getName(), page, pageSize);

            if (items == null || items.isEmpty()) {
                log.debug("No more issues found for {}/{} at page {}", repository.getOwner(), repository.getName(), page);
                break;
            }

            try {
                for (Map<String, Object> item : items) {
                    // Skip pull requests returned by issues endpoint
                    if (item.containsKey("pull_request") && item.get("pull_request") != null) {
                        continue;
                    }

                    Integer number = ((Number) item.get("number")).intValue();
                    Issue issue = issueRepository.findByRepositoryIdAndNumber(repository.getId(), number)
                            .orElse(new Issue());

                    issue.setRepository(repository);
                    issue.setNumber(number);
                    if (item.get("id") != null) {
                        issue.setGithubId(((Number) item.get("id")).longValue());
                    }
                    issue.setTitle((String) item.get("title"));
                    issue.setBody((String) item.get("body"));
                    issue.setHtmlUrl((String) item.get("html_url"));

                    String stateStr = (String) item.get("state");
                    issue.setState("closed".equalsIgnoreCase(stateStr) ? IssueState.CLOSED : IssueState.OPEN);

                    if (item.get("user") instanceof Map<?, ?> userMap) {
                        issue.setAuthor((String) userMap.get("login"));
                    }

                    issue.setGithubCreatedAt(parseDateTime((String) item.get("created_at")));
                    issue.setGithubUpdatedAt(parseDateTime((String) item.get("updated_at")));
                    issue.setGithubClosedAt(parseDateTime((String) item.get("closed_at")));

                    issueRepository.save(issue);
                    totalImported++;
                }
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                log.error("Failed to parse and persist issues for {}/{}: {}", repository.getOwner(), repository.getName(), safeMsg);
                throw new GitHubApiException("Failed to process issues for repository " + repository.getFullName() + ": " + safeMsg, 502, e);
            }

            if (items.size() < pageSize) {
                break;
            }

            page++;
        }

        if (page > MAX_PAGES) {
            log.warn("Reached maximum pagination limit ({}) for issues on {}/{}", MAX_PAGES, repository.getOwner(), repository.getName());
        }

        log.info("Finished importing issues for {}/{}: imported {} issues across {} page(s)",
                repository.getOwner(), repository.getName(), totalImported, Math.min(page, MAX_PAGES));
    }

    public List<Map<String, Object>> fetchPullRequestsPage(String owner, String repoName, int page, int perPage) {
        return executeWithRetry("fetchPullRequestsPage " + owner + "/" + repoName + " (page " + page + ")", () -> {
            RestClient client = getRestClient();
            try {
                return client.get()
                        .uri("/repos/{owner}/{repo}/pulls?state=all&per_page={perPage}&page={page}",
                                owner, repoName, perPage, page)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            } catch (GitHubApiException e) {
                log.error("GitHub API error while importing pull requests page {} for {}/{}: {}",
                        page, owner, repoName, e.getMessage());
                throw e;
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                log.error("Failed to retrieve pull requests from GitHub for {}/{} (page {}): {}",
                        owner, repoName, page, safeMsg);
                throw new GitHubApiException("Failed to retrieve pull requests from GitHub for " + owner + "/" + repoName + ": " + safeMsg, 502, e);
            }
        });
    }

    public Map<String, Object> fetchPullRequestDetails(String owner, String repoName, Integer number) {
        return executeWithRetry("fetchPullRequestDetails " + owner + "/" + repoName + " #" + number, () -> {
            RestClient client = getRestClient();
            log.debug("Fetching diff statistics for PR #{} for {}/{}", number, owner, repoName);
            Map<String, Object> detailData;
            try {
                detailData = client.get()
                        .uri("/repos/{owner}/{repo}/pulls/{pullNumber}", owner, repoName, number)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (GitHubApiException e) {
                log.error("GitHub API error while fetching PR #{} detail for {}/{}: {}",
                        number, owner, repoName, e.getMessage());
                throw e;
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                log.error("Failed to fetch PR #{} detail from GitHub for {}/{}: {}",
                        number, owner, repoName, safeMsg);
                throw new GitHubApiException("Failed to fetch PR #" + number + " detail from GitHub for " + owner + "/" + repoName + ": " + safeMsg, 502, e);
            }

            if (detailData == null) {
                log.error("Empty response from GitHub for PR #{} detail on {}/{}", number, owner, repoName);
                throw new GitHubApiException("Empty response from GitHub for PR #" + number + " detail on " + owner + "/" + repoName, 502);
            }
            return detailData;
        });
    }

    private void applyPullRequestDetails(Repository repository, PullRequest pr, Integer number) {
        Map<String, Object> detailData = fetchPullRequestDetails(repository.getOwner(), repository.getName(), number);

        if (detailData.get("additions") instanceof Number additions) {
            pr.setAdditions(additions.intValue());
        } else {
            pr.setAdditions(0);
        }

        if (detailData.get("deletions") instanceof Number deletions) {
            pr.setDeletions(deletions.intValue());
        } else {
            pr.setDeletions(0);
        }

        if (detailData.get("changed_files") instanceof Number changedFiles) {
            pr.setChangedFiles(changedFiles.intValue());
        } else {
            pr.setChangedFiles(0);
        }
    }

    public boolean shouldFetchPullRequestDetails(PullRequest existingPr, String rawUpdatedAtStr) {
        if (existingPr == null) {
            return true;
        }

        // If it's a freshly instantiated empty PullRequest (transient entity)
        if (existingPr.getId() == null && existingPr.getGithubUpdatedAt() == null && existingPr.getTitle() == null) {
            return true;
        }

        // If diff statistics are missing or null
        if (existingPr.getAdditions() == null || existingPr.getDeletions() == null || existingPr.getChangedFiles() == null) {
            return true;
        }

        // If GitHub list updated_at is missing, blank, or fails parsing
        if (rawUpdatedAtStr == null || rawUpdatedAtStr.trim().isEmpty()) {
            return true;
        }
        LocalDateTime githubUpdatedAt = parseDateTime(rawUpdatedAtStr);
        if (githubUpdatedAt == null) {
            return true;
        }

        // If persisted timestamp is missing
        LocalDateTime persistedUpdatedAt = existingPr.getGithubUpdatedAt();
        if (persistedUpdatedAt == null) {
            return true;
        }

        // Skip only if timestamps match exactly
        return !persistedUpdatedAt.equals(githubUpdatedAt);
    }

    private void importPullRequests(Repository repository) {
        int page = 1;
        int totalImported = 0;

        Map<Integer, PullRequest> existingPrMap = new HashMap<>();
        try {
            List<PullRequest> existingList = pullRequestRepository.findByRepositoryId(repository.getId());
            if (existingList != null) {
                for (PullRequest p : existingList) {
                    if (p.getNumber() != null) {
                        existingPrMap.put(p.getNumber(), p);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        while (page <= MAX_PAGES) {
            log.info("Fetching pull requests page {} for {}/{}", page, repository.getOwner(), repository.getName());

            List<Map<String, Object>> items = fetchPullRequestsPage(repository.getOwner(), repository.getName(), page, pageSize);

            if (items == null || items.isEmpty()) {
                log.debug("No more pull requests found for {}/{} at page {}", repository.getOwner(), repository.getName(), page);
                break;
            }

            try {
                for (Map<String, Object> item : items) {
                    Integer number = ((Number) item.get("number")).intValue();
                    PullRequest pr = existingPrMap.get(number);
                    if (pr == null) {
                        pr = pullRequestRepository.findByRepositoryIdAndNumber(repository.getId(), number)
                                .orElse(new PullRequest());
                    }

                    String rawUpdatedAtStr = (String) item.get("updated_at");
                    LocalDateTime githubUpdatedAt = parseDateTime(rawUpdatedAtStr);

                    boolean needDetails = shouldFetchPullRequestDetails(pr, rawUpdatedAtStr);

                    pr.setRepository(repository);
                    pr.setNumber(number);
                    if (item.get("id") != null) {
                        pr.setGithubId(((Number) item.get("id")).longValue());
                    }
                    pr.setTitle((String) item.get("title"));
                    pr.setBody((String) item.get("body"));
                    pr.setHtmlUrl((String) item.get("html_url"));

                    Boolean draft = (Boolean) item.get("draft");
                    pr.setDraft(draft != null && draft);

                    String stateStr = (String) item.get("state");
                    if ("closed".equalsIgnoreCase(stateStr)) {
                        if (item.get("merged_at") != null) {
                            pr.setState(PullRequestState.MERGED);
                        } else {
                            pr.setState(PullRequestState.CLOSED);
                        }
                    } else {
                        pr.setState(PullRequestState.OPEN);
                    }

                    if (item.get("user") instanceof Map<?, ?> userMap) {
                        pr.setAuthor((String) userMap.get("login"));
                    }
                    if (item.get("head") instanceof Map<?, ?> headMap) {
                        pr.setSourceBranch((String) headMap.get("ref"));
                    }
                    if (item.get("base") instanceof Map<?, ?> baseMap) {
                        pr.setTargetBranch((String) baseMap.get("ref"));
                    }

                    pr.setGithubCreatedAt(parseDateTime((String) item.get("created_at")));
                    pr.setGithubUpdatedAt(githubUpdatedAt);
                    pr.setGithubClosedAt(parseDateTime((String) item.get("closed_at")));
                    pr.setGithubMergedAt(parseDateTime((String) item.get("merged_at")));

                    if (needDetails) {
                        applyPullRequestDetails(repository, pr, number);
                    } else {
                        log.debug("Skipping diff statistics detail call for unchanged PR #{} on {}/{}",
                                number, repository.getOwner(), repository.getName());
                    }

                    PullRequest saved = pullRequestRepository.save(pr);
                    existingPrMap.put(number, saved);
                    totalImported++;
                }
            } catch (GitHubApiException e) {
                throw e;
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                log.error("Failed to parse and persist pull requests for {}/{}: {}", repository.getOwner(), repository.getName(), safeMsg);
                throw new GitHubApiException("Failed to process pull requests for repository " + repository.getFullName() + ": " + safeMsg, 502, e);
            }

            if (items.size() < pageSize) {
                break;
            }

            page++;
        }

        if (page > MAX_PAGES) {
            log.warn("Reached maximum pagination limit ({}) for pull requests on {}/{}", MAX_PAGES, repository.getOwner(), repository.getName());
        }

        log.info("Finished importing pull requests for {}/{}: imported {} pull requests across {} page(s)",
                repository.getOwner(), repository.getName(), totalImported, Math.min(page, MAX_PAGES));
    }

    public List<Map<String, Object>> fetchCommitsPage(String owner, String repoName, int page, int perPage) {
        return executeWithRetry("fetchCommitsPage " + owner + "/" + repoName + " (page " + page + ")", () -> {
            RestClient client = getRestClient();
            try {
                return client.get()
                        .uri("/repos/{owner}/{repo}/commits?per_page={perPage}&page={page}",
                                owner, repoName, perPage, page)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            } catch (GitHubApiException e) {
                log.error("GitHub API error while importing commits page {} for {}/{}: {}",
                        page, owner, repoName, e.getMessage());
                throw e;
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                log.error("Failed to retrieve commits from GitHub for {}/{} (page {}): {}",
                        owner, repoName, page, safeMsg);
                throw new GitHubApiException("Failed to retrieve commits from GitHub for " + owner + "/" + repoName + ": " + safeMsg, 502, e);
            }
        });
    }

    private void importCommits(Repository repository) {
        int page = 1;
        int totalImported = 0;

        while (page <= MAX_PAGES) {
            log.info("Fetching commits page {} for {}/{}", page, repository.getOwner(), repository.getName());

            List<Map<String, Object>> items = fetchCommitsPage(repository.getOwner(), repository.getName(), page, pageSize);

            if (items == null || items.isEmpty()) {
                log.debug("No more commits found for {}/{} at page {}", repository.getOwner(), repository.getName(), page);
                break;
            }

            try {
                for (Map<String, Object> item : items) {
                    String sha = (String) item.get("sha");
                    if (sha == null) continue;

                    Commit commit = commitRepository.findByRepositoryIdAndSha(repository.getId(), sha)
                            .orElse(new Commit());

                    commit.setRepository(repository);
                    commit.setSha(sha);
                    commit.setHtmlUrl((String) item.get("html_url"));

                    if (item.get("commit") instanceof Map<?, ?> cMap) {
                        commit.setMessage((String) cMap.get("message"));

                        if (cMap.get("author") instanceof Map<?, ?> authorMap) {
                            commit.setAuthorName((String) authorMap.get("name"));
                            commit.setAuthorEmail((String) authorMap.get("email"));
                            commit.setCommittedAt(parseDateTime((String) authorMap.get("date")));
                        }
                    }

                    commitRepository.save(commit);
                    totalImported++;
                }
            } catch (Exception e) {
                String safeMsg = sanitize(e.getMessage());
                log.error("Failed to parse and persist commits for {}/{}: {}", repository.getOwner(), repository.getName(), safeMsg);
                throw new GitHubApiException("Failed to process commits for repository " + repository.getFullName() + ": " + safeMsg, 502, e);
            }

            if (items.size() < pageSize) {
                break;
            }

            page++;
        }

        if (page > MAX_PAGES) {
            log.warn("Reached maximum pagination limit ({}) for commits on {}/{}", MAX_PAGES, repository.getOwner(), repository.getName());
        }

        log.info("Finished importing commits for {}/{}: imported {} commits across {} page(s)",
                repository.getOwner(), repository.getName(), totalImported, Math.min(page, MAX_PAGES));
    }

    LocalDateTime parseDateTime(String isoString) {
        if (isoString == null || isoString.trim().isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(isoString).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse ISO datetime string '{}': {}", sanitize(isoString), sanitize(e.getMessage()));
            return null;
        }
    }
}
