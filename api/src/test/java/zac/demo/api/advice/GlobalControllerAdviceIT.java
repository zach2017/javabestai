package zac.demo.api.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context integration test for the HTML 404 page.
 *
 * Uses @SpringBootTest so the static-resource handler and
 * NoResourceFoundException auto-configuration behave exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalControllerAdviceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET on an unknown path returns 404 HTML page styled with Tailwind")
    void unknownPath_returnsHtml404Page() throws Exception {
        mockMvc.perform(get("/this-path-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("404")))
                .andExpect(content().string(containsString("Page not found")))
                .andExpect(content().string(containsString("/tailwinds.css")));
    }

    @Test
    @DisplayName("nested unknown path also returns the HTML 404 page")
    void nestedUnknownPath_returnsHtml404Page() throws Exception {
        mockMvc.perform(get("/api/does/not/exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Page not found")));
    }
}
