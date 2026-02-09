package com.example.tinyledger.repository

import com.example.tinyledger.Transaction
import com.example.tinyledger.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

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
    fun `should delete from underlying repository when not within transaction`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.save(transaction)
        repository.delete(transaction)

        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should delete from underlying repository after transaction commit`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(transaction)

        repository.withTransaction {
            repository.delete(transaction)
        }

        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should not delete if transaction is rolled back`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(transaction)

        assertThrows(RuntimeException::class.java) {
            repository.withTransaction {
                repository.delete(transaction)
                throw RuntimeException("Boom!")
            }
        }

        assertTrue(repository.findAll().isNotEmpty())
    }

    @Test
    fun `should delete from pending transactions within transaction`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.withTransaction {
            repository.save(transaction)
            repository.delete(transaction)
        }

        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should not see deleted committed transaction within same transaction`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(transaction)

        repository.withTransaction {
            repository.delete(transaction)
            assertTrue(repository.findAll().isEmpty())
        }
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

    @Test
    fun `should not allow ABA vulnerability allowing commit on stale data`() {
        val store = ConcurrentLinkedQueue<Transaction>()
        val threadARolledBack = CountDownLatch(1)
        val threadBBegan = CountDownLatch(1)
        val threadCCommitted = CountDownLatch(1)

        val poisonAmount = BigDecimal("999")

        val customRepo = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                if (transaction.amount == poisonAmount) {
                    throw RuntimeException("simulated save failure")
                }
                store.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = store.toList()
            override fun delete(transaction: Transaction): Boolean = store.remove(transaction)
        }

        val repository = InMemoryTransactionalRepository(customRepo)

        val seed = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        store.add(seed)

        val phantomDeposit = Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT)
        val poison = Transaction(amount = poisonAmount, type = TransactionType.DEPOSIT)

        var threadBCommitSucceeded = false

        // Thread A: CAS(0,1) succeeds → saves phantomDeposit → poison throws → rollback
        // Version stays bumped at 1, but phantom is cleaned up
        val threadA = Thread {
            repository.begin()
            repository.save(phantomDeposit)
            repository.save(poison)
            try {
                repository.commit()
            } catch (_: RuntimeException) { }
            threadARolledBack.countDown()
        }

        // Thread B: begins after A's failed commit (readVersion=1), sees clean state
        val threadB = Thread {
            threadARolledBack.await()
            repository.begin() // readVersion = 1
            threadBBegan.countDown()

            // Lock prevents seeing phantom - snapshot is clean
            assertFalse(repository.findAll().contains(phantomDeposit), "Thread B should not see phantom data")

            threadCCommitted.await()
            repository.save(Transaction(amount = BigDecimal("50"), type = TransactionType.DEPOSIT))
            try {
                repository.commit() // CAS(1, 2) fails - version is now 2 after Thread C
                threadBCommitSucceeded = true
            } catch (_: OptimisticLockException) {
                threadBCommitSucceeded = false
            }
        }

        // Thread C: commits after A's rollback and B's begin → bumps version to 2
        val threadC = Thread {
            threadBBegan.await()
            repository.withTransaction {
                repository.save(Transaction(amount = BigDecimal("10"), type = TransactionType.DEPOSIT))
            }
            threadCCommitted.countDown()
        }

        threadA.start()
        threadB.start()
        threadC.start()
        threadA.join()
        threadB.join()
        threadC.join()

        assertFalse(threadBCommitSucceeded, "Thread B commit should fail - stale readVersion")
        assertFalse(store.contains(phantomDeposit), "Phantom deposit should not be in the store")
    }

    @Test
    fun `concurrent withdrawals should not overdraft due to snapshot during apply`() {
        val threadSafeStore = ConcurrentLinkedQueue<Transaction>()
        val seed = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        threadSafeStore.add(seed)

        val threadSafeRepo = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                threadSafeStore.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = threadSafeStore.toList()
            override fun delete(transaction: Transaction): Boolean = threadSafeStore.remove(transaction)
        }

        val repository = InMemoryTransactionalRepository(threadSafeRepo)
        val threadCount = 10
        val latch = CountDownLatch(1)

        // All threads try to withdraw $80 from a $100 balance - at most one should succeed
        val results = ConcurrentLinkedQueue<Boolean>()
        val threads = (1..threadCount).map {
            Thread {
                latch.await()
                try {
                    repository.withTransaction {
                        val balance = repository.findAll()
                            .fold(BigDecimal.ZERO) { acc, t ->
                                if (t.type == TransactionType.DEPOSIT) acc + t.amount else acc - t.amount
                            }
                        if (balance < BigDecimal("80")) throw IllegalStateException("Insufficient balance")
                        repository.save(Transaction(amount = BigDecimal("80"), type = TransactionType.WITHDRAWAL))
                    }
                    results.add(true)
                } catch (_: IllegalStateException) {
                    results.add(false)
                }
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join() }

        val successes = results.count { it }
        assertEquals(1, successes, "Exactly one withdrawal should succeed")

        val finalBalance = threadSafeStore.toList().fold(BigDecimal.ZERO) { acc, t ->
            if (t.type == TransactionType.DEPOSIT) acc + t.amount else acc - t.amount
        }
        assertEquals(BigDecimal("20"), finalBalance, "Final balance should be $20")
    }

    @Test
    fun `should rollback all applied saves even when a compensating delete throws`() {
        val persistedStore = mutableListOf<Transaction>()
        val trx1 = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        val trx2 = Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT)
        val trx3 = Transaction(amount = BigDecimal("300"), type = TransactionType.DEPOSIT)

        val flakyRepo = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                if (transaction == trx3) throw RuntimeException("save failed")
                persistedStore.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = persistedStore.toList()
            override fun delete(transaction: Transaction): Boolean {
                if (transaction == trx1) throw RuntimeException("delete failed")
                return persistedStore.remove(transaction)
            }
        }

        val repository = InMemoryTransactionalRepository(flakyRepo)

        repository.begin()
        repository.save(trx1)
        repository.save(trx2)
        repository.save(trx3)

        assertThrows(RuntimeException::class.java) {
            repository.commit()
        }

        // trx1 delete threw, but trx2 should still have been cleaned up
        assertFalse(persistedStore.contains(trx2), "trx2 should be rolled back")
        // trx3 was never saved, so it should not be in the store
        assertFalse(persistedStore.contains(trx3), "trx3 was never saved")
    }

    @Test
    fun `should ensure snapshot isolation or repeatable reads`() {
        val threadABegan = CountDownLatch(1)
        val threadBSaved = CountDownLatch(1)
        var firstRead: List<Transaction> = emptyList()
        var secondRead: List<Transaction> = emptyList()

        val threadA = Thread {
            repository.withTransaction {
                firstRead = repository.findAll()
                threadABegan.countDown()
                threadBSaved.await()
                secondRead = repository.findAll()
            }
        }
        val threadB = Thread {
            threadABegan.await()
            repository.withTransaction {
                repository.save(Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT))
            }
            threadBSaved.countDown()
        }

        threadA.start()
        threadB.start()
        threadA.join()
        threadB.join()

        assertEquals(emptyList<Transaction>(), firstRead)
        assertEquals(emptyList<Transaction>(), secondRead)
    }
}