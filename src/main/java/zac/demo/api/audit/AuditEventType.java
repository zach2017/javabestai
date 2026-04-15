package zac.demo.api.audit;

/**
 * The lifecycle phase an AuditEvent represents.
 */
public enum AuditEventType {
    /** Method has been entered; args are populated. */
    ENTRY,
    /** Method returned normally; result and durationMs are populated. */
    EXIT,
    /** Method threw; exceptionType / exceptionMessage / durationMs are populated. */
    ERROR
}
