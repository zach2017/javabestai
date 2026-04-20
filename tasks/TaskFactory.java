package com.example.tasks;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory that injects {@link TaskAuthorizer} and {@link TaskAuditor} into
 * task builders so callers never have to wire them manually.
 */
@Component
@RequiredArgsConstructor
public class TaskFactory {

    private final TaskAuthorizer authorizer;
    private final TaskAuditor auditor;

    /** Plain local task. */
    public <T> Task.TaskBuilder<T> newTask() {
        return Task.<T>builder()
                .authorizer(authorizer)
                .auditor(auditor);
    }

    /** Temporal-backed task wrapping a WorkflowClient.execute(...) call. */
    public <T> TemporalTask.TemporalTaskBuilder<T> newTemporalTask() {
        return TemporalTask.<T>builder()
                .authorizer(authorizer)
                .auditor(auditor);
    }
}
