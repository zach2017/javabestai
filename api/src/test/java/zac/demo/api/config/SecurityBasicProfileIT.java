package zac.demo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zac.demo.api.model.Person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profile test — security-basic.
 *
 * SecurityBasicConfig is loaded; HTTP Basic auth is enforced on /api/**.
 * The static user comes from application-security-basic.properties:
 *   admin / changeme
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-basic")
class SecurityBasicProfileIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext context;

    private static final String VALID_USER = "admin";
    private static final String VALID_PASSWORD = "changeme";

    @Test
    @DisplayName("POST /api/hello returns 401 without credentials (JSON client)")
    void apiCallWithoutCredentialsReturns401() throws Exception {
        Person p = new Person("Ada", 1L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/hello returns 401 with invalid credentials (JSON client)")
    void apiCallWithInvalidCredentialsReturns401() throws Exception {
        Person p = new Person("Ada", 1L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                        .with(httpBasic("admin", "wrongpassword"))
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/hello returns 200 with valid credentials")
    void apiCallWithValidCredentialsReturns200() throws Exception {
        Person p = new Person("Ada", 1L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                        .with(httpBasic(VALID_USER, VALID_PASSWORD))
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Swagger /v3/api-docs is accessible without credentials (WebSecurityCustomizer bypass)")
    void swaggerOpenApiBypassesAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Static landing page / is accessible without credentials")
    void rootPathIsPublic() throws Exception {
        mockMvc.perform(get("/"))
                // 200 (index.html), or 404 if static index not present — both
                // prove auth was not required.
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isNotEqualTo(401);
                });
    }

    @Test
    @DisplayName("SecurityFilterChain bean is registered when security is on")
    void securityFilterChainBeanExists() {
        SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
        assertThat(chain).isNotNull();
    }

    @Test
    @DisplayName("SecurityBasicConfig is loaded; SecurityOffConfig is not")
    void onlySecurityBasicConfigIsLoaded() {
        assertThat(context.containsBean("securityBasicConfig")).isTrue();
        assertThat(context.containsBean("securityOffConfig")).isFalse();
    }

    @Test
    @DisplayName("Browser request (Accept: text/html) without auth redirects to /login")
    void browserRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/hello").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String location = result.getResponse().getHeader("Location");
                    assertThat(location).endsWith("/login");
                });
    }

    @Test
    @DisplayName("GET /login returns the auto-generated login form")
    void loginPageIsAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Please sign in")));
    }

    @Test
    @DisplayName("POST /login with valid credentials authenticates and redirects")
    void formLoginWithValidCredentialsRedirects() throws Exception {
        mockMvc.perform(formLogin("/login").user(VALID_USER).password(VALID_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(VALID_USER));
    }

    @Test
    @DisplayName("POST /login with invalid credentials redirects to /login?error")
    void formLoginWithInvalidCredentialsRedirectsToError() throws Exception {
        mockMvc.perform(formLogin("/login").user(VALID_USER).password("wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated())
                .andExpect(result -> {
                    String location = result.getResponse().getHeader("Location");
                    assertThat(location).contains("/login");
                    assertThat(location).contains("error");
                });
    }

    @Test
    @DisplayName("Static user properties are bound from the profile properties file")
    void staticUserPropertiesArePopulated() {
        SecurityUserProperties props = context.getBean(SecurityUserProperties.class);
        assertThat(props.getName()).isEqualTo("admin");
        assertThat(props.getPassword()).isEqualTo("changeme");
    }
}
