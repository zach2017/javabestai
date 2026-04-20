package com.example.tasks;

/**
 * Pluggable audit sink. Implementations can log, push to Kafka,
 * persist to DB, send to Splunk, etc.
 */
@FunctionalInterface
public interface TaskAuditor {
    void record(TaskAuditEvent event);
}
