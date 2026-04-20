package com.example.tasks;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Example of using TemporalTask to submit a workflow asynchronously and
 * handle every possible Temporal failure via TaskResult.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSubmissionService {

    private static final String TASK_QUEUE = "ORDERS_TQ";

    private final WorkflowClient workflowClient;
    private final TaskFactory taskFactory;

    @WorkflowInterface
    public interface OrderWorkflow {
        @WorkflowMethod
        String submit(String orderId);
    }

    public CompletableFuture<TaskResult<String>> submitOrder(String orderId) {
        // 1) Build a typed stub with all the Temporal-side policies.
        OrderWorkflow stub = workflowClient.newWorkflowStub(
                OrderWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("order-" + orderId)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(10))
                        .setWorkflowRunTimeout(Duration.ofMinutes(5))
                        .build());

        // 2) Wrap WorkflowClient.execute() in a TemporalTask for auth,
        //    audit, client-side deadline, and unified TaskResult handling.
        return taskFactory.<String>newTemporalTask()
                .name("submit-order")
                .timeout(Duration.ofMinutes(2))            // client-side deadline
                .clientMaxRetries(2)                       // only retries infra failures
                .clientRetryBackoff(Duration.ofMillis(500))
                .requireAuthenticated(true)
                .requiredAuthority("ROLE_ORDER_WRITE")
                .fallback(err -> "QUEUED_FOR_LATER")       // graceful degradation
                .build()
                .execute(() -> WorkflowClient.execute(stub::submit, orderId));
    }

    /**
     * Example of consuming the result and branching on Temporal-specific
     * error codes.
     */
    public void handleSubmission(String orderId) {
        submitOrder(orderId).thenAccept(result -> {
            if (result.isSuccess()) {
                log.info("Order {} submitted: {}", orderId, result.getValueOrNull());
                return;
            }
            TaskErrorCode code = result.errorCodeOpt().orElse(TaskErrorCode.EXECUTION_ERROR);
            switch (code) {
                case FORBIDDEN, UNAUTHENTICATED
                        -> log.warn("Access denied for order {}: {}", orderId, code);
                case WORKFLOW_ALREADY_STARTED
                        -> log.info("Order {} already in flight — idempotent no-op", orderId);
                case WORKFLOW_TIMED_OUT, TIMEOUT
                        -> log.warn("Order {} timed out — will retry via outbox", orderId);
                case ACTIVITY_NON_RETRYABLE
                        -> log.error("Order {} has unrecoverable business error", orderId);
                case ACTIVITY_FAILED, WORKFLOW_FAILED
                        -> log.error("Order {} workflow failed after Temporal retries", orderId);
                case SERVICE_UNAVAILABLE, WORKER_UNAVAILABLE
                        -> log.error("Temporal infra issue — order {} not submitted", orderId);
                default
                        -> log.error("Unexpected failure for order {}: {}", orderId, code);
            }
        });
    }
}
