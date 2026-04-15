package zac.demo.api.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration loaded only when the {@code security-off} profile is active.
 *
 * The actual disabling of Spring Security happens in
 * {@code application-security-off.properties} via:
 *
 * <pre>
 * spring.autoconfigure.exclude=
 *     org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,
 *     org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,
 *     org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration
 * </pre>
 *
 * That exclusion has to live in the profile-specific properties file because
 * {@code @SpringBootApplication(exclude=...)} would apply globally.
 *
 * This class exists mainly to:
 *   1. Document the profile via a startup log line.
 *   2. Give us a stable place to add profile-specific beans later
 *      (e.g. CORS that's only loose when security is off).
 */
@Slf4j
@Configuration
@Profile("security-off")
public class SecurityOffConfig {

    @PostConstruct
    void announce() {
        log.warn("=== [security-off] PROFILE ACTIVE — all endpoints are public ===");
        log.warn("=== Switch to security-basic for HTTP Basic auth.            ===");
    }
}
