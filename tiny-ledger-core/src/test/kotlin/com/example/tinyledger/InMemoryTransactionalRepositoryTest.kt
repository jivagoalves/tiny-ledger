package com.example.tinyledger

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InMemoryTransactionalRepositoryTest {
    private val store = mutableListOf<Transaction>()

    val fakeLedgerRepository = object : LedgerRepository {
        override fun save(transaction: Transaction): Transaction {
            store.add(transaction)
            return transaction
        }

        override fun findAll(): List<Transaction> = store
    }

    private val repository = InMemoryTransactionalRepository(fakeLedgerRepository)

    @BeforeEach
    fun setUp() {
        store.clear()
    }

    @Test
    fun `run a series of transactions with autocommit`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.save(transaction)

        assertEquals(listOf(transaction), repository.findAll())
    }

    @Test
    fun `run a series of transactions with commit`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.begin()
        repository.save(transaction)
        repository.commit()

        assertEquals(listOf(transaction), repository.findAll())
    }

    @Test
    fun `run a series of transactions with rollback`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.begin()
        repository.save(transaction)
        repository.rollback()

        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should read previously committed transactions within a session`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(transaction)

        repository.begin()
        assertEquals(listOf(transaction), repository.findAll())
    }
}