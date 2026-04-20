package com.example.tasks;

/**
 * Canonical error codes emitted by {@link Task} and {@link TemporalTask}.
 */
public enum TaskErrorCode {
    // Generic
    TIMEOUT,
    CANCELLED,
    INTERRUPTED,
    BAD_INPUT,
    UNAUTHENTICATED,
    FORBIDDEN,
    EXECUTION_ERROR,

    // Temporal-specific
    WORKFLOW_FAILED,           // WorkflowFailedException - workflow threw
    WORKFLOW_NOT_FOUND,        // WorkflowNotFoundException
    WORKFLOW_ALREADY_STARTED,  // WorkflowExecutionAlreadyStarted
    WORKFLOW_TIMED_OUT,        // Temporal TimeoutFailure (schedule-to-close, etc.)
    WORKFLOW_TERMINATED,       // TerminatedFailure
    ACTIVITY_FAILED,           // ActivityFailure wrapping a retryable ApplicationFailure
    ACTIVITY_NON_RETRYABLE,    // ApplicationFailure with nonRetryable=true
    WORKER_UNAVAILABLE,        // no worker polling the task queue
    SERVICE_UNAVAILABLE        // Temporal frontend unreachable
}
