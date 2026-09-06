package com.bugpilot.service;

import com.bugpilot.entity.SyncJob;
import com.bugpilot.enums.SyncJobStep;
import com.bugpilot.exception.GitHubApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RepositorySyncWorker {

    private static final Logger log = LoggerFactory.getLogger(RepositorySyncWorker.class);

    private final GitHubService gitHubService;
    private final RepositorySyncPersistenceService persistenceService;

    public RepositorySyncWorker(GitHubService gitHubService,
                                RepositorySyncPersistenceService persistenceService) {
        this.gitHubService = gitHubService;
        this.persistenceService = persistenceService;
    }

    @Async("syncExecutor")
    public void executeSync(Long jobId) {
        log.info("Starting background sync execution for SyncJob ID: {}", jobId);

        SyncJob job = persistenceService.findJobById(jobId);
        if (job == null) {
            log.error("SyncJob {} not found in database. Aborting worker execution.", jobId);
            return;
        }

        Long repositoryId = job.getRepository().getId();
        String owner = job.getRepository().getOwner();
        String repoName = job.getRepository().getName();
        String userEmail = job.getTriggeredBy().getEmail();
        String actorName = job.getTriggeredBy().getName();

        try {
            // Step 1: Mark job IN_PROGRESS
            persistenceService.markJobInProgress(jobId);

            // Step 2: Fetch and persist repository metadata (NO DB txn held during GitHub call)
            persistenceService.updateJobStep(jobId, SyncJobStep.FETCHING_METADATA);
            log.info("Fetching repository metadata for {}/{}", owner, repoName);
            Map<String, Object> repoData = gitHubService.fetchRepositoryMetadata(owner, repoName);
            persistenceService.persistRepositoryMetadata(repositoryId, repoData, userEmail);

            int pageSize = gitHubService.getPageSize();

            // Step 3: Fetch and persist issues
            persistenceService.updateJobStep(jobId, SyncJobStep.FETCHING_ISSUES);
            int page = 1;
            while (page <= GitHubService.MAX_PAGES) {
                log.info("Fetching issues page {} for {}/{}", page, owner, repoName);
                List<Map<String, Object>> items = gitHubService.fetchIssuesPage(owner, repoName, page, pageSize);
                if (items == null || items.isEmpty()) {
                    break;
                }
                int count = persistenceService.persistIssues(repositoryId, items);
                persistenceService.incrementCounts(jobId, count, 0, 0);

                if (items.size() < pageSize) {
                    break;
                }
                page++;
            }

            // Step 4: Fetch and persist pull requests (with diff stats)
            persistenceService.updateJobStep(jobId, SyncJobStep.FETCHING_PULL_REQUESTS);
            page = 1;
            Map<Integer, com.bugpilot.entity.PullRequest> existingPrMap = null;
            try {
                existingPrMap = persistenceService.getExistingPullRequestsMap(repositoryId);
            } catch (Exception ignored) {
            }
            if (existingPrMap == null) {
                existingPrMap = java.util.Collections.emptyMap();
            }

            while (page <= GitHubService.MAX_PAGES) {
                log.info("Fetching pull requests page {} for {}/{}", page, owner, repoName);
                List<Map<String, Object>> items = gitHubService.fetchPullRequestsPage(owner, repoName, page, pageSize);
                if (items == null || items.isEmpty()) {
                    break;
                }

                // For each PR, fetch diff statistics from GitHub outside DB txn only when needed
                for (Map<String, Object> prItem : items) {
                    if (prItem.get("number") instanceof Number num) {
                        int prNum = num.intValue();
                        com.bugpilot.entity.PullRequest existingPr = existingPrMap.get(prNum);
                        String rawUpdatedAt = (String) prItem.get("updated_at");

                        boolean needDetails = gitHubService.shouldFetchPullRequestDetails(existingPr, rawUpdatedAt);
                        if (needDetails) {
                            try {
                                Map<String, Object> detail = gitHubService.fetchPullRequestDetails(owner, repoName, prNum);
                                if (detail != null) {
                                    prItem.put("_additions", detail.get("additions"));
                                    prItem.put("_deletions", detail.get("deletions"));
                                    prItem.put("_changed_files", detail.get("changed_files"));
                                }
                            } catch (Exception ex) {
                                log.error("Failed to fetch diff stats for PR #{} on {}/{}", prNum, owner, repoName, ex);
                                throw ex;
                            }
                        } else {
                            log.debug("Skipping diff stats detail call for unchanged PR #{} on {}/{}", prNum, owner, repoName);
                        }
                    }
                }

                int count = persistenceService.persistPullRequests(repositoryId, items);
                persistenceService.incrementCounts(jobId, 0, count, 0);

                if (items.size() < pageSize) {
                    break;
                }
                page++;
            }

            // Step 5: Fetch and persist commits
            persistenceService.updateJobStep(jobId, SyncJobStep.FETCHING_COMMITS);
            page = 1;
            while (page <= GitHubService.MAX_PAGES) {
                log.info("Fetching commits page {} for {}/{}", page, owner, repoName);
                List<Map<String, Object>> items = gitHubService.fetchCommitsPage(owner, repoName, page, pageSize);
                if (items == null || items.isEmpty()) {
                    break;
                }
                int count = persistenceService.persistCommits(repositoryId, items);
                persistenceService.incrementCounts(jobId, 0, 0, count);

                if (items.size() < pageSize) {
                    break;
                }
                page++;
            }

            // Step 6: Mark job COMPLETED
            persistenceService.markJobCompleted(jobId, repositoryId, actorName);
            log.info("SyncJob {} completed successfully for {}/{}", jobId, owner, repoName);

        } catch (GitHubApiException e) {
            String safeMsg = gitHubService.sanitize(e.getMessage());
            log.error("SyncJob {} failed with GitHubApiException (status {}): {}",
                    jobId, e.getStatusCode(), safeMsg);
            persistenceService.markJobFailed(jobId, safeMsg, e.getStatusCode(), e.getRateLimitReset());

        } catch (Throwable t) {
            String safeMsg = gitHubService.sanitize(t.getMessage() != null ? t.getMessage() : "Unknown error during sync");
            log.error("SyncJob {} failed with exception: {}", jobId, safeMsg, t);
            persistenceService.markJobFailed(jobId, safeMsg, 502, null);
        }
    }
}
