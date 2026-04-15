package zac.demo.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes the OpenAPI document used by Swagger UI.
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Slf4j
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        log.info("Initializing OpenAPI / Swagger configuration");
        return new OpenAPI().info(new Info()
                .title("Demo API")
                .description("Simple hello-world API with a single Person endpoint.")
                .version("v1"));
    }
}
