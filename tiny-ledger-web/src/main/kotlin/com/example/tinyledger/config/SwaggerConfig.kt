package com.example.tinyledger.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    @Bean
    fun customOpenAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Tiny Ledger API")
                .version("1.0.0")
                .description("Simple Ledger API for deposits, withdrawals, balance and history.")
        )
}