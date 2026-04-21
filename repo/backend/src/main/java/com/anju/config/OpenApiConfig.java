package com.anju.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI anjuOpenApi() {
        String basicAuthScheme = "basicAuth";
        String bearerAuthScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Anju Medical Appointment System API")
                        .description("API documentation for appointment, property, file, finance, and auth modules.")
                        .version("v1")
                        .contact(new Contact().name("Anju API Team"))
                        .license(new License().name("Internal Use")))
                .addSecurityItem(new SecurityRequirement().addList(basicAuthScheme))
                .addSecurityItem(new SecurityRequirement().addList(bearerAuthScheme))
                .schemaRequirement(basicAuthScheme,
                        new SecurityScheme()
                                .name(basicAuthScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic"))
                .schemaRequirement(bearerAuthScheme,
                        new SecurityScheme()
                                .name(bearerAuthScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));
    }
}
