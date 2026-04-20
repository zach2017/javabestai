package com.example.tasks;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reusable generic Task wrapper built on CompletableFuture with:
 *  - Lombok-based fluent builder
 *  - Configurable timeout & retry-with-backoff
 *  - Fallback function on failure
 *  - Authorization check (any-of authorities) via {@link TaskAuthorizer}
 *  - Audit events at every phase via {@link TaskAuditor}
 *
 * Example:
 * <pre>
 *   Task.&lt;String&gt;builder()
 *       .name("fetch-user")
 *       .timeout(Duration.ofSeconds(5))
 *       .maxRetries(3)
 *       .retryBackoff(Duration.ofMillis(200))
 *       .requireAuthenticated(true)
 *       .requiredAuthority("ROLE_USER_READ")
 *       .fallback(ex -&gt; "guest")
 *       .authorizer(authorizer)
 *       .auditor(auditor)
 *       .build()
 *       .execute(() -&gt; userService.load(id));
 * </pre>
 */
@Slf4j
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Task<T> {

    @Builder.Default
    private final String name = "unnamed-task";

    @Builder.Default
    private final Duration timeout = Duration.ofSeconds(10);

    @Builder.Default
    private final int maxRetries = 0;

    @Builder.Default
    private final Duration retryBackoff = Duration.ofMillis(100);

    private final Function<Throwable, T> fallback;

    @Builder.Default
    private final Executor executor = ForkJoinPool.commonPool();

    // ---- security ----
    @Builder.Default
    private final boolean requireAuthenticated = false;

    /** Any-of authorities. Empty = no authority check. */
    @Singular("requiredAuthority")
    private final Set<String> requiredAuthorities;

    @NonNull
    private final TaskAuthorizer authorizer;

    @NonNull
    private final TaskAuditor auditor;

    // -------- public API --------

    public CompletableFuture<TaskResult<T>> execute(Supplier<T> supplier) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        final String taskId = UUID.randomUUID().toString();
        final long start = System.currentTimeMillis();
        final String principal = safePrincipal();

        // Authorization check up-front, before we spend any work.
        try {
            authorizer.authorize(requiredAuthorities, requireAuthenticated);
        } catch (TaskAuthorizationException ae) {
            auditor.record(TaskAuditEvent.builder()
                    .taskId(taskId).taskName(name)
                    .phase(TaskAuditEvent.Phase.AUTH_DENIED)
                    .timestamp(Instant.now())
                    .principal(principal)
                    .requiredPermission(String.join(",", safeAuthorities()))
                    .errorCode(ae.getCode())
                    .errorMessage(ae.getMessage())
                    .build());
            return CompletableFuture.completedFuture(
                    TaskResult.<T>failure(ae.getCode(), ae, 0L, taskId, principal));
        }

        auditor.record(TaskAuditEvent.builder()
                .taskId(taskId).taskName(name)
                .phase(TaskAuditEvent.Phase.STARTED)
                .timestamp(Instant.now())
                .principal(principal)
                .requiredPermission(String.join(",", safeAuthorities()))
                .build());

        return runWithRetry(supplier, 0, taskId, principal)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((value, error) -> buildResult(value, error, start, taskId, principal));
    }

    // -------- internals --------

    private CompletableFuture<T> runWithRetry(Supplier<T> supplier, int attempt,
                                              String taskId, String principal) {
        return CompletableFuture.supplyAsync(supplier, executor)
                .handle((value, error) -> {
                    if (error == null) {
                        return CompletableFuture.completedFuture(value);
                    }
                    Throwable cause = unwrap(error);
                    if (attempt < maxRetries && isRetryable(cause)) {
                        long delayMs = retryBackoff.toMillis() * (1L << attempt);
                        auditor.record(TaskAuditEvent.builder()
                                .taskId(taskId).taskName(name)
                                .phase(TaskAuditEvent.Phase.RETRY)
                                .timestamp(Instant.now())
                                .principal(principal)
                                .attempt(attempt + 1)
                                .errorMessage(cause.toString())
                                .build());
                        log.warn("Task '{}' attempt {} failed: {} — retrying in {}ms",
                                name, attempt + 1, cause.toString(), delayMs);
                        return delay(delayMs)
                                .thenCompose(v -> runWithRetry(supplier, attempt + 1, taskId, principal));
                    }
                    CompletableFuture<T> failed = new CompletableFuture<>();
                    failed.completeExceptionally(cause);
                    return failed;
                })
                .thenCompose(Function.identity());
    }

    private TaskResult<T> buildResult(T value, Throwable error, long start,
                                      String taskId, String principal) {
        long elapsed = System.currentTimeMillis() - start;
        if (error == null) {
            TaskResult<T> ok = TaskResult.success(value, elapsed, taskId, principal);
            auditor.record(TaskAuditEvent.builder()
                    .taskId(taskId).taskName(name)
                    .phase(TaskAuditEvent.Phase.COMPLETED)
                    .timestamp(Instant.now())
                    .principal(principal)
                    .elapsedMs(elapsed)
                    .resultStatus(ok.getStatus())
                    .build());
            return ok;
        }
        Throwable cause = unwrap(error);
        TaskErrorCode code = classify(cause);
        log.error("Task '{}' failed after {}ms with code {}: {}", name, elapsed, code, cause.toString());

        if (fallback != null) {
            try {
                T fv = fallback.apply(cause);
                TaskResult<T> r = TaskResult.recovered(fv, code, cause, elapsed, taskId, principal);
                auditor.record(TaskAuditEvent.builder()
                        .taskId(taskId).taskName(name)
                        .phase(TaskAuditEvent.Phase.COMPLETED)
                        .timestamp(Instant.now())
                        .principal(principal)
                        .elapsedMs(elapsed)
                        .errorCode(code)
                        .errorMessage(cause.toString())
                        .resultStatus(r.getStatus())
                        .build());
                return r;
            } catch (Exception fe) {
                log.error("Task '{}' fallback also failed", name, fe);
                return recordFailure(fe, TaskErrorCode.EXECUTION_ERROR, elapsed, taskId, principal);
            }
        }
        return recordFailure(cause, code, elapsed, taskId, principal);
    }

    private TaskResult<T> recordFailure(Throwable cause, TaskErrorCode code, long elapsed,
                                        String taskId, String principal) {
        TaskResult<T> fail = TaskResult.failure(code, cause, elapsed, taskId, principal);
        auditor.record(TaskAuditEvent.builder()
                .taskId(taskId).taskName(name)
                .phase(TaskAuditEvent.Phase.FAILED)
                .timestamp(Instant.now())
                .principal(principal)
                .elapsedMs(elapsed)
                .errorCode(code)
                .errorMessage(cause.toString())
                .resultStatus(fail.getStatus())
                .build());
        return fail;
    }

    private String safePrincipal() {
        try { return authorizer.currentPrincipal(); }
        catch (Exception e) { return "unknown"; }
    }

    private Set<String> safeAuthorities() {
        return requiredAuthorities == null ? Set.of() : requiredAuthorities;
    }

    private static TaskErrorCode classify(Throwable t) {
        if (t instanceof TaskAuthorizationException tae) return tae.getCode();
        if (t instanceof TimeoutException)       return TaskErrorCode.TIMEOUT;
        if (t instanceof CancellationException)  return TaskErrorCode.CANCELLED;
        if (t instanceof InterruptedException)   return TaskErrorCode.INTERRUPTED;
        if (t instanceof IllegalArgumentException) return TaskErrorCode.BAD_INPUT;
        return TaskErrorCode.EXECUTION_ERROR;
    }

    private static boolean isRetryable(Throwable t) {
        return !(t instanceof IllegalArgumentException
                || t instanceof CancellationException
                || t instanceof TaskAuthorizationException);
    }

    private static Throwable unwrap(Throwable t) {
        return (t instanceof CompletionException && t.getCause() != null) ? t.getCause() : t;
    }

    private static CompletableFuture<Void> delay(long ms) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS).execute(() -> cf.complete(null));
        return cf;
    }
}
