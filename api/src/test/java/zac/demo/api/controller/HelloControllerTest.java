package zac.demo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import zac.demo.api.model.Person;
import zac.demo.api.service.GreetingService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test for HelloController.
 *  - Loads only the MVC infrastructure + this controller.
 *  - GreetingService is replaced with a Mockito mock via @MockitoBean.
 *  - GlobalControllerAdvice is auto-scanned by @WebMvcTest (it picks up
 *    @ControllerAdvice classes by default), so we can assert on its
 *    JSON error responses (validation, malformed body, generic 500).
 */
@WebMvcTest(HelloController.class)
@AutoConfigureMockMvc(addFilters = false)
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GreetingService greetingService;

    // -----------------------------------------------------------------
    // happy path
    // -----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/hello returns 200 and greeting JSON for a valid Person")
    void hello_returnsGreeting_forValidPerson() throws Exception {
        Person person = new Person("Ada Lovelace", 42L, "ADMIN");
        when(greetingService.greet(any(Person.class)))
                .thenReturn("Hello, Ada Lovelace! (id=42, role=ADMIN)");

        mockMvc.perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(person)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Hello, Ada Lovelace! (id=42, role=ADMIN)"))
                .andExpect(jsonPath("$.person.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.person.id").value(42))
                .andExpect(jsonPath("$.person.role").value("ADMIN"));

        verify(greetingService).greet(any(Person.class));
    }

    // -----------------------------------------------------------------
    // validation failures (advice handles MethodArgumentNotValidException)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/hello returns 400 with field error when name is blank")
    void hello_returns400_whenNameIsBlank() throws Exception {
        Person person = new Person("   ", 42L, "ADMIN");

        mockMvc.perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(person)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("name must not be blank"));

        verify(greetingService, never()).greet(any());
    }

    @Test
    @DisplayName("POST /api/hello returns 400 with field error when id is null")
    void hello_returns400_whenIdIsNull() throws Exception {
        Person person = new Person("Ada", null, "ADMIN");

        mockMvc.perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(person)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("id must not be null"));

        verify(greetingService, never()).greet(any());
    }

    @Test
    @DisplayName("POST /api/hello returns 400 with field errors when multiple fields invalid")
    void hello_returns400_whenMultipleFieldsInvalid() throws Exception {
        Person person = new Person("", -1L, "");

        mockMvc.perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(person)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.id").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    // -----------------------------------------------------------------
    // malformed JSON (advice handles HttpMessageNotReadableException)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/hello returns 400 when body is not valid JSON")
    void hello_returns400_whenBodyIsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not even json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }
}
