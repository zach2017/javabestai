package com.example.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Example Spring service showing the full feature set:
 *   - authorization (ROLE_USER_READ required)
 *   - timeout + retry
 *   - fallback value
 *   - auditing happens automatically
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExampleTaskService {

    private final TaskFactory taskFactory;

    public CompletableFuture<TaskResult<String>> fetchUserName(long userId) {
        return taskFactory.<String>newTask()
                .name("fetch-user-name")
                .timeout(Duration.ofSeconds(3))
                .maxRetries(2)
                .retryBackoff(Duration.ofMillis(150))
                .requireAuthenticated(true)
                .requiredAuthority("ROLE_USER_READ")
                .fallback(ex -> "guest")
                .build()
                .execute(() -> {
                    if (userId < 0) throw new IllegalArgumentException("bad id");
                    return "user-" + userId;
                });
    }

    public CompletableFuture<TaskResult<Integer>> adminOnlyCompute() {
        return taskFactory.<Integer>newTask()
                .name("admin-compute")
                .timeout(Duration.ofSeconds(2))
                .requireAuthenticated(true)
                .requiredAuthority("ROLE_ADMIN")
                .requiredAuthority("SCOPE_compute:write")  // any-of
                .build()
                .execute(() -> 42);
    }
}
