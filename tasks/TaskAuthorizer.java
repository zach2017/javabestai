package com.example.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Checks whether the current security principal can run a task.
 * The default implementation reads {@link SecurityContextHolder} and
 * validates against required authorities/roles.
 *
 * Replace with a custom bean to hook into your own RBAC / ABAC engine.
 */
public interface TaskAuthorizer {

    /** Principal name for audit / logs; "anonymous" if unauthenticated. */
    String currentPrincipal();

    /**
     * Throws {@link TaskAuthorizationException} if the caller cannot run.
     *
     * @param requiredAuthorities set of authority strings (any-of semantics).
     *                            Empty set means "no auth required".
     * @param requireAuthenticated if true, anonymous callers are rejected
     *                             even when requiredAuthorities is empty.
     */
    void authorize(Set<String> requiredAuthorities, boolean requireAuthenticated);

    // -------- default Spring Security implementation --------
    @Slf4j
    @Component
    @ConditionalOnMissingBean(TaskAuthorizer.class)
    class SpringSecurityTaskAuthorizer implements TaskAuthorizer {

        @Override
        public String currentPrincipal() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return "anonymous";
            }
            return auth.getName();
        }

        @Override
        public void authorize(Set<String> requiredAuthorities, boolean requireAuthenticated) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean authenticated = auth != null
                    && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal());

            if (requireAuthenticated && !authenticated) {
                throw TaskAuthorizationException.unauthenticated();
            }
            if (requiredAuthorities == null || requiredAuthorities.isEmpty()) {
                return;
            }
            if (!authenticated) {
                throw TaskAuthorizationException.unauthenticated();
            }
            Set<String> held = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());

            boolean ok = requiredAuthorities.stream().anyMatch(held::contains);
            if (!ok) {
                throw TaskAuthorizationException.forbidden(String.join(",", requiredAuthorities));
            }
        }
    }
}
