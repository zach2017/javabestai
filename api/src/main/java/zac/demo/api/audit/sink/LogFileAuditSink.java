package zac.demo.api.audit.sink;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zac.demo.api.audit.AuditEvent;

/**
 * Default audit sink — writes events through SLF4J, which Spring Boot's
 * Logback configuration sends to both console and file (controlled by
 * {@code logging.file.name} in application.properties).
 *
 * Active by default. Disable with {@code audit.sinks.file.enabled=false}.
 *
 * Future sinks (database, Elasticsearch, ...) implement AuditSink the
 * same way — register them as @Component with their own
 * @ConditionalOnProperty toggle. Multiple sinks can be active at once;
 * the aspect emits to all of them.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "audit.sinks.file.enabled", havingValue = "true", matchIfMissing = true)
public class LogFileAuditSink implements AuditSink {

    @Override
    public void record(AuditEvent event) {
        try {
            switch (event.type()) {
                case ENTRY -> log.info("→ {}.{}({})",
                        event.className(), event.methodName(), event.argsFormatted());
                case EXIT -> log.info("← {}.{} returned {}{}",
                        event.className(), event.methodName(),
                        event.resultFormatted(), formatTiming(event.durationMs()));
                case ERROR -> log.warn("✗ {}.{} threw {}: {}{}",
                        event.className(), event.methodName(),
                        event.exceptionType(), event.exceptionMessage(),
                        formatTiming(event.durationMs()));
            }
        } catch (Exception e) {
            // Sinks must never break the audited request.
            log.warn("Failed to write audit event", e);
        }
    }

    private String formatTiming(Long durationMs) {
        return durationMs == null ? "" : " [" + durationMs + "ms]";
    }
}
