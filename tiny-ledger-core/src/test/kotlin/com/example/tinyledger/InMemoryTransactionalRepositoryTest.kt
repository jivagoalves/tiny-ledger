package com.example.tinyledger

import com.example.tinyledger.repository.InMemoryTransactionalRepository
import com.example.tinyledger.repository.LedgerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.jvm.javaClass

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
        assertThrows(IllegalStateException::class.java) {
            repository.commit()
        }


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

    @Test
    fun `concurrent transactions should have isolated state`() {
        val threadSafeStore = ConcurrentLinkedQueue<Transaction>()
        val threadSafeRepo = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                threadSafeStore.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = threadSafeStore.toList()
            override fun delete(transaction: Transaction): Boolean = threadSafeStore.remove(transaction)
        }
        val repository = InMemoryTransactionalRepository(threadSafeRepo)

        val transactionA = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        val transactionB = Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT)

        val threadAStarted = CountDownLatch(1)
        val threadBCommitted = CountDownLatch(1)

        val threadA = Thread {
            repository.begin()
            repository.save(transactionA)
            threadAStarted.countDown()
            threadBCommitted.await()
            repository.rollback()
        }

        val threadB = Thread {
            threadAStarted.await()
            repository.withTransaction {
                repository.save(transactionB)
            }
            threadBCommitted.countDown()
        }

        threadA.start()
        threadB.start()
        threadA.join()
        threadB.join()

        assertEquals(listOf(transactionB), threadSafeStore.toList())
    }
}