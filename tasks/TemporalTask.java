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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client-side wrapper that submits work to a Temporal Worker and returns
 * {@code CompletableFuture<TaskResult<T>>}. It maps Temporal's rich failure
 * hierarchy (WorkflowFailedException -> ActivityFailure -> ApplicationFailure,
 * TimeoutFailure, TerminatedFailure, CanceledFailure, gRPC
 * WorkflowServiceException, etc.) into {@link TaskErrorCode} + {@link TaskResult}.
 *
 * Usage:
 * <pre>
 *   // Create a typed workflow stub once.
 *   OrderWorkflow stub = workflowClient.newWorkflowStub(
 *       OrderWorkflow.class,
 *       WorkflowOptions.newBuilder()
 *           .setTaskQueue("ORDERS_TQ")
 *           .setWorkflowId("order-" + orderId)
 *           .setWorkflowExecutionTimeout(Duration.ofMinutes(10))
 *           // Server-side retry of the workflow itself (rare - usually left null)
 *           // .setRetryOptions(RetryOptions.newBuilder()...build())
 *           .build());
 *
 *   // Submit asynchronously. WorkflowClient.execute returns CompletableFuture&lt;R&gt;.
 *   TemporalTask.&lt;String&gt;builder()
 *       .name("submit-order")
 *       .timeout(Duration.ofMinutes(2))           // client-side deadline
 *       .requiredAuthority("ROLE_ORDER_WRITE")
 *       .authorizer(authorizer).auditor(auditor)
 *       .fallback(err -&gt; "QUEUED_FOR_LATER")
 *       .build()
 *       .execute(() -&gt; WorkflowClient.execute(stub::submit, orderId));
 * </pre>
 *
 * Design notes:
 *  - Client-side retry is OFF by default. Temporal already handles retries
 *    for activities (ActivityOptions.RetryPolicy) and optionally workflows.
 *    Only enable clientMaxRetries for transient infrastructure failures like
 *    SERVICE_UNAVAILABLE / WORKER_UNAVAILABLE where the workflow never started.
 *  - {@code timeout} is a client-side deadline on the returned future. It does
 *    NOT stop the workflow — set {@code setWorkflowExecutionTimeout} on
 *    {@code WorkflowOptions} for that.
 */
