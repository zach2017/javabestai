package zac.demo.api.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All knobs that control the audit aspect, bound from properties under
 * the {@code audit.*} prefix.
 *
 * Defaults are written inline so the application works out of the box
 * without any audit.* properties set.
 */
@Data
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {

    /** Master on/off switch for the entire audit aspect. */
    private boolean enabled = true;

    /** Include method arguments in audit events. Disable for sensitive payloads. */
    private boolean logArgs = true;

    /** Include return values in audit events. */
    private boolean logResult = true;

    /** Include elapsed time in audit events. */
    private boolean logTiming = true;

    /** Maximum length of any single arg/result string before truncation. */
    private int maxValueLength = 200;

    /** When false, springdoc/swagger calls are not audited even though the pointcut matches. */
    private boolean includeSwagger = true;
}
