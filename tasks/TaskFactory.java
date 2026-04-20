package com.example.tasks;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Convenience factory so callers don't have to wire {@link TaskAuthorizer}
 * and {@link TaskAuditor} into every builder by hand.
 *
 * Usage:
 * <pre>
 *   taskFactory.&lt;String&gt;newTask()
 *       .name("fetch-user")
 *       .requireAuthenticated(true)
 *       .requiredAuthority("ROLE_USER_READ")
 *       .build()
 *       .execute(() -&gt; userService.load(id));
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class TaskFactory {

    private final TaskAuthorizer authorizer;
    private final TaskAuditor auditor;

    public <T> Task.TaskBuilder<T> newTask() {
        return Task.<T>builder()
                .authorizer(authorizer)
                .auditor(auditor);
    }
}
