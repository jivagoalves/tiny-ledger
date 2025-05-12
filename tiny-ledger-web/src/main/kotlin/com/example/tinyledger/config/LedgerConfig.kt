package com.example.tinyledger.config

import com.example.tinyledger.InMemoryLedgerRepository
import com.example.tinyledger.LedgerRepository
import com.example.tinyledger.LedgerService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class LedgerConfig {

    @Bean
    @Primary
    fun ledgerRepository(): LedgerRepository =
        InMemoryLedgerRepository()

    @Bean
    fun ledgerService(repository: LedgerRepository): LedgerService =
        LedgerService(repository)
}