package zac.demo.api.audit;

import java.time.Instant;

/**
 * Structured audit event handed to every AuditSink.
 *
 * Designed to be sink-agnostic: a file/console sink renders it as text,
 * a future database sink maps fields to columns, an Elasticsearch sink
 * serializes it to JSON.
 *
 * Some fields are null depending on event type — see AuditEventType.
 */
public record AuditEvent(
        Instant timestamp,
        AuditEventType type,
        String className,
        String methodName,
        String argsFormatted,      // populated on ENTRY
        String resultFormatted,    // populated on EXIT
        String exceptionType,      // populated on ERROR
        String exceptionMessage,   // populated on ERROR
        Long durationMs            // populated on EXIT and ERROR
) {
}
