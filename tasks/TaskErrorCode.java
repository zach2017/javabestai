package com.example.tasks;

/**
 * Canonical error codes emitted by {@link Task}.
 */
public enum TaskErrorCode {
    TIMEOUT,
    CANCELLED,
    INTERRUPTED,
    BAD_INPUT,
    UNAUTHENTICATED,   // no security context / anonymous when auth required
    FORBIDDEN,         // authenticated but missing required permission/role
    EXECUTION_ERROR
}
