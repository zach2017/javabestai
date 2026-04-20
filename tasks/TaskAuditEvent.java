package com.example.tasks;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable audit record emitted for each task lifecycle transition
 * (STARTED, AUTH_DENIED, RETRY, COMPLETED, FAILED).
 */
@Value
@Builder
public class TaskAuditEvent {

    public enum Phase { STARTED, AUTH_DENIED, RETRY, COMPLETED, FAILED }

    String taskId;
    String taskName;
    Phase phase;
    Instant timestamp;
    String principal;
    String requiredPermission;
    Integer attempt;
    Long elapsedMs;
    TaskErrorCode errorCode;
    String errorMessage;
    TaskResult.Status resultStatus;
}
