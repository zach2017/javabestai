package zac.demo.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple person payload.
 * Lombok generates getters, setters, equals/hashCode, toString,
 * plus all-args and no-args constructors. The no-args constructor
 * is what Jackson uses to deserialize incoming JSON by default.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "A person with identifying information")
public class Person {

    @Schema(description = "Full name of the person", example = "Ada Lovelace")
    private String name;

    @Schema(description = "Unique numeric identifier", example = "42")
    private Long id;

    @Schema(description = "Role assigned to the person", example = "ADMIN")
    private String role;
}