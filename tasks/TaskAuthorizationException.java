package com.example.tasks;

import lombok.Getter;

/**
 * Thrown when a Task fails authorization checks.
 * Distinct subclasses let us map cleanly to UNAUTHENTICATED vs FORBIDDEN.
 */
@Getter
public class TaskAuthorizationException extends RuntimeException {

    private final TaskErrorCode code;

    public TaskAuthorizationException(TaskErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static TaskAuthorizationException unauthenticated() {
        return new TaskAuthorizationException(
                TaskErrorCode.UNAUTHENTICATED, "No authenticated principal");
    }

    public static TaskAuthorizationException forbidden(String required) {
        return new TaskAuthorizationException(
                TaskErrorCode.FORBIDDEN, "Missing required authority: " + required);
    }
}