@Slf4j
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TemporalTask<T> {

    @Builder.Default
    private final String name = "unnamed-temporal-task";

    @Builder.Default
    private final Duration timeout = Duration.ofMinutes(5);

    @Builder.Default
    private final int clientMaxRetries = 0;

    @Builder.Default
    private final Duration clientRetryBackoff = Duration.ofMillis(250);

    private final Function<Throwable, T> fallback;

    // ---- security / audit ----
    @Builder.Default
    private final boolean requireAuthenticated = false;

    @Singular("requiredAuthority")
    private final Set<String> requiredAuthorities;

    @NonNull private final TaskAuthorizer authorizer;
    @NonNull private final TaskAuditor auditor;

    // -------- public API --------

    /**
     * Submit a Temporal workflow call. The {@code submitter} should invoke
     * {@code WorkflowClient.execute(stub::method, args)} (or any other
     * method returning a {@code CompletableFuture<T>} from the SDK).
     */
    public CompletableFuture<TaskResult<T>> execute(Supplier<CompletableFuture<T>> submitter) {
        final String taskId = UUID.randomUUID().toString();
        final long start = System.currentTimeMillis();
        final String principal = safePrincipal();

        // 1) Authorization check — BEFORE contacting Temporal.
        try {
            authorizer.authorize(requiredAuthorities, requireAuthenticated);
        } catch (TaskAuthorizationException ae) {
            audit(TaskAuditEvent.Phase.AUTH_DENIED, taskId, principal,
                    null, ae.getCode(), ae.getMessage(), null);
            return CompletableFuture.completedFuture(
                    TaskResult.<T>failure(ae.getCode(), ae, 0L, taskId, principal));
        }

        audit(TaskAuditEvent.Phase.STARTED, taskId, principal,
                null, null, null, null);

        return submitWithRetry(submitter, 0, taskId, principal)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((value, err) -> buildResult(value, err, start, taskId, principal));
    }

    // -------- internals --------

    /**
     * Retry ONLY around transient infra (service/worker unavailable) where the
     * workflow never started. Never retry WORKFLOW_FAILED / ACTIVITY_FAILED —
     * those are deterministic business outcomes from the workflow itself.
     */
    private CompletableFuture<T> submitWithRetry(Supplier<CompletableFuture<T>> submitter,
                                                 int attempt, String taskId, String principal) {
        CompletableFuture<T> first;
        try {
            first = submitter.get();
            if (first == null) {
                first = CompletableFuture.failedFuture(
                        new IllegalStateException("submitter returned null future"));
            }
        } catch (Throwable t) {
            first = CompletableFuture.failedFuture(t);
        }

        return first.handle((value, err) -> {
            if (err == null) return CompletableFuture.completedFuture(value);
            Throwable cause = unwrap(err);
            TemporalFailureClassifier.Classified c = TemporalFailureClassifier.classify(cause);

            boolean transientInfra =
                    c.getCode() == TaskErrorCode.SERVICE_UNAVAILABLE
                            || c.getCode() == TaskErrorCode.WORKER_UNAVAILABLE;

            if (attempt < clientMaxRetries && transientInfra) {
                long delayMs = clientRetryBackoff.toMillis() * (1L << attempt);
                audit(TaskAuditEvent.Phase.RETRY, taskId, principal,
                        attempt + 1, c.getCode(), cause.toString(), null);
                return delay(delayMs).thenCompose(
                        v -> submitWithRetry(submitter, attempt + 1, taskId, principal));
            }
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(cause);
            return failed;
        }).thenCompose(Function.identity());
    }

    private TaskResult<T> buildResult(T value, Throwable error, long start,
                                      String taskId, String principal) {
        long elapsed = System.currentTimeMillis() - start;
        if (error == null) {
            TaskResult<T> ok = TaskResult.success(value, elapsed, taskId, principal);
            audit(TaskAuditEvent.Phase.COMPLETED, taskId, principal,
                    null, null, null, ok.getStatus());
            return ok;
        }

        Throwable cause = unwrap(error);
        TemporalFailureClassifier.Classified c = TemporalFailureClassifier.classify(cause);
        log.error("Temporal task '{}' failed after {}ms code={} type={} cause={}",
                name, elapsed, c.getCode(), c.getFailureType(), c.getRootCause().toString());

        if (fallback != null) {
            try {
                T fv = fallback.apply(c.getRootCause());
                TaskResult<T> r = TaskResult.recovered(fv, c.getCode(),
                        c.getRootCause(), elapsed, taskId, principal);
                audit(TaskAuditEvent.Phase.COMPLETED, taskId, principal,
                        null, c.getCode(), c.getRootCause().toString(), r.getStatus());
                return r;
            } catch (Exception fe) {
                log.error("Temporal task '{}' fallback also failed", name, fe);
                return recordFailure(fe, TaskErrorCode.EXECUTION_ERROR, elapsed, taskId, principal);
            }
        }
        return recordFailure(c.getRootCause(), c.getCode(), elapsed, taskId, principal);
    }

    private TaskResult<T> recordFailure(Throwable cause, TaskErrorCode code, long elapsed,
                                        String taskId, String principal) {
        TaskResult<T> fail = TaskResult.failure(code, cause, elapsed, taskId, principal);
        audit(TaskAuditEvent.Phase.FAILED, taskId, principal,
                null, code, cause.toString(), fail.getStatus());
        return fail;
    }

    private void audit(TaskAuditEvent.Phase phase, String taskId, String principal,
                       Integer attempt, TaskErrorCode code, String msg,
                       TaskResult.Status status) {
        auditor.record(TaskAuditEvent.builder()
                .taskId(taskId)
                .taskName(name)
                .phase(phase)
                .timestamp(Instant.now())
                .principal(principal)
                .requiredPermission(requiredAuthorities == null
                        ? "" : String.join(",", requiredAuthorities))
                .attempt(attempt)
                .errorCode(code)
                .errorMessage(msg)
                .resultStatus(status)
                .build());
    }

    private String safePrincipal() {
        try { return authorizer.currentPrincipal(); }
        catch (Exception e) { return "unknown"; }
    }

    private static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static CompletableFuture<Void> delay(long ms) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS).execute(() -> cf.complete(null));
        return cf;
    }
}
