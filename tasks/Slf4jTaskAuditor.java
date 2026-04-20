package com.example.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default SLF4J-backed auditor. Structured key=value format so it's
 * easy to parse in log aggregators (Splunk / ELK / Datadog).
 */
@Slf4j
@Component
@ConditionalOnMissingBean(TaskAuditor.class)
public class Slf4jTaskAuditor implements TaskAuditor {

    @Override
    public void record(TaskAuditEvent e) {
        log.info("AUDIT taskId={} task={} phase={} principal={} perm={} attempt={} elapsedMs={} code={} status={} err={}",
                e.getTaskId(),
                e.getTaskName(),
                e.getPhase(),
                e.getPrincipal(),
                e.getRequiredPermission(),
                e.getAttempt(),
                e.getElapsedMs(),
                e.getErrorCode(),
                e.getResultStatus(),
                e.getErrorMessage());
    }
}
