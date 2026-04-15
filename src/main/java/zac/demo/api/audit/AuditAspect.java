package zac.demo.api.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import zac.demo.api.audit.sink.AuditSink;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Audit aspect — intercepts our application stereotypes plus springdoc
 * controllers, builds an AuditEvent for each entry/exit/error, and
 * broadcasts it to all AuditSink beans in the context.
 *
 * Behaviour is controlled at runtime via AuditProperties; sink wiring
 * is controlled at startup via @ConditionalOnProperty on each sink.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private static final String SWAGGER_PACKAGE_PREFIX = "org.springdoc";

    private final AuditProperties props;
    private final List<AuditSink> sinks;

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController) "
            + "|| @within(org.springframework.web.bind.annotation.RestControllerAdvice) "
            + "|| @within(org.springframework.stereotype.Service)")
    public void appComponents() {
    }

    @Pointcut("within(org.springdoc..*)")
    public void swaggerComponents() {
    }

    @Around("appComponents() || swaggerComponents()")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        if (!props.isEnabled()) {
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String declaringTypeName = signature.getDeclaringTypeName();
        boolean isSwaggerCall = declaringTypeName.startsWith(SWAGGER_PACKAGE_PREFIX);
        if (isSwaggerCall && !props.isIncludeSwagger()) {
            return pjp.proceed();
        }

        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String argsStr = props.isLogArgs() ? formatArgs(pjp.getArgs()) : "<args hidden>";

        long startNs = System.nanoTime();
        publish(new AuditEvent(
                Instant.now(), AuditEventType.ENTRY,
                className, methodName, argsStr,
                null, null, null, null
        ));

        try {
            Object result = pjp.proceed();
            Long elapsed = props.isLogTiming() ? elapsedMs(startNs) : null;
            String resultStr = props.isLogResult() ? formatValue(result) : "<result hidden>";
            publish(new AuditEvent(
                    Instant.now(), AuditEventType.EXIT,
                    className, methodName, null,
                    resultStr, null, null, elapsed
            ));
            return result;
        } catch (Throwable t) {
            Long elapsed = props.isLogTiming() ? elapsedMs(startNs) : null;
            publish(new AuditEvent(
                    Instant.now(), AuditEventType.ERROR,
                    className, methodName, null,
                    null, t.getClass().getSimpleName(), t.getMessage(), elapsed
            ));
            throw t;
        }
    }

    private void publish(AuditEvent event) {
        for (AuditSink sink : sinks) {
            try {
                sink.record(event);
            } catch (Exception e) {
                // Defensive: a misbehaving sink must never break the request.
                log.warn("Audit sink {} failed", sink.getClass().getSimpleName(), e);
            }
        }
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args)
                .map(this::formatValue)
                .collect(Collectors.joining(", "));
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        String s = String.valueOf(value);
        int max = props.getMaxValueLength();
        return s.length() > max
                ? s.substring(0, max) + "...(+" + (s.length() - max) + " chars)"
                : s;
    }
}
