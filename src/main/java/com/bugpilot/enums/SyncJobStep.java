package com.bugpilot.enums;

public enum SyncJobStep {
    QUEUED,
    FETCHING_METADATA,
    FETCHING_ISSUES,
    FETCHING_PULL_REQUESTS,
    FETCHING_COMMITS,
    COMPLETED
}
