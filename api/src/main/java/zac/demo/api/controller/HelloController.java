package zac.demo.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zac.demo.api.model.Person;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Hello", description = "Simple hello-world endpoint")
public class HelloController {

    @Operation(
            summary = "Greet a person",
            description = "Accepts a Person (name, id, role) and returns a hello-world greeting."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Greeting generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PostMapping(value = "/hello", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> hello(@RequestBody Person person) {
        log.info("Received /hello request: {}", person);

        if (person == null || person.getName() == null || person.getName().isBlank()) {
            log.debug("Rejecting request: missing or blank name");
            throw new IllegalArgumentException("Person 'name' must not be blank");
        }
        if (person.getId() == null) {
            log.debug("Rejecting request: missing id");
            throw new IllegalArgumentException("Person 'id' must not be null");
        }

        String message = "Hello, %s! (id=%d, role=%s)".formatted(
                person.getName(), person.getId(), person.getRole());

        log.debug("Returning greeting: {}", message);

        return ResponseEntity.ok(Map.of(
                "message", message,
                "person", person
        ));
    }
}