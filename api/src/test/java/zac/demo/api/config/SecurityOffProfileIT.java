package zac.demo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profile test — security-off.
 *
 * Because application-security-off.properties excludes
 * SecurityAutoConfiguration, no SecurityFilterChain bean exists and no
 * authentication is required for any endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-off")
class SecurityOffProfileIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("POST /api/hello succeeds without any credentials")
    void apiCallSucceedsWithoutAuth() throws Exception {
        Person p = new Person("Ada", 1L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Swagger endpoints are accessible without credentials")
    void swaggerAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("404 page returns without authentication")
    void notFoundReturnsHtmlWithoutAuth() throws Exception {
        mockMvc.perform(get("/this-does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("No SecurityFilterChain bean is registered when security is off")
    void noSecurityFilterChainBeanExists() {
        assertThatThrownBy(() -> context.getBean(SecurityFilterChain.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    @DisplayName("SecurityOffConfig is loaded; SecurityBasicConfig is not")
    void onlySecurityOffConfigIsLoaded() {
        assertThat(context.containsBean("securityOffConfig")).isTrue();
        assertThat(context.containsBean("securityBasicConfig")).isFalse();
    }
}
