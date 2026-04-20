package com.demo.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public Map<String, Object> hello(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "message", "Hello World",
            "user", jwt.getClaimAsString("preferred_username"),
            "subject", jwt.getSubject(),
            "roles", extractRoles(jwt)
        );
    }

    @GetMapping("/hello/admin")
    @PreAuthorize("hasRole('Admin')")
    public Map<String, Object> helloAdmin(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "message", "Hello Admin — you have privileged access",
            "user", jwt.getClaimAsString("preferred_username")
        );
    }

    @GetMapping("/hello/user")
    @PreAuthorize("hasRole('User')")
    public Map<String, Object> helloUser(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "message", "Hello User",
            "user", jwt.getClaimAsString("preferred_username")
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || realmAccess.get("roles") == null) {
            return List.of();
        }
        return (List<String>) realmAccess.get("roles");
    }
}
