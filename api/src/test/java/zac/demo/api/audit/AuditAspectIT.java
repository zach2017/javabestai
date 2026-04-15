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
import zac.demo.api.audit.sink.LogFileAuditSink;
import zac.demo.api.model.Person;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Integration test for AuditAspect + LogFileAuditSink + AuditProperties.
 *
 * Attaches a Logback ListAppender to the LogFileAuditSink logger so we
 * can directly assert on the rendered audit lines.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditAspectIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditProperties auditProperties;

    private ListAppender<ILoggingEvent> appender;
    private Logger sinkLogger;

    @BeforeEach
    void attachAppender() {
        sinkLogger = (Logger) LoggerFactory.getLogger(LogFileAuditSink.class);
        appender = new ListAppender<>();
        appender.start();
        sinkLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        sinkLogger.detachAppender(appender);
        appender.stop();
        // Reset any property mutations done in individual tests so the
        // next test sees the configured defaults again.
        auditProperties.setEnabled(true);
        auditProperties.setLogArgs(true);
        auditProperties.setLogResult(true);
        auditProperties.setLogTiming(true);
        auditProperties.setIncludeSwagger(true);
    }

    @Test
    @DisplayName("Successful controller call produces entry/exit lines for both controller and service")
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

        assertThat(messages).anyMatch(m -> m.startsWith("→ GlobalControllerAdvice.handleValidation"));
        assertThat(messages).anyMatch(m -> m.startsWith("← GlobalControllerAdvice.handleValidation returned"));
        assertThat(messages).noneMatch(m -> m.startsWith("→ HelloController.hello"));
    }

    @Test
    @DisplayName("Swagger endpoint /v3/api-docs is audited when audit.include-swagger=true")
    void logsSwaggerOpenApiEndpointWhenIncluded() throws Exception {
        mockMvc.perform(get("/v3/api-docs"));

        boolean swaggerCallAudited = capturedMessages().stream()
                .anyMatch(m -> m.startsWith("→") || m.startsWith("←"));

        assertThat(swaggerCallAudited)
                .as("AuditAspect should fire on springdoc controller methods")
                .isTrue();
    }

    @Test
    @DisplayName("Swagger calls are skipped when audit.include-swagger=false")
    void skipsSwaggerWhenExcluded() throws Exception {
        auditProperties.setIncludeSwagger(false);

        mockMvc.perform(get("/v3/api-docs"));

        // We may still see app-component logs from other beans, but no
        // log line should reference a springdoc class.
        assertThat(capturedMessages())
                .noneMatch(m -> m.contains("OpenApi") || m.contains("Springdoc")
                        || m.contains("Swagger"));
    }

    @Test
    @DisplayName("audit.enabled=false silences the aspect entirely")
    void disabledMasterSwitchSilencesEverything() throws Exception {
        auditProperties.setEnabled(false);
        Person p = new Person("Ada", 1L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p)));

        assertThat(capturedMessages()).isEmpty();
    }

    @Test
    @DisplayName("audit.log-args=false hides argument values in entry lines")
    void hidesArgsWhenLogArgsDisabled() throws Exception {
        auditProperties.setLogArgs(false);
        Person p = new Person("SecretName", 1L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p)));

        List<String> entryLines = capturedMessages().stream()
                .filter(m -> m.startsWith("→"))
                .toList();

        assertThat(entryLines).isNotEmpty();
        assertThat(entryLines).allMatch(m -> m.contains("<args hidden>"));
        assertThat(entryLines).noneMatch(m -> m.contains("SecretName"));
    }

    @Test
    @DisplayName("audit.log-timing=false omits the [Xms] suffix from exit lines")
    void omitsTimingWhenLogTimingDisabled() throws Exception {
        auditProperties.setLogTiming(false);
        Person p = new Person("Ada", 1L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p)));

        List<String> exitLines = capturedMessages().stream()
                .filter(m -> m.startsWith("←"))
                .toList();

        assertThat(exitLines).isNotEmpty();
        assertThat(exitLines).noneMatch(m -> m.matches(".*\\[\\d+ms\\].*"));
    }

    private List<String> capturedMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO || e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
