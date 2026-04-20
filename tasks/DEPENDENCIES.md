# Dependencies

Target: **Java 21**, **Spring Boot 3.x**, **Lombok**, **Spring Security**, **Temporal Java SDK**.

```xml
<properties>
    <java.version>21</java.version>
    <temporal.version>1.28.1</temporal.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>io.temporal</groupId>
        <artifactId>temporal-sdk</artifactId>
        <version>${temporal.version}</version>
    </dependency>
    <!-- Optional: auto-configures WorkflowClient / WorkerFactory beans -->
    <dependency>
        <groupId>io.temporal</groupId>
        <artifactId>temporal-spring-boot-starter</artifactId>
        <version>${temporal.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <source>21</source>
                <target>21</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Error mapping reference

| Temporal exception | TaskErrorCode | Client-retry? |
|---|---|---|
| `WorkflowFailedException` (generic) | `WORKFLOW_FAILED` | No |
| `... -> ActivityFailure -> ApplicationFailure` (retryable) | `ACTIVITY_FAILED` | No |
| `... -> ActivityFailure -> ApplicationFailure` (nonRetryable) | `ACTIVITY_NON_RETRYABLE` | No |
| `... -> TimeoutFailure` | `WORKFLOW_TIMED_OUT` | No |
| `... -> TerminatedFailure` | `WORKFLOW_TERMINATED` | No |
| `... -> CanceledFailure` | `CANCELLED` | No |
| `WorkflowNotFoundException` | `WORKFLOW_NOT_FOUND` | No |
| `WorkflowExecutionAlreadyStarted` | `WORKFLOW_ALREADY_STARTED` | No |
| `WorkflowServiceException` (gRPC UNAVAILABLE) | `SERVICE_UNAVAILABLE` | **Yes** |
| `WorkflowServiceException` (gRPC NOT_FOUND) | `WORKER_UNAVAILABLE` | **Yes** |
| `TimeoutException` (client `orTimeout`) | `TIMEOUT` | No |
| `TaskAuthorizationException` | `UNAUTHENTICATED` / `FORBIDDEN` | No |

## Key design decisions

**Never client-retry business failures.** Temporal already retries activities with backoff via `ActivityOptions.RetryPolicy` and maintains durable state. If a workflow fails after Temporal gave up, it's a deterministic business outcome — client-retrying wastes time. `TemporalTask` only retries on `SERVICE_UNAVAILABLE` / `WORKER_UNAVAILABLE` (the workflow never started).

**Client timeout ≠ workflow timeout.** `TemporalTask#timeout` fires `orTimeout` on the returned future but does **not** stop the workflow. Use `WorkflowOptions.setWorkflowExecutionTimeout()` / `setWorkflowRunTimeout()` for that.

**Use idempotent `WorkflowId`s.** Pair a deterministic ID like `order-{id}` with retry — a retry that races an already-started run produces `WORKFLOW_ALREADY_STARTED`, which the client can treat as a no-op.

**Failures are wrapped, not flattened.** Temporal wraps errors in layers: `WorkflowFailedException → ActivityFailure → ApplicationFailure`. `TemporalFailureClassifier` walks the chain and exposes both a code and the Temporal `type` string (e.g. the `type` argument passed to `ApplicationFailure.newFailure`), which is what you typically route on.
