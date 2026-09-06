package com.bugpilot.service;

import com.bugpilot.entity.*;
import com.bugpilot.enums.ActivityType;
import com.bugpilot.enums.IssueState;
import com.bugpilot.enums.PullRequestState;
import com.bugpilot.enums.SyncJobStatus;
import com.bugpilot.enums.SyncJobStep;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class RepositorySyncPersistenceService {

    private final SyncJobRepository syncJobRepository;
    private final RepoRepository repoRepository;
    private final IssueRepository issueRepository;
    private final PullRequestRepository pullRequestRepository;
    private final CommitRepository commitRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public RepositorySyncPersistenceService(SyncJobRepository syncJobRepository,
                                            RepoRepository repoRepository,
                                            IssueRepository issueRepository,
                                            PullRequestRepository pullRequestRepository,
                                            CommitRepository commitRepository,
                                            ActivityRepository activityRepository,
                                            UserRepository userRepository) {
        this.syncJobRepository = syncJobRepository;
        this.repoRepository = repoRepository;
        this.issueRepository = issueRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.commitRepository = commitRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
    }

    public SyncJob createSyncJob(Repository repository, User user) {
        SyncJob job = new SyncJob(repository, user);
        return syncJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public SyncJob findJobById(Long jobId) {
        return syncJobRepository.findById(jobId).orElse(null);
    }

    public SyncJob markJobInProgress(Long jobId) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(SyncJobStatus.IN_PROGRESS);
            job.setCurrentStep(SyncJobStep.FETCHING_METADATA);
            job.setStartedAt(LocalDateTime.now());
            return syncJobRepository.save(job);
        }
        return null;
    }

    public SyncJob updateJobStep(Long jobId, SyncJobStep step) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setCurrentStep(step);
            return syncJobRepository.save(job);
        }
        return null;
    }

    public Repository persistRepositoryMetadata(Long repositoryId, Map<String, Object> repoData, String userEmail) {
        Repository repository = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        if (repoData.get("full_name") != null) {
            repository.setFullName((String) repoData.get("full_name"));
        }
        if (repoData.get("description") != null) {
            repository.setDescription((String) repoData.get("description"));
        }
        if (repoData.get("html_url") != null) {
            repository.setHtmlUrl((String) repoData.get("html_url"));
        }
        if (repoData.get("default_branch") != null) {
            repository.setDefaultBranch((String) repoData.get("default_branch"));
        }

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

        return repoRepository.save(repository);
    }

    public int persistIssues(Long repositoryId, List<Map<String, Object>> items) {
        Repository repository = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        if (items == null || items.isEmpty()) {
            return 0;
        }

        List<Integer> numbers = items.stream()
                .filter(item -> !(item.containsKey("pull_request") && item.get("pull_request") != null))
                .map(item -> item.get("number"))
                .filter(Number.class::isInstance)
                .map(num -> ((Number) num).intValue())
                .distinct()
                .toList();

        Map<Integer, Issue> existingMap = new java.util.HashMap<>();
        if (!numbers.isEmpty()) {
            List<Issue> existingList = issueRepository.findByRepositoryIdAndNumberIn(repositoryId, numbers);
            if (existingList != null) {
                for (Issue issue : existingList) {
                    if (issue.getNumber() != null) {
                        existingMap.put(issue.getNumber(), issue);
                    }
                }
            }
        }

        List<Issue> toSave = new java.util.ArrayList<>();
        for (Map<String, Object> item : items) {
            if (item.containsKey("pull_request") && item.get("pull_request") != null) {
                continue;
            }
            if (!(item.get("number") instanceof Number num)) {
                continue;
            }
            Integer number = num.intValue();
            Issue issue = existingMap.get(number);
            if (issue == null) {
                issue = new Issue();
                existingMap.put(number, issue);
            }

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

            toSave.add(issue);
        }

        if (!toSave.isEmpty()) {
            issueRepository.saveAll(toSave);
        }
        return toSave.size();
    }

    public int persistPullRequests(Long repositoryId, List<Map<String, Object>> items) {
        Repository repository = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        if (items == null || items.isEmpty()) {
            return 0;
        }

        List<Integer> numbers = items.stream()
                .map(item -> item.get("number"))
                .filter(Number.class::isInstance)
                .map(num -> ((Number) num).intValue())
                .distinct()
                .toList();

        Map<Integer, PullRequest> existingMap = new java.util.HashMap<>();
        if (!numbers.isEmpty()) {
            List<PullRequest> existingList = pullRequestRepository.findByRepositoryIdAndNumberIn(repositoryId, numbers);
            if (existingList != null) {
                for (PullRequest pr : existingList) {
                    if (pr.getNumber() != null) {
                        existingMap.put(pr.getNumber(), pr);
                    }
                }
            }
        }

        List<PullRequest> toSave = new java.util.ArrayList<>();
        for (Map<String, Object> item : items) {
            if (!(item.get("number") instanceof Number num)) {
                continue;
            }
            Integer number = num.intValue();
            PullRequest pr = existingMap.get(number);
            if (pr == null) {
                pr = new PullRequest();
                existingMap.put(number, pr);
            }

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
            pr.setGithubUpdatedAt(parseDateTime((String) item.get("updated_at")));
            pr.setGithubClosedAt(parseDateTime((String) item.get("closed_at")));
            pr.setGithubMergedAt(parseDateTime((String) item.get("merged_at")));

            if (item.get("_additions") instanceof Number additions) {
                pr.setAdditions(additions.intValue());
            } else if (pr.getAdditions() == null) {
                pr.setAdditions(0);
            }
            if (item.get("_deletions") instanceof Number deletions) {
                pr.setDeletions(deletions.intValue());
            } else if (pr.getDeletions() == null) {
                pr.setDeletions(0);
            }
            if (item.get("_changed_files") instanceof Number changedFiles) {
                pr.setChangedFiles(changedFiles.intValue());
            } else if (pr.getChangedFiles() == null) {
                pr.setChangedFiles(0);
            }

            toSave.add(pr);
        }

        if (!toSave.isEmpty()) {
            pullRequestRepository.saveAll(toSave);
        }
        return toSave.size();
    }

    @Transactional(readOnly = true)
    public Map<Integer, PullRequest> getExistingPullRequestsMap(Long repositoryId) {
        List<PullRequest> list = pullRequestRepository.findByRepositoryId(repositoryId);
        if (list == null || list.isEmpty()) {
            return new java.util.HashMap<>();
        }
        Map<Integer, PullRequest> map = new java.util.HashMap<>();
        for (PullRequest pr : list) {
            if (pr.getNumber() != null) {
                map.put(pr.getNumber(), pr);
            }
        }
        return map;
    }

    public int persistCommits(Long repositoryId, List<Map<String, Object>> items) {
        Repository repository = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        if (items == null || items.isEmpty()) {
            return 0;
        }

        List<String> shas = items.stream()
                .map(item -> (String) item.get("sha"))
                .filter(sha -> sha != null && !sha.isBlank())
                .distinct()
                .toList();

        Map<String, Commit> existingMap = new java.util.HashMap<>();
        if (!shas.isEmpty()) {
            List<Commit> existingList = commitRepository.findByRepositoryIdAndShaIn(repositoryId, shas);
            if (existingList != null) {
                for (Commit commit : existingList) {
                    if (commit.getSha() != null) {
                        existingMap.put(commit.getSha(), commit);
                    }
                }
            }
        }

        List<Commit> toSave = new java.util.ArrayList<>();
        for (Map<String, Object> item : items) {
            String sha = (String) item.get("sha");
            if (sha == null || sha.isBlank()) {
                continue;
            }

            Commit commit = existingMap.get(sha);
            if (commit == null) {
                commit = new Commit();
                existingMap.put(sha, commit);
            }

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

            toSave.add(commit);
        }

        if (!toSave.isEmpty()) {
            commitRepository.saveAll(toSave);
        }
        return toSave.size();
    }

    public void incrementCounts(Long jobId, Integer issues, Integer prs, Integer commits) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            if (issues != null && issues > 0) {
                job.setIssuesImported((job.getIssuesImported() != null ? job.getIssuesImported() : 0) + issues);
            }
            if (prs != null && prs > 0) {
                job.setPullRequestsImported((job.getPullRequestsImported() != null ? job.getPullRequestsImported() : 0) + prs);
            }
            if (commits != null && commits > 0) {
                job.setCommitsImported((job.getCommitsImported() != null ? job.getCommitsImported() : 0) + commits);
            }
            syncJobRepository.save(job);
        }
    }

    public void markJobCompleted(Long jobId, Long repositoryId, String actorName) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(SyncJobStatus.COMPLETED);
            job.setCurrentStep(SyncJobStep.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            syncJobRepository.save(job);
        }
        repoRepository.findById(repositoryId).ifPresent(repo -> {
            String actor = actorName != null ? actorName : "System";
            activityRepository.save(new Activity(repo, ActivityType.ISSUE_CREATED,
                    "Synchronized GitHub repository " + repo.getFullName(), actor));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobFailed(Long jobId, String sanitizedMessage, Integer githubStatus, Long rateLimitReset) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(SyncJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(sanitizedMessage);
            job.setGithubStatus(githubStatus);
            job.setRateLimitReset(rateLimitReset);
            syncJobRepository.save(job);
        }
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(dateStr), ZoneId.of("UTC"));
        } catch (Exception e) {
            return null;
        }
    }
}
