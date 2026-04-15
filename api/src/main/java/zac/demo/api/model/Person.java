package zac.demo.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "A person with identifying information")
public class Person {

    @NotBlank(message = "name must not be blank")
    @Schema(description = "Full name of the person", example = "Ada Lovelace")
    private String name;

    @NotNull(message = "id must not be null")
    @Positive(message = "id must be a positive number")
    @Schema(description = "Unique numeric identifier", example = "42")
    private Long id;

    @NotBlank(message = "role must not be blank")
    @Schema(description = "Role assigned to the person", example = "ADMIN")
    private String role;
}
