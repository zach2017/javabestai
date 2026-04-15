package zac.demo.api.audit.sink;

import zac.demo.api.audit.AuditEvent;

/**
 * Strategy for persisting audit events.
 *
 * The aspect publishes every event to all AuditSink beans in the context.
 * To add a new sink (database, Elasticsearch, Kafka, etc.), implement this
 * interface, mark the implementation with {@code @Component} plus a
 * {@code @ConditionalOnProperty} so it can be toggled, and that's it —
 * the aspect requires no changes.
 */
public interface AuditSink {

    /**
     * Persist or forward a single audit event.
     * Implementations should never throw — failure to record an audit
     * event must not break the audited request.
     */
    void record(AuditEvent event);
}
