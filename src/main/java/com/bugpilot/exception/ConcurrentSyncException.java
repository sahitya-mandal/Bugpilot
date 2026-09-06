package com.bugpilot.exception;

public class ConcurrentSyncException extends RuntimeException {

    private final Long activeJobId;

    public ConcurrentSyncException(Long activeJobId) {
        super("A sync job is already in progress for this repository.");
        this.activeJobId = activeJobId;
    }

    public ConcurrentSyncException(String message, Long activeJobId) {
        super(message);
        this.activeJobId = activeJobId;
    }

    public Long getActiveJobId() {
        return activeJobId;
    }
}
