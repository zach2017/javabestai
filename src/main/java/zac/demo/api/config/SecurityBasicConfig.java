package zac.demo.api.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration loaded only when the {@code security-basic} profile is active.
 *
 * Provides:
 *   - {@link SecurityFilterChain} — HTTP Basic auth on /api/**, public on /, the
 *     Tailwind asset, and Spring's static index page.
 *   - {@link UserDetailsService} — single in-memory user from
 *     {@link SecurityUserProperties}.
 *   - {@link PasswordEncoder} — BCrypt.
 *   - {@link WebSecurityCustomizer} — bypasses the security filter chain
 *     entirely for the springdoc/swagger endpoints so the API docs remain
 *     readable without credentials.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@Profile("security-basic")
@RequiredArgsConstructor
public class SecurityBasicConfig {

    private final SecurityUserProperties userProps;

    @PostConstruct
    void announce() {
        log.warn("=== [security-basic] PROFILE ACTIVE — HTTP Basic enforced on /api/** ===");
        log.warn("=== Static user: {} (roles={}) ===", userProps.getName(), userProps.getRoles());
    }

    /**
     * The single SecurityFilterChain bean. Replaces Spring Boot's default
     * (which would also enforce auth, but with a generated password).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF protection is not useful for a stateless JSON API
                // accessed with Basic auth. Disable it to keep curl/etc. simple.
                .csrf(csrf -> csrf.disable())
                // Basic auth doesn't need a session.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Static / landing pages remain public.
                        .requestMatchers("/", "/index.html", "/tailwinds.css",
                                "/error", "/favicon.ico").permitAll()
                        // Everything else (notably /api/**) requires authentication.
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    /**
     * In-memory user store seeded from SecurityUserProperties.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails user = User.withUsername(userProps.getName())
                .password(encoder.encode(userProps.getPassword()))
                .roles(userProps.getRoles().split("\\s*,\\s*"))
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * WebSecurityCustomizer tells Spring Security to NOT install the security
     * filter chain at all for the listed paths. Use this for paths that should
     * bypass authentication AND the security context entirely (vs.
     * {@code .permitAll()} in the filter chain, which still runs every filter).
     *
     * Here we apply it to the swagger / OpenAPI endpoints so the docs stay
     * readable without credentials, even when the rest of the API is locked down.
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(
                "/v3/api-docs",
                "/v3/api-docs/**",
                "/v3/api-docs.yaml",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/swagger-resources/**"
        );
    }
}
