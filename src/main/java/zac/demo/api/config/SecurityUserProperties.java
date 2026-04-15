package zac.demo.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Static user credentials used by SecurityBasicConfig to seed an
 * InMemoryUserDetailsManager.
 *
 * Bound from application-security-basic.properties (or any other source
 * of {@code app.security.user.*}).
 *
 * Defaults are provided so misconfiguration is loud rather than silent —
 * if these were wired to e.g. environment variables and the variables
 * weren't set, you'd at least know the credentials are "admin/changeme"
 * rather than ending up with a generated password.
 */
@Data
@ConfigurationProperties(prefix = "app.security.user")
public class SecurityUserProperties {
    private String name = "admin";
    private String password = "changeme";
    private String roles = "USER";
}
