package com.bugpilot.service;

import com.bugpilot.entity.*;
import com.bugpilot.exception.GitHubApiException;
import com.bugpilot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private RepoRepository repoRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RepositoryService repositoryService;

    private GitHubService gitHubService;

    @BeforeEach
    void setUp() {
        gitHubService = new GitHubService(
                repoRepository,
                issueRepository,
                pullRequestRepository,
                commitRepository,
                activityRepository,
                userRepository,
                repositoryService
        );
        gitHubService.setSleeper(ms -> {});
    }

    private String generateMockIssuesJson(int startNumber, int count, boolean includePr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            int num = startNumber + i;
            if (i > 0) sb.append(",");
            if (includePr && i == 0) {
                // Return a pull request inside issues response
                sb.append(String.format(
                        "{\"id\":%d,\"number\":%d,\"title\":\"PR in issues %d\",\"state\":\"open\",\"pull_request\":{\"url\":\"https://api.github.com/repos/octocat/Hello-World/pulls/%d\"},\"user\":{\"login\":\"dev%d\"},\"created_at\":\"2026-01-01T10:00:00Z\"}",
                        1000 + num, num, num, num, num
                ));
            } else {
                sb.append(String.format(
                        "{\"id\":%d,\"number\":%d,\"title\":\"Issue %d\",\"state\":\"open\",\"user\":{\"login\":\"dev%d\"},\"created_at\":\"2026-01-01T10:00:00Z\"}",
                        1000 + num, num, num, num
                ));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String generateMockPullsJson(int startNumber, int count) {
        return generateMockPullsJson(startNumber, count, null);
    }

    private String generateMockPullsJson(int startNumber, int count, String updatedAt) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            int num = startNumber + i;
            if (i > 0) sb.append(",");
            sb.append(generateMockPullItemJson(num, updatedAt));
        }
        sb.append("]");
        return sb.toString();
    }

    private String generateMockPullItemJson(int num, String updatedAt) {
        return String.format(
                "{\"id\":%d,\"number\":%d,\"title\":\"PR %d\",\"state\":\"open\",\"draft\":false,\"user\":{\"login\":\"dev%d\"},\"head\":{\"ref\":\"feat-%d\"},\"base\":{\"ref\":\"main\"},\"created_at\":\"2026-01-01T10:00:00Z\"%s}",
                2000 + num, num, num, num, num,
                (updatedAt != null ? ",\"updated_at\":\"" + updatedAt + "\"" : "")
        );
    }

    private String generateMockPullDetailJson(int number, int additions, int deletions, int changedFiles) {
        return String.format(
                "{\"id\":%d,\"number\":%d,\"title\":\"PR %d\",\"additions\":%d,\"deletions\":%d,\"changed_files\":%d}",
                2000 + number, number, number, additions, deletions, changedFiles
        );
    }

    private String generateMockCommitsJson(String prefix, int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            String sha = String.format("%s%05d", prefix, i);
            sb.append(String.format(
                    "{\"sha\":\"%s\",\"html_url\":\"https://github.com/octocat/Hello-World/commit/%s\",\"commit\":{\"message\":\"Commit %d\",\"author\":{\"name\":\"Dev %d\",\"email\":\"dev%d@example.com\",\"date\":\"2026-01-01T10:00:00Z\"}}}",
                    sha, sha, i, i, i
            ));
        }
        sb.append("]");
        return sb.toString();
    }

    @Test
    void syncRepository_whenNonOwner_throwsAccessDeniedException() {
        when(repositoryService.getRepositoryEntityForUser(1L, "seconddev@bugpilot.com"))
                .thenThrow(new AccessDeniedException("Access denied: You do not own this repository"));

        assertThrows(AccessDeniedException.class, () ->
                gitHubService.syncRepository(1L, "seconddev@bugpilot.com"));

        verify(repoRepository, never()).save(any(Repository.class));
    }

    @Test
    void importRepository_whenGitHub401Unauthorized_throwsGitHubApiExceptionWith401() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Bad credentials\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.isUnauthorized());
        assertFalse(ex.isForbidden());
        assertFalse(ex.isNotFound());
        assertTrue(ex.getMessage().contains("authentication failed"));
        server.verify();
        verify(repoRepository, never()).save(any(Repository.class));
    }

    @Test
    void importRepository_whenGitHub403Forbidden_throwsGitHubApiExceptionWith403() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"message\":\"API rate limit exceeded\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.isForbidden());
        assertFalse(ex.isUnauthorized());
        assertFalse(ex.isNotFound());
        assertTrue(ex.getMessage().contains("forbidden") || ex.getMessage().contains("rate limit"));
        server.verify();
        verify(repoRepository, never()).save(any(Repository.class));
    }

    @Test
    void importRepository_whenGitHub404NotFound_throwsGitHubApiExceptionWith404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"Not Found\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.isNotFound());
        assertFalse(ex.isUnauthorized());
        assertFalse(ex.isForbidden());
        assertTrue(ex.getMessage().contains("not found"));
        server.verify();
        verify(repoRepository, never()).save(any(Repository.class));
    }

    @Test
    void importRepository_whenGitHub500InternalError_throwsGitHubApiExceptionWith500() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("500"));
        server.verify();
        verify(repoRepository, never()).save(any(Repository.class));
    }

    @Test
    void importRepository_whenIssuesImportFails_doesNotSilentlySwallowAndThrowsException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues endpoint fails on page 1 with 500
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(500, ex.getStatusCode());
        server.verify();
        verify(activityRepository, never()).save(any(Activity.class));
    }

    @Test
    void importRepository_whenPullRequestsImportFails_doesNotSilentlySwallowAndThrowsException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues succeeds (empty list on page 1)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. Pull requests fails on page 1 with 403 Forbidden
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.isForbidden());
        server.verify();
        verify(activityRepository, never()).save(any(Activity.class));
    }

    @Test
    void importRepository_whenCommitsImportFails_doesNotSilentlySwallowAndThrowsException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. Pull requests succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 4. Commits fails on page 1 with 502 Bad Gateway (exhausts retries)
        server.expect(ExpectedCount.times(3), requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(502, ex.getStatusCode());
        server.verify();
        verify(activityRepository, never()).save(any(Activity.class));
    }

    @Test
    void sanitize_redactsSecretsAndTokens() {
        gitHubService.setGithubToken("ghp_SecretToken1234567890abcdef");

        String rawText = "Error communicating with token ghp_SecretToken1234567890abcdef and Bearer github_pat_11ABCD_XYZ using Authorization: Bearer someSecret";
        String sanitized = gitHubService.sanitize(rawText);

        assertFalse(sanitized.contains("ghp_SecretToken1234567890abcdef"));
        assertFalse(sanitized.contains("github_pat_11ABCD_XYZ"));
        assertFalse(sanitized.contains("someSecret"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }

    @Test
    void importRepository_whenTokenConfigured_doesNotExposeTokenInException() {
        String secretToken = "ghp_SecretToken1234567890abcdef";
        gitHubService.setGithubToken(secretToken);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertFalse(ex.getMessage().contains(secretToken));
        server.verify();
    }

    @Test
    void importRepository_issuesPagination_fetchesMultiplePagesAndImportsAll() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Sahitya");
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues Page 1: full page of 100 items (first item is a PR which should be excluded -> 99 issues)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockIssuesJson(1, 100, true), MediaType.APPLICATION_JSON));

        // 3. Issues Page 2: partial page of 25 items (< 100 -> stops pagination)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockIssuesJson(101, 25, false), MediaType.APPLICATION_JSON));

        // 4. Pull Requests: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 5. Commits: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();
        // 99 issues from page 1 + 25 issues from page 2 = 124 saved issues
        verify(issueRepository, times(124)).save(any(Issue.class));
        verify(activityRepository, times(1)).save(any(Activity.class));
    }

    @Test
    void importRepository_pullRequestsPagination_fetchesMultiplePagesAndImportsAll() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Sahitya");
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs Page 1: 100 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 100), MediaType.APPLICATION_JSON));

        for (int i = 1; i <= 100; i++) {
            server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/" + i))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(generateMockPullDetailJson(i, 10, 5, 2), MediaType.APPLICATION_JSON));
        }

        // 4. PRs Page 2: 15 items (< 100 -> stops pagination)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(101, 15), MediaType.APPLICATION_JSON));

        for (int i = 101; i <= 115; i++) {
            server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/" + i))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(generateMockPullDetailJson(i, 10, 5, 2), MediaType.APPLICATION_JSON));
        }

        // 5. Commits: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();
        // 100 + 15 = 115 pull requests saved
        verify(pullRequestRepository, times(115)).save(any(PullRequest.class));
        verify(activityRepository, times(1)).save(any(Activity.class));
    }

    @Test
    void importRepository_commitsPagination_fetchesMultiplePagesAndImportsAll() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Sahitya");
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 4. Commits Page 1: 100 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockCommitsJson("sha1_", 100), MediaType.APPLICATION_JSON));

        // 5. Commits Page 2: 10 items (< 100 -> stops pagination)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockCommitsJson("sha2_", 10), MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();
        // 100 + 10 = 110 commits saved
        verify(commitRepository, times(110)).save(any(Commit.class));
        verify(activityRepository, times(1)).save(any(Activity.class));
    }

    @Test
    void importRepository_whenExactPageSizeReturned_fetchesNextPageUntilEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Sahitya");
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues Page 1: exactly 100 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockIssuesJson(1, 100, false), MediaType.APPLICATION_JSON));

        // 3. Issues Page 2: 0 items (empty array -> stops pagination)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 4. PRs: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 5. Commits: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();
        verify(issueRepository, times(100)).save(any(Issue.class));
    }

    @Test
    void importRepository_repeatedSync_idempotentlyUpdatesWithoutDuplicates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Sahitya");
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        Issue existingIssue = new Issue();
        existingIssue.setId(999L);
        existingIssue.setNumber(1);
        existingIssue.setTitle("Original Title");
        when(issueRepository.findByRepositoryIdAndNumber(10L, 1)).thenReturn(Optional.of(existingIssue));

        // 1. Repo metadata
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues: returns 1 issue with number 1
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockIssuesJson(1, 1, false), MediaType.APPLICATION_JSON));

        // 3. PRs: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 4. Commits: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();
        // Existing issue updated in-place without creating a new instance
        assertEquals("Issue 1", existingIssue.getTitle());
        verify(issueRepository, times(1)).save(existingIssue);
    }

    @Test
    void importRepository_pullRequestDetailStats_persistsAdditionsDeletionsAndChangedFiles() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Sahitya");
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs list: returns PR #42
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(42, 1), MediaType.APPLICATION_JSON));

        // 4. PR #42 detail endpoint returns additions=25, deletions=10, changed_files=4
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(42, 25, 10, 4), MediaType.APPLICATION_JSON));

        // 5. Commits: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        ArgumentCaptor<PullRequest> prCaptor = ArgumentCaptor.forClass(PullRequest.class);
        verify(pullRequestRepository, times(1)).save(prCaptor.capture());

        PullRequest savedPr = prCaptor.getValue();
        assertEquals(42, savedPr.getNumber());
        assertEquals("PR 42", savedPr.getTitle());
        assertEquals(25, savedPr.getAdditions());
        assertEquals(10, savedPr.getDeletions());
        assertEquals(4, savedPr.getChangedFiles());
    }

    @Test
    void importRepository_multiplePullRequests_eachFetchesOwnDetailEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Sahitya");
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs list: returns 3 PRs (1, 2, 3)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 3), MediaType.APPLICATION_JSON));

        // 4. Detail for PR 1
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(1, 10, 2, 1), MediaType.APPLICATION_JSON));

        // 5. Detail for PR 2
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(2, 20, 4, 2), MediaType.APPLICATION_JSON));

        // 6. Detail for PR 3
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(3, 30, 6, 3), MediaType.APPLICATION_JSON));

        // 7. Commits: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        ArgumentCaptor<PullRequest> prCaptor = ArgumentCaptor.forClass(PullRequest.class);
        verify(pullRequestRepository, times(3)).save(prCaptor.capture());

        var savedPrs = prCaptor.getAllValues();
        assertEquals(3, savedPrs.size());

        assertEquals(1, savedPrs.get(0).getNumber());
        assertEquals(10, savedPrs.get(0).getAdditions());
        assertEquals(2, savedPrs.get(0).getDeletions());
        assertEquals(1, savedPrs.get(0).getChangedFiles());

        assertEquals(2, savedPrs.get(1).getNumber());
        assertEquals(20, savedPrs.get(1).getAdditions());
        assertEquals(4, savedPrs.get(1).getDeletions());
        assertEquals(2, savedPrs.get(1).getChangedFiles());

        assertEquals(3, savedPrs.get(2).getNumber());
        assertEquals(30, savedPrs.get(2).getAdditions());
        assertEquals(6, savedPrs.get(2).getDeletions());
        assertEquals(3, savedPrs.get(2).getChangedFiles());
    }

    @Test
    void importRepository_repeatedSync_updatesExistingPullRequestStatsWithoutDuplicates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Sahitya");
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        PullRequest existingPr = new PullRequest();
        existingPr.setId(500L);
        existingPr.setNumber(7);
        existingPr.setTitle("Old Title");
        existingPr.setAdditions(2);
        existingPr.setDeletions(1);
        existingPr.setChangedFiles(1);
        when(pullRequestRepository.findByRepositoryIdAndNumber(10L, 7)).thenReturn(Optional.of(existingPr));

        // 1. Repo metadata
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs: returns PR 7
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(7, 1), MediaType.APPLICATION_JSON));

        // 4. Detail returns updated stats: 55, 18, 8
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(7, 55, 18, 8), MediaType.APPLICATION_JSON));

        // 5. Commits: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        // Verify existing entity updated in-place without creating a duplicate
        assertEquals(500L, existingPr.getId());
        assertEquals("PR 7", existingPr.getTitle());
        assertEquals(55, existingPr.getAdditions());
        assertEquals(18, existingPr.getDeletions());
        assertEquals(8, existingPr.getChangedFiles());
        verify(pullRequestRepository, times(1)).save(existingPr);
    }

    @Test
    void importRepository_whenPullRequestDetail401Unauthorized_throwsGitHubApiExceptionWith401() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues succeeds (empty list)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs list succeeds with 1 PR
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1), MediaType.APPLICATION_JSON));

        // 4. PR detail fails with 401 Unauthorized
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Bad credentials\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.isUnauthorized());
        server.verify();
        verify(pullRequestRepository, never()).save(any(PullRequest.class));
        verify(activityRepository, never()).save(any(Activity.class));
    }

    @Test
    void importRepository_whenPullRequestDetail403Forbidden_throwsGitHubApiExceptionWith403() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues succeeds (empty list)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs list succeeds with 1 PR
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1), MediaType.APPLICATION_JSON));

        // 4. PR detail fails with 403 Forbidden
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"message\":\"API rate limit exceeded\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.isForbidden());
        server.verify();
        verify(pullRequestRepository, never()).save(any(PullRequest.class));
    }

    @Test
    void importRepository_whenPullRequestDetail404NotFound_throwsGitHubApiExceptionWith404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues succeeds (empty list)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs list succeeds with 1 PR
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1), MediaType.APPLICATION_JSON));

        // 4. PR detail fails with 404 Not Found
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"Not Found\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.isNotFound());
        server.verify();
        verify(pullRequestRepository, never()).save(any(PullRequest.class));
    }

    @Test
    void importRepository_whenPullRequestDetail500InternalError_throwsGitHubApiExceptionWith500() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues succeeds (empty list)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs list succeeds with 1 PR
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1), MediaType.APPLICATION_JSON));

        // 4. PR detail fails with 500
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(500, ex.getStatusCode());
        server.verify();
        verify(pullRequestRepository, never()).save(any(PullRequest.class));
    }

    @Test
    void importRepository_whenPullRequestDetailFails_doesNotExposeSecretToken() {
        String secretToken = "ghp_SecretToken1234567890abcdef";
        gitHubService.setGithubToken(secretToken);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        // 1. Repo metadata succeeds
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // 2. Issues succeeds (empty list)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 3. PRs list succeeds with 1 PR
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1), MediaType.APPLICATION_JSON));

        // 4. PR detail fails with 401
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertFalse(ex.getMessage().contains(secretToken));
        server.verify();
    }

    @Test
    void importRepository_whenGitHub429TooManyRequests_throwsRateLimitExceptionWithHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", "1741234567");
        headers.set("Retry-After", "60");

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(429, ex.getStatusCode());
        assertEquals(429, ex.getGithubStatus());
        assertTrue(ex.isRateLimit());
        assertTrue(ex.isRateLimited());
        assertEquals(0, ex.getRateLimitRemaining());
        assertEquals(1741234567L, ex.getRateLimitReset());
        assertEquals(60L, ex.getRetryAfter());
        assertTrue(ex.getMessage().contains("rate limit"));
        server.verify();
    }

    @Test
    void importRepository_whenGitHub429WithoutOptionalHeaders_throwsRateLimitExceptionWithoutError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.isRateLimit());
        assertNull(ex.getRateLimitRemaining());
        assertNull(ex.getRateLimitReset());
        assertNull(ex.getRetryAfter());
        server.verify();
    }

    @Test
    void importRepository_whenGitHub403WithZeroRemaining_isDetectedAsRateLimited() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", "1741234567");

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.isForbidden());
        assertTrue(ex.isRateLimit());
        assertEquals(0, ex.getRateLimitRemaining());
        assertEquals(1741234567L, ex.getRateLimitReset());
        assertTrue(ex.getMessage().contains("rate limit"));
        server.verify();
    }

    @Test
    void importRepository_whenGitHub403WithRemainingQuota_remainsNormalPermissionError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "50");

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.isForbidden());
        assertFalse(ex.isRateLimit());
        assertTrue(ex.getMessage().contains("forbidden") || ex.getMessage().contains("permissions"));
        server.verify();
    }

    @Test
    void importRepository_whenGitHub403WithRetryAfter_isDetectedAsRateLimited() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "120");

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.isRateLimit());
        assertEquals(120L, ex.getRetryAfter());
        server.verify();
    }

    @Test
    void importRepository_whenRateLimitHit_doesNotExposeSecretToken() {
        String secretToken = "ghp_SecretToken1234567890abcdef";
        gitHubService.setGithubToken(secretToken);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertFalse(ex.getMessage().contains(secretToken));
        server.verify();
    }

    @Test
    void timeouts_defaultValues_are10sConnectAnd30sRead() {
        assertEquals(10000, gitHubService.getConnectTimeoutMs());
        assertEquals(30000, gitHubService.getReadTimeoutMs());
    }

    @Test
    void timeouts_customValues_areRespectedInRequestFactory() {
        gitHubService.setConnectTimeoutMs(5000);
        gitHubService.setReadTimeoutMs(15000);

        assertEquals(5000, gitHubService.getConnectTimeoutMs());
        assertEquals(15000, gitHubService.getReadTimeoutMs());

        var factory = gitHubService.createRequestFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof SimpleClientHttpRequestFactory);
    }

    @Test
    void restClientReuse_returnsSameInstanceUntilReconfigured() {
        RestClient client1 = gitHubService.getRestClient();
        RestClient client2 = gitHubService.getRestClient();
        assertSame(client1, client2, "RestClient should be cached and reused across calls");

        // When reconfigured, cached instance should be invalidated and rebuilt
        gitHubService.setConnectTimeoutMs(8000);
        RestClient client3 = gitHubService.getRestClient();
        assertNotSame(client1, client3, "Reconfiguring timeouts should invalidate cached RestClient");
        assertSame(client3, gitHubService.getRestClient(), "New RestClient should be cached and reused");
    }

    @Test
    void fetchRepositoryMetadata_whenTimeoutOccurs_throwsGitHubApiExceptionWith502() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(ExpectedCount.times(3), requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchRepositoryMetadata("octocat", "Hello-World"));

        assertEquals(502, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Read timed out") || ex.getMessage().contains("connect"));
        server.verify();
    }

    @Test
    void fetchIssuesPage_whenTimeoutOccurs_throwsGitHubApiExceptionWith502() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(ExpectedCount.times(3), requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Connection timed out");
                });

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchIssuesPage("octocat", "Hello-World", 1, 100));

        assertEquals(502, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Connection timed out") || ex.getMessage().contains("Failed to retrieve issues"));
        server.verify();
    }

    @Test
    void fetchPullRequestDetails_whenTimeoutOccurs_throwsGitHubApiExceptionWith502() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(ExpectedCount.times(3), requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchPullRequestDetails("octocat", "Hello-World", 10));

        assertEquals(502, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Read timed out") || ex.getMessage().contains("Failed to fetch PR"));
        server.verify();
    }

    @Test
    void restClientReuse_multipleApiCalls_useSameRestClientInstance() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":123,\"name\":\"Hello-World\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        RestClient clientBefore = gitHubService.getRestClient();
        var meta = gitHubService.fetchRepositoryMetadata("octocat", "Hello-World");
        var issues = gitHubService.fetchIssuesPage("octocat", "Hello-World", 1, 100);
        RestClient clientAfter = gitHubService.getRestClient();

        assertSame(clientBefore, clientAfter, "The exact same RestClient instance must be reused across distinct calls");
        assertNotNull(meta);
        assertNotNull(issues);
        server.verify();
    }

    @Test
    void shouldFetchPullRequestDetails_coversAllDecisionBranches() {
        // 1. null existing PR -> true
        assertTrue(gitHubService.shouldFetchPullRequestDetails(null, "2026-01-01T10:00:00Z"));

        // 2. transient unpersisted empty PR -> true
        PullRequest transientPr = new PullRequest();
        assertTrue(gitHubService.shouldFetchPullRequestDetails(transientPr, "2026-01-01T10:00:00Z"));

        // 3. existing PR with null additions -> true
        PullRequest prMissingAdditions = new PullRequest();
        prMissingAdditions.setId(1L);
        prMissingAdditions.setAdditions(null);
        prMissingAdditions.setDeletions(5);
        prMissingAdditions.setChangedFiles(1);
        prMissingAdditions.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));
        assertTrue(gitHubService.shouldFetchPullRequestDetails(prMissingAdditions, "2026-01-01T10:00:00Z"));

        // 4. existing PR with null deletions -> true
        PullRequest prMissingDeletions = new PullRequest();
        prMissingDeletions.setId(1L);
        prMissingDeletions.setAdditions(10);
        prMissingDeletions.setDeletions(null);
        prMissingDeletions.setChangedFiles(1);
        prMissingDeletions.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));
        assertTrue(gitHubService.shouldFetchPullRequestDetails(prMissingDeletions, "2026-01-01T10:00:00Z"));

        // 5. existing PR with null changedFiles -> true
        PullRequest prMissingFiles = new PullRequest();
        prMissingFiles.setId(1L);
        prMissingFiles.setAdditions(10);
        prMissingFiles.setDeletions(5);
        prMissingFiles.setChangedFiles(null);
        prMissingFiles.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));
        assertTrue(gitHubService.shouldFetchPullRequestDetails(prMissingFiles, "2026-01-01T10:00:00Z"));

        // 6. complete PR but rawUpdatedAt is null or blank -> true
        PullRequest completePr = new PullRequest();
        completePr.setId(1L);
        completePr.setAdditions(10);
        completePr.setDeletions(5);
        completePr.setChangedFiles(2);
        completePr.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));
        assertTrue(gitHubService.shouldFetchPullRequestDetails(completePr, null));
        assertTrue(gitHubService.shouldFetchPullRequestDetails(completePr, "   "));

        // 7. complete PR but rawUpdatedAt cannot be parsed -> true
        assertTrue(gitHubService.shouldFetchPullRequestDetails(completePr, "not-a-valid-date"));

        // 8. complete PR but persisted githubUpdatedAt is null -> true
        PullRequest prNullPersistedDate = new PullRequest();
        prNullPersistedDate.setId(1L);
        prNullPersistedDate.setAdditions(10);
        prNullPersistedDate.setDeletions(5);
        prNullPersistedDate.setChangedFiles(2);
        prNullPersistedDate.setGithubUpdatedAt(null);
        assertTrue(gitHubService.shouldFetchPullRequestDetails(prNullPersistedDate, "2026-01-01T10:00:00Z"));

        // 9. complete PR but updated_at changed -> true
        assertTrue(gitHubService.shouldFetchPullRequestDetails(completePr, "2026-01-02T10:00:00Z"));

        // 10. complete PR and updated_at matches persisted date exactly -> false (SKIP detail call!)
        assertFalse(gitHubService.shouldFetchPullRequestDetails(completePr, "2026-01-01T10:00:00Z"));
    }

    @Test
    void importRepository_whenNewPR_callsDetailEndpointAndPersistsStats() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository mockRepo = new Repository();
        mockRepo.setId(10L);
        mockRepo.setOwner("octocat");
        mockRepo.setName("Hello-World");
        mockRepo.setFullName("octocat/Hello-World");
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.empty());
        when(repoRepository.save(any(Repository.class))).thenReturn(mockRepo);

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of());

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1, "2026-01-01T10:00:00Z"), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(1, 30, 10, 2), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        ArgumentCaptor<PullRequest> prCaptor = ArgumentCaptor.forClass(PullRequest.class);
        verify(pullRequestRepository, times(1)).save(prCaptor.capture());
        PullRequest savedPr = prCaptor.getValue();
        assertEquals(1, savedPr.getNumber());
        assertEquals(30, savedPr.getAdditions());
        assertEquals(10, savedPr.getDeletions());
        assertEquals(2, savedPr.getChangedFiles());
    }

    @Test
    void importRepository_whenUnchangedPR_skipsDetailEndpointAndPreservesExistingStats() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        PullRequest existingPr = new PullRequest();
        existingPr.setId(500L);
        existingPr.setNumber(1);
        existingPr.setTitle("Original PR 1");
        existingPr.setAdditions(45);
        existingPr.setDeletions(15);
        existingPr.setChangedFiles(3);
        existingPr.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of(existingPr));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // GitHub returns PR 1 with the identical updated_at
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1, "2026-01-01T10:00:00Z"), MediaType.APPLICATION_JSON));

        // NOTE: NO expectation for /repos/octocat/Hello-World/pulls/1 !
        // If a detail request was made, MockRestServiceServer would immediately fail with an unexpected request error.

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        // Verify stats are completely preserved and PR is saved
        assertEquals(45, existingPr.getAdditions());
        assertEquals(15, existingPr.getDeletions());
        assertEquals(3, existingPr.getChangedFiles());
        verify(pullRequestRepository, times(1)).save(existingPr);
    }

    @Test
    void importRepository_whenChangedPR_callsDetailEndpointAndPersistsUpdatedStats() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        PullRequest existingPr = new PullRequest();
        existingPr.setId(500L);
        existingPr.setNumber(1);
        existingPr.setTitle("Old Title");
        existingPr.setAdditions(45);
        existingPr.setDeletions(15);
        existingPr.setChangedFiles(3);
        existingPr.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of(existingPr));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // GitHub returns PR 1 with a changed updated_at
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1, "2026-01-05T12:00:00Z"), MediaType.APPLICATION_JSON));

        // Detail endpoint MUST be called
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(1, 90, 35, 8), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        assertEquals(90, existingPr.getAdditions());
        assertEquals(35, existingPr.getDeletions());
        assertEquals(8, existingPr.getChangedFiles());
        assertEquals(gitHubService.parseDateTime("2026-01-05T12:00:00Z"), existingPr.getGithubUpdatedAt());
        verify(pullRequestRepository, times(1)).save(existingPr);
    }

    @Test
    void importRepository_whenExistingPRWithMissingStats_callsDetailEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        PullRequest existingPr = new PullRequest();
        existingPr.setId(500L);
        existingPr.setNumber(1);
        existingPr.setAdditions(null); // Missing additions!
        existingPr.setDeletions(10);
        existingPr.setChangedFiles(2);
        existingPr.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of(existingPr));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // GitHub returns same updated_at
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1, "2026-01-01T10:00:00Z"), MediaType.APPLICATION_JSON));

        // Detail endpoint MUST be called to backfill missing stats
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(1, 60, 20, 5), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        assertEquals(60, existingPr.getAdditions());
        assertEquals(20, existingPr.getDeletions());
        assertEquals(5, existingPr.getChangedFiles());
    }

    @Test
    void importRepository_multiplePRs_onlyFetchesDetailsForChangedAndNewPRs() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        LocalDateTime baseDate = gitHubService.parseDateTime("2026-01-01T10:00:00Z");

        PullRequest pr1 = new PullRequest();
        pr1.setId(101L); pr1.setNumber(1); pr1.setAdditions(10); pr1.setDeletions(2); pr1.setChangedFiles(1); pr1.setGithubUpdatedAt(baseDate);

        PullRequest pr2 = new PullRequest();
        pr2.setId(102L); pr2.setNumber(2); pr2.setAdditions(20); pr2.setDeletions(4); pr2.setChangedFiles(2); pr2.setGithubUpdatedAt(baseDate);

        PullRequest pr3 = new PullRequest();
        pr3.setId(103L); pr3.setNumber(3); pr3.setAdditions(30); pr3.setDeletions(6); pr3.setChangedFiles(3); pr3.setGithubUpdatedAt(baseDate);

        // PR 4 is NEW (not in DB)

        PullRequest pr5 = new PullRequest();
        pr5.setId(105L); pr5.setNumber(5); pr5.setAdditions(50); pr5.setDeletions(10); pr5.setChangedFiles(5); pr5.setGithubUpdatedAt(baseDate);

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of(pr1, pr2, pr3, pr5));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 5 PRs returned: 1, 3, 5 unchanged; 2 changed; 4 new
        String prsJson = "[" +
                generateMockPullItemJson(1, "2026-01-01T10:00:00Z") + "," +
                generateMockPullItemJson(2, "2026-01-02T10:00:00Z") + "," + // Changed!
                generateMockPullItemJson(3, "2026-01-01T10:00:00Z") + "," +
                generateMockPullItemJson(4, "2026-01-01T10:00:00Z") + "," + // New!
                generateMockPullItemJson(5, "2026-01-01T10:00:00Z") +
                "]";

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(prsJson, MediaType.APPLICATION_JSON));

        // Only PR 2 and PR 4 should result in detail calls!
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(2, 99, 44, 9), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/4"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(4, 40, 8, 4), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        // Unchanged PRs retain original stats
        assertEquals(10, pr1.getAdditions());
        assertEquals(30, pr3.getAdditions());
        assertEquals(50, pr5.getAdditions());

        // Changed PR 2 received updated stats
        assertEquals(99, pr2.getAdditions());
        assertEquals(44, pr2.getDeletions());
        assertEquals(9, pr2.getChangedFiles());
    }

    @Test
    void importRepository_repeatedSync_firstFetchesDetailsSecondSkipsUnchanged() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        // 1. First sync: PR 1 is in DB with updated_at matching the list
        PullRequest pr1 = new PullRequest();
        pr1.setId(101L);
        pr1.setNumber(1);
        pr1.setAdditions(25);
        pr1.setDeletions(5);
        pr1.setChangedFiles(2);
        pr1.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of(pr1));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1, "2026-01-01T10:00:00Z"), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // Re-sync: Detail request is skipped completely!
        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();
        assertEquals(25, pr1.getAdditions());
        assertEquals(5, pr1.getDeletions());
    }

    @Test
    void importRepository_pagination_deltaOptimizationWorksAcrossPages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        // Page size 1 for pagination test
        gitHubService.setPageSize(1);

        LocalDateTime baseDate = gitHubService.parseDateTime("2026-01-01T10:00:00Z");

        PullRequest pr1 = new PullRequest();
        pr1.setId(101L); pr1.setNumber(1); pr1.setAdditions(10); pr1.setDeletions(2); pr1.setChangedFiles(1); pr1.setGithubUpdatedAt(baseDate);

        PullRequest pr2 = new PullRequest();
        pr2.setId(102L); pr2.setNumber(2); pr2.setAdditions(20); pr2.setDeletions(4); pr2.setChangedFiles(2); pr2.setGithubUpdatedAt(baseDate);

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of(pr1, pr2));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        // Issues: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=1&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // PR Page 1: PR 1 (unchanged) -> detail skipped!
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=1&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[" + generateMockPullItemJson(1, "2026-01-01T10:00:00Z") + "]", MediaType.APPLICATION_JSON));

        // PR Page 2: PR 2 (changed!) -> detail called!
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=1&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[" + generateMockPullItemJson(2, "2026-01-03T10:00:00Z") + "]", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullDetailJson(2, 77, 33, 7), MediaType.APPLICATION_JSON));

        // PR Page 3: 0 items (end of PRs)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=1&page=3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // Commits: 0 items
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=1&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubService.importRepository("octocat", "Hello-World", "user@test.com");

        server.verify();

        assertEquals(10, pr1.getAdditions());
        assertEquals(77, pr2.getAdditions());
    }

    @Test
    void importRepository_whenChangedPRDetailFails_preservesGitHubApiException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        when(userRepository.findFirstByEmailOrderByIdDesc("user@test.com")).thenReturn(Optional.of(mockUser));

        Repository existingRepo = new Repository();
        existingRepo.setId(10L);
        existingRepo.setOwner("octocat");
        existingRepo.setName("Hello-World");
        existingRepo.setUser(mockUser);
        when(repoRepository.findByOwnerAndName("octocat", "Hello-World")).thenReturn(Optional.of(existingRepo));
        when(repoRepository.save(any(Repository.class))).thenReturn(existingRepo);

        PullRequest existingPr = new PullRequest();
        existingPr.setId(500L);
        existingPr.setNumber(1);
        existingPr.setAdditions(45);
        existingPr.setDeletions(15);
        existingPr.setChangedFiles(3);
        existingPr.setGithubUpdatedAt(gitHubService.parseDateTime("2026-01-01T10:00:00Z"));

        when(pullRequestRepository.findByRepositoryId(10L)).thenReturn(List.of(existingPr));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // Changed PR 1
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(generateMockPullsJson(1, 1, "2026-01-05T12:00:00Z"), MediaType.APPLICATION_JSON));

        // Detail endpoint fails with 500
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.importRepository("octocat", "Hello-World", "user@test.com"));

        assertEquals(500, ex.getStatusCode());
        server.verify();
    }

    // =========================================================================
    // Phase 6C: Bounded GitHub API Transient Retry Tests
    // =========================================================================

    @Test
    void fetchRepositoryMetadata_when502ThenSucceeds_retriesAndReturnsData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        // Attempt 1: 502 Bad Gateway
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        // Attempt 2: 200 OK
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":12345,\"name\":\"Hello-World\",\"full_name\":\"octocat/Hello-World\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> data = gitHubService.fetchRepositoryMetadata("octocat", "Hello-World");

        assertNotNull(data);
        assertEquals("octocat/Hello-World", data.get("full_name"));
        server.verify(); // Verifies exactly 2 attempts were executed
    }

    @Test
    void fetchIssuesPage_whenTimeoutThenSucceeds_retriesAndReturnsData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        // Attempt 1: Transient timeout
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        // Attempt 2: 200 OK
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":1,\"number\":1,\"title\":\"Bug 1\"}]", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> issues = gitHubService.fetchIssuesPage("octocat", "Hello-World", 1, 100);

        assertNotNull(issues);
        assertEquals(1, issues.size());
        assertEquals("Bug 1", issues.get(0).get("title"));
        server.verify(); // Verifies exactly 2 attempts
    }

    @Test
    void fetchPullRequestDetails_when503Then503Then503_exhaustsRetries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        // 3 consecutive 503 responses (1 initial + 2 retries)
        server.expect(ExpectedCount.times(3), requestTo("https://api.github.com/repos/octocat/Hello-World/pulls/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchPullRequestDetails("octocat", "Hello-World", 42));

        assertEquals(503, ex.getStatusCode());
        server.verify(); // Verifies exactly 3 total attempts
    }

    @Test
    void fetchCommitsPage_when504ThenSucceeds_retries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        // Attempt 1: 504 Gateway Timeout
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        // Attempt 2: 200 OK
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"sha\":\"abc1234\"}]", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> commits = gitHubService.fetchCommitsPage("octocat", "Hello-World", 1, 100);

        assertNotNull(commits);
        assertEquals(1, commits.size());
        assertEquals("abc1234", commits.get(0).get("sha"));
        server.verify(); // Exactly 2 attempts
    }

    @Test
    void fetchCommitsPage_when401_failsImmediatelyWithoutRetry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(ExpectedCount.times(1), requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Bad credentials\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchCommitsPage("octocat", "Hello-World", 1, 100));

        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.isUnauthorized());
        server.verify(); // Exactly 1 attempt
    }

    @Test
    void fetchRepositoryMetadata_when403Permission_failsImmediatelyWithoutRetry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "500");

        server.expect(ExpectedCount.times(1), requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers).body("{\"message\":\"Must have admin rights\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchRepositoryMetadata("octocat", "Hello-World"));

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.isForbidden());
        assertFalse(ex.isRateLimit());
        server.verify(); // Exactly 1 attempt
    }

    @Test
    void fetchIssuesPage_when404_failsImmediatelyWithoutRetry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(ExpectedCount.times(1), requestTo("https://api.github.com/repos/octocat/Hello-World/issues?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"Not Found\"}"));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchIssuesPage("octocat", "Hello-World", 1, 100));

        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.isNotFound());
        server.verify(); // Exactly 1 attempt
    }

    @Test
    void fetchPullRequestsPage_whenPrimaryRateLimitExceeded_failsImmediately() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", "1741234567");

        server.expect(ExpectedCount.times(1), requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchPullRequestsPage("octocat", "Hello-World", 1, 100));

        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.isRateLimit());
        assertNull(ex.getRetryAfter());
        server.verify(); // Exactly 1 attempt
    }

    @Test
    void fetchPullRequestsPage_whenSecondaryRateLimitRetryAfter2Seconds_retriesOnce() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "2");

        // Attempt 1: 429 with Retry-After: 2
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        // Attempt 2: 200 OK
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":10,\"number\":10,\"title\":\"PR 10\"}]", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> pulls = gitHubService.fetchPullRequestsPage("octocat", "Hello-World", 1, 100);

        assertNotNull(pulls);
        assertEquals(1, pulls.size());
        server.verify(); // Exactly 2 attempts
    }

    @Test
    void fetchPullRequestsPage_whenSecondaryRateLimitFailsAgain_doesNotPerformNormal3AttemptRetry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "2");

        // Attempt 1: 429 with Retry-After: 2
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        // Attempt 2: 429 with Retry-After: 2 (fails again)
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/pulls?state=all&per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchPullRequestsPage("octocat", "Hello-World", 1, 100));

        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.isRateLimit());
        server.verify(); // Exactly 2 attempts (NO 3rd attempt!)
    }

    @Test
    void fetchRepositoryMetadata_whenTimeoutExhaustion_preservesGitHubApiExceptionStatusAndSanitizedMessage() {
        String secretToken = "ghp_SecretToken12345ABCDE";
        gitHubService.setGithubToken(secretToken);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        server.expect(ExpectedCount.times(3), requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out with token " + secretToken);
                });

        GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                gitHubService.fetchRepositoryMetadata("octocat", "Hello-World"));

        assertEquals(502, ex.getStatusCode());
        assertFalse(ex.getMessage().contains(secretToken));
        assertTrue(ex.getMessage().contains("[REDACTED]"));
        server.verify(); // Exactly 3 attempts
    }

    @Test
    void interruptedRetry_restoresInterruptFlagAndStops() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        gitHubService.setRestClientBuilder(builder);

        // Sleeper throws InterruptedException
        gitHubService.setSleeper(ms -> {
            throw new InterruptedException("Interrupted during test");
        });

        server.expect(ExpectedCount.times(1), requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        try {
            GitHubApiException ex = assertThrows(GitHubApiException.class, () ->
                    gitHubService.fetchRepositoryMetadata("octocat", "Hello-World"));
            assertEquals(502, ex.getStatusCode());
            assertTrue(Thread.currentThread().isInterrupted(), "Thread interrupt flag should be restored");
        } finally {
            // Clear interrupted flag so other tests are not affected
            Thread.interrupted();
        }

        server.verify(); // Stopped after attempt 1 because of interruption!
    }

    @Test
    void calculateBackoff_exponentialValuesWithCapAndJitter() {
        // Attempt 1 retry backoff (retriesDone = 0): base 500, +0..100 jitter -> 500..600
        long backoff0 = gitHubService.calculateBackoff(0);
        assertTrue(backoff0 >= 500 && backoff0 <= 600, "Backoff for retriesDone=0 should be between 500 and 600 ms");

        // Attempt 2 retry backoff (retriesDone = 1): base 1000, +0..100 jitter -> 1000..1100
        long backoff1 = gitHubService.calculateBackoff(1);
        assertTrue(backoff1 >= 1000 && backoff1 <= 1100, "Backoff for retriesDone=1 should be between 1000 and 1100 ms");

        // High attempt backoff: base 4000, capped at 2000
        long backoff3 = gitHubService.calculateBackoff(3);
        assertEquals(2000L, backoff3, "Backoff should be capped at 2000ms");
    }
}
