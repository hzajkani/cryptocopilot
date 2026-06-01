package com.cryptocopilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the springdoc-generated docs (Swagger UI at {@code /swagger-ui.html}). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cryptoCopilotOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CryptoCopilot API")
                .description("Read-only market data, fused ML + TA signals, and the deterministic "
                        + "ta4j TA verdict. Decision-support, not financial advice.")
                .version("v1"));
    }
}
