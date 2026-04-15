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
     *
     * Two auth mechanisms are wired in side by side:
     *   - formLogin — browser users hitting a protected URL get redirected
     *     to /login (Spring Security's default auto-generated form).
     *   - httpBasic — API clients (curl, Postman, fetch with an
     *     Authorization header) get a 401 + WWW-Authenticate: Basic.
     *
     * Spring picks based on the Accept header: text/html → form, anything
     * else → Basic.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF protection is disabled because curl/Postman calls with
                // Basic auth shouldn't need a token. The form-login POST is
                // still safe because Spring Security's default login form
                // submits to the same origin.
                .csrf(csrf -> csrf.disable())
                // Form login needs a session to remember the authenticated user;
                // IF_REQUIRED is the default and what we want.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // Static / landing pages and the login form remain public.
                        .requestMatchers("/", "/index.html", "/tailwinds.css",
                                "/error", "/favicon.ico",
                                "/login", "/logout").permitAll()
                        // Everything else (notably /api/**) requires authentication.
                        .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .logout(Customizer.withDefaults())
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
