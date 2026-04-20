package com.example.tasks;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Optional;

/**
 * Immutable result wrapper returned by {@link Task#execute}.
 */
@Getter
@ToString
@RequiredArgsConstructor(staticName = "of")
public final class TaskResult<T> {

    public enum Status { SUCCESS, RECOVERED, FAILURE }

    private final Status status;
    private final T value;
    private final TaskErrorCode errorCode;
    private final Throwable error;
    private final long elapsedMs;
    private final String taskId;
    private final String principal;

    public static <T> TaskResult<T> success(T value, long elapsedMs, String taskId, String principal) {
        return TaskResult.of(Status.SUCCESS, value, null, null, elapsedMs, taskId, principal);
    }

    public static <T> TaskResult<T> recovered(T value, TaskErrorCode code, Throwable err,
                                              long elapsedMs, String taskId, String principal) {
        return TaskResult.of(Status.RECOVERED, value, code, err, elapsedMs, taskId, principal);
    }

    public static <T> TaskResult<T> failure(TaskErrorCode code, Throwable err,
                                            long elapsedMs, String taskId, String principal) {
        return TaskResult.of(Status.FAILURE, null, code, err, elapsedMs, taskId, principal);
    }

    public boolean isSuccess()   { return status == Status.SUCCESS; }
    public boolean isRecovered() { return status == Status.RECOVERED; }
    public boolean isFailure()   { return status == Status.FAILURE; }

    // --- Lombok @Getter already generates getValue(), getErrorCode(), getError(), etc. ---
    // These are the nullable-safe / Optional-wrapped convenience variants.

    public T getValueOrNull() { return value; }
    public T orElse(T other)  { return value != null ? value : other; }

    public Optional<T>             valueOpt() { return Optional.ofNullable(value); }
    public Optional<TaskErrorCode> errorCodeOpt() { return Optional.ofNullable(errorCode); }
    public Optional<Throwable>     errorOpt() { return Optional.ofNullable(error); }
}
