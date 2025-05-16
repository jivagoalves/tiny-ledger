package com.example.tinyledger.config

import com.example.tinyledger.InMemoryLedgerRepository
import com.example.tinyledger.repository.InMemoryTransactionalRepository
import com.example.tinyledger.repository.LedgerRepository
import com.example.tinyledger.LedgerService
import com.example.tinyledger.repository.TransactionalLedgerRepository
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
    fun transactionalLedgerRepository(ledgerRepository: LedgerRepository): TransactionalLedgerRepository =
        InMemoryTransactionalRepository(ledgerRepository)

    @Bean
    fun ledgerService(repository: TransactionalLedgerRepository): LedgerService =
        LedgerService(repository)
}