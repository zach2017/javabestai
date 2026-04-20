package com.example.tasks;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowServiceException;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.failure.TerminatedFailure;
import io.temporal.failure.TimeoutFailure;
import lombok.Value;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * Walks the Temporal exception chain to surface the real, actionable cause.
 *
 * Temporal wraps failures in layers:
 *   WorkflowFailedException
 *     -> (optional) ChildWorkflowFailure
 *       -> ActivityFailure
 *         -> ApplicationFailure   (this is usually what you want to act on)
 *
 * This classifier unwraps to the root cause and returns both a
 * {@link TaskErrorCode} and a human-readable failure type string
 * (the {@code type} passed to {@link ApplicationFailure#newFailure}),
 * which is what you typically route on client-side.
 */
public final class TemporalFailureClassifier {

    private TemporalFailureClassifier() {}

    @Value
    public static class Classified {
        TaskErrorCode code;
        /** Temporal ApplicationFailure 'type' if present, else null. */
        String failureType;
        /** The deepest useful cause we could extract. */
        Throwable rootCause;
        boolean nonRetryable;
    }

    public static Classified classify(Throwable raw) {
        Throwable t = unwrap(raw);

        // --- non-Temporal shells first ---
        if (t instanceof TaskAuthorizationException tae) {
            return new Classified(tae.getCode(), null, tae, true);
        }
        if (t instanceof TimeoutException) {
            return new Classified(TaskErrorCode.TIMEOUT, null, t, false);
        }
        if (t instanceof CancellationException) {
            return new Classified(TaskErrorCode.CANCELLED, null, t, true);
        }
        if (t instanceof InterruptedException) {
            return new Classified(TaskErrorCode.INTERRUPTED, null, t, true);
        }

        // --- Temporal client-level exceptions ---
        if (t instanceof WorkflowNotFoundException) {
            return new Classified(TaskErrorCode.WORKFLOW_NOT_FOUND, null, t, true);
        }
        if (t instanceof WorkflowExecutionAlreadyStarted) {
            return new Classified(TaskErrorCode.WORKFLOW_ALREADY_STARTED, null, t, true);
        }
        if (t instanceof WorkflowServiceException wse) {
            // Frontend gRPC error - could be service down, rate limited, etc.
            Throwable grpc = findGrpc(wse);
            if (grpc instanceof StatusRuntimeException sre) {
                Status.Code c = sre.getStatus().getCode();
                if (c == Status.Code.UNAVAILABLE || c == Status.Code.DEADLINE_EXCEEDED) {
                    return new Classified(TaskErrorCode.SERVICE_UNAVAILABLE, null, sre, false);
                }
                if (c == Status.Code.NOT_FOUND) {
                    return new Classified(TaskErrorCode.WORKER_UNAVAILABLE, null, sre, false);
                }
            }
            return new Classified(TaskErrorCode.SERVICE_UNAVAILABLE, null, wse, false);
        }

        // --- workflow threw ---
        if (t instanceof WorkflowFailedException wfe) {
            return classifyWorkflowFailure(wfe);
        }

        // --- raw TemporalFailure types (from workflow code, updates, etc.) ---
        if (t instanceof TimeoutFailure tf) {
            return new Classified(TaskErrorCode.WORKFLOW_TIMED_OUT,
                    "TimeoutFailure:" + tf.getTimeoutType(), tf, true);
        }
        if (t instanceof TerminatedFailure) {
            return new Classified(TaskErrorCode.WORKFLOW_TERMINATED, null, t, true);
        }
        if (t instanceof CanceledFailure) {
            return new Classified(TaskErrorCode.CANCELLED, null, t, true);
        }
        if (t instanceof ActivityFailure af) {
            return classifyActivity(af);
        }
        if (t instanceof ChildWorkflowFailure cwf) {
            if (cwf.getCause() != null) {
                return classify(cwf.getCause());
            }
            return new Classified(TaskErrorCode.WORKFLOW_FAILED, null, cwf, true);
        }
        if (t instanceof ApplicationFailure appf) {
            return fromApplicationFailure(appf);
        }

        // --- generic Temporal shell or anything else ---
        if (t instanceof WorkflowException) {
            return new Classified(TaskErrorCode.WORKFLOW_FAILED, null, t, true);
        }
        return new Classified(TaskErrorCode.EXECUTION_ERROR, null, t, false);
    }

    private static Classified classifyWorkflowFailure(WorkflowFailedException wfe) {
        Throwable cause = wfe.getCause();
        if (cause == null) {
            return new Classified(TaskErrorCode.WORKFLOW_FAILED, null, wfe, true);
        }
        // Recurse — cause could be ActivityFailure -> ApplicationFailure, etc.
        Classified inner = classify(cause);
        // If we bottomed out at a generic execution error but the outer shell
        // was WorkflowFailedException, report it as WORKFLOW_FAILED.
        if (inner.code == TaskErrorCode.EXECUTION_ERROR) {
            return new Classified(TaskErrorCode.WORKFLOW_FAILED,
                    inner.failureType, inner.rootCause, true);
        }
        return inner;
    }

    private static Classified classifyActivity(ActivityFailure af) {
        Throwable inner = af.getCause();
        if (inner instanceof ApplicationFailure appf) {
            Classified c = fromApplicationFailure(appf);
            // Preserve Temporal nuance: wrap non-retryable vs retryable activity failures
            if (c.code == TaskErrorCode.EXECUTION_ERROR) {
                TaskErrorCode code = appf.isNonRetryable()
                        ? TaskErrorCode.ACTIVITY_NON_RETRYABLE
                        : TaskErrorCode.ACTIVITY_FAILED;
                return new Classified(code, appf.getType(), appf, appf.isNonRetryable());
            }
            return c;
        }
        if (inner instanceof TimeoutFailure tf) {
            return new Classified(TaskErrorCode.WORKFLOW_TIMED_OUT,
                    "TimeoutFailure:" + tf.getTimeoutType(), tf, true);
        }
        return new Classified(TaskErrorCode.ACTIVITY_FAILED, null,
                inner != null ? inner : af, false);
    }

    private static Classified fromApplicationFailure(ApplicationFailure appf) {
        TaskErrorCode code = appf.isNonRetryable()
                ? TaskErrorCode.ACTIVITY_NON_RETRYABLE
                : TaskErrorCode.ACTIVITY_FAILED;
        return new Classified(code, appf.getType(), appf, appf.isNonRetryable());
    }

    private static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static Throwable findGrpc(Throwable t) {
        Throwable c = t;
        while (c != null) {
            if (c instanceof StatusRuntimeException) return c;
            c = c.getCause();
        }
        return t;
    }
}
