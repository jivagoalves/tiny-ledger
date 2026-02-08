package com.example.tinyledger.config

import com.example.tinyledger.InMemoryLedgerRepository
import com.example.tinyledger.repository.InMemoryTransactionalRepository
import com.example.tinyledger.repository.LedgerRepository
import com.example.tinyledger.LedgerService
import com.example.tinyledger.repository.TransactionalLedgerRepository
import com.example.tinyledger.repository.WalLedgerRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.nio.file.Path

@Configuration
class LedgerConfig(
    @param:Value("\${ledger.wal.path}") private val walPath: String
) {

    @Bean
    @Primary
    fun ledgerRepository(): LedgerRepository =
        InMemoryLedgerRepository()

    @Bean
    fun walLedgerRepository(ledgerRepository: LedgerRepository): WalLedgerRepository =
        WalLedgerRepository(ledgerRepository, Path.of(walPath)).also { it.recover() }

    @Bean
    fun transactionalLedgerRepository(walLedgerRepository: WalLedgerRepository): TransactionalLedgerRepository =
        InMemoryTransactionalRepository(walLedgerRepository)

    @Bean
    fun ledgerService(repository: TransactionalLedgerRepository): LedgerService =
        LedgerService(repository)
}