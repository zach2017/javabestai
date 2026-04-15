package zac.demo.api.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Audit aspect — logs every public call into:
 *
 *   - our own @RestController, @RestControllerAdvice and @Service beans
 *   - Springdoc's @Controller / @RestController beans that serve
 *     /swagger-ui/** and /v3/api-docs (so Swagger access is also audited).
 *
 * Each invocation produces:
 *
 *   →  Class.method(args...)                  (entry)
 *   ←  Class.method returned <result> [Xms]   (success)
 *   ✗  Class.method threw Type: msg [Xms]     (failure, exception is rethrown)
 *
 * Long arg/result strings are truncated to keep the log readable.
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private static final int MAX_PRINT_LENGTH = 200;

    /** Our own MVC stereotypes plus Spring services. */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController) "
            + "|| @within(org.springframework.web.bind.annotation.RestControllerAdvice) "
            + "|| @within(org.springframework.stereotype.Service)")
    public void appComponents() {
    }

    /** Springdoc's controllers (swagger-ui welcome page, /v3/api-docs, etc.). */
    @Pointcut("within(org.springdoc..*)")
    public void swaggerComponents() {
    }

    @Around("appComponents() || swaggerComponents()")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String argsStr = formatArgs(pjp.getArgs());

        long startNs = System.nanoTime();
        log.info("→ {}.{}({})", className, methodName, argsStr);

        try {
            Object result = pjp.proceed();
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("← {}.{} returned {} [{}ms]",
                    className, methodName, formatValue(result), elapsedMs);
            return result;
        } catch (Throwable t) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.warn("✗ {}.{} threw {}: {} [{}ms]",
                    className, methodName,
                    t.getClass().getSimpleName(), t.getMessage(), elapsedMs);
            throw t;
        }
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
        return s.length() > MAX_PRINT_LENGTH
                ? s.substring(0, MAX_PRINT_LENGTH) + "...(+" + (s.length() - MAX_PRINT_LENGTH) + " chars)"
                : s;
    }
}
