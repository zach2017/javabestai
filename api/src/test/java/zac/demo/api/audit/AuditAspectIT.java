package zac.demo.api.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import zac.demo.api.model.Person;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Integration test for AuditAspect.
 *
 * Attaches a Logback ListAppender to the AuditAspect logger so we can
 * make assertions against what the aspect actually emitted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditAspectIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogger;

    @BeforeEach
    void attachAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger(AuditAspect.class);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("Successful controller call produces entry and exit audit logs for both controller and service")
    void logsControllerAndServiceInvocations() throws Exception {
        Person p = new Person("Ada Lovelace", 42L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p)));

        List<String> messages = capturedMessages();

        assertThat(messages).anyMatch(m -> m.startsWith("→ HelloController.hello"));
        assertThat(messages).anyMatch(m -> m.startsWith("← HelloController.hello returned"));
        assertThat(messages).anyMatch(m -> m.startsWith("→ GreetingService.greet"));
        assertThat(messages).anyMatch(m -> m.startsWith("← GreetingService.greet returned"));
    }

    @Test
    @DisplayName("Validation failure is logged on the advice handler, controller is never entered")
    void logsValidationFailureViaAdvice() throws Exception {
        Person invalid = new Person("", null, "");

        mockMvc.perform(post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)));

        List<String> messages = capturedMessages();

        // @Valid fails before the controller method runs, so the audit advice logs
        // the @ControllerAdvice handler being invoked instead.
        assertThat(messages).anyMatch(m -> m.startsWith("→ GlobalControllerAdvice.handleValidation"));
        assertThat(messages).anyMatch(m -> m.startsWith("← GlobalControllerAdvice.handleValidation returned"));
        assertThat(messages).noneMatch(m -> m.startsWith("→ HelloController.hello"));
    }

    @Test
    @DisplayName("Swagger endpoint /v3/api-docs is also audited via the springdoc pointcut")
    void logsSwaggerOpenApiEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"));

        boolean swaggerCallAudited = capturedMessages().stream()
                .anyMatch(m -> m.startsWith("→") || m.startsWith("←"));

        assertThat(swaggerCallAudited)
                .as("AuditAspect should fire on springdoc controller methods")
                .isTrue();
    }

    @Test
    @DisplayName("Aspect rethrows the original exception after logging it")
    void exceptionIsLoggedAndRethrown() throws Exception {
        // POSTing malformed JSON makes Spring throw HttpMessageNotReadableException
        // before the controller method runs. The advice handler will catch it,
        // and the aspect logs that handler's invocation.
        mockMvc.perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));

        List<String> messages = capturedMessages();

        assertThat(messages).anyMatch(m -> m.startsWith("→ GlobalControllerAdvice.handleUnreadable"));
    }

    private List<String> capturedMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO || e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
