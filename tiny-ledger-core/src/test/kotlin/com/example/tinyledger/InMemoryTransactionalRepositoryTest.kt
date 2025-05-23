package com.example.tinyledger

import com.example.tinyledger.repository.InMemoryTransactionalRepository
import com.example.tinyledger.repository.LedgerRepository
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

        override fun delete(transaction: Transaction): Boolean =
            store.remove(transaction)
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
    fun `commit is idempotent`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.begin()
        repository.save(transaction)
        repository.commit()
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

    @Test
    fun `transactions should be committed atomically`() {
        val repository = InMemoryTransactionalRepository(object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction =
                if (transaction.amount == BigDecimal("200"))
                    throw IllegalStateException()
                else
                    transaction

            override fun findAll(): List<Transaction> = emptyList()

            override fun delete(transaction: Transaction): Boolean =
                store.remove(transaction)

        })

        repository.begin()
        repository.save(Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT))
        repository.save(Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT))
        repository.commit()

        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should allow to group transactional operations within a block`() {
        val transaction1 = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        val transaction2 = Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT)

        val result = repository.withTransaction {
            repository.save(transaction1)
            repository.save(transaction2)
        }

        assertEquals(transaction2, result)
        assertEquals(listOf(transaction1, transaction2), repository.findAll())
    }

    @Test
    fun `should commit transactional operations in a block atomically`() {
        val transaction1 = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        assertThrows(RuntimeException::class.java) {
            repository.withTransaction {
                repository.save(transaction1)
                throw RuntimeException()
            }
        }

        assertTrue(repository.findAll().isEmpty())
    }
}