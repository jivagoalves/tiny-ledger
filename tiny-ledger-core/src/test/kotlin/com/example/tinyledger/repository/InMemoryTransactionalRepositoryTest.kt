package com.example.tinyledger.repository

import com.example.tinyledger.Transaction
import com.example.tinyledger.TransactionType
import org.junit.jupiter.api.Assertions
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

        Assertions.assertEquals(listOf(transaction), repository.findAll())
    }

    @Test
    fun `run a series of transactions with commit`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.begin()
        repository.save(transaction)
        repository.commit()

        Assertions.assertEquals(listOf(transaction), repository.findAll())
    }

    @Test
    fun `commit is idempotent`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.begin()
        repository.save(transaction)
        repository.commit()
        repository.commit()

        Assertions.assertEquals(listOf(transaction), repository.findAll())
    }

    @Test
    fun `run a series of transactions with rollback`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.begin()
        repository.save(transaction)
        repository.rollback()

        Assertions.assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should delete from underlying repository when not within transaction`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.save(transaction)
        repository.delete(transaction)

        Assertions.assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should delete from underlying repository after transaction commit`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(transaction)

        repository.withTransaction {
            repository.delete(transaction)
        }

        Assertions.assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should not delete if transaction is rolled back`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(transaction)

        Assertions.assertThrows(RuntimeException::class.java) {
            repository.withTransaction {
                repository.delete(transaction)
                throw RuntimeException("Boom!")
            }
        }

        Assertions.assertTrue(repository.findAll().isNotEmpty())
    }

    @Test
    fun `should delete from pending transactions within transaction`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.withTransaction {
            repository.save(transaction)
            repository.delete(transaction)
        }

        Assertions.assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should not see deleted committed transaction within same transaction`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(transaction)

        repository.withTransaction {
            repository.delete(transaction)
            Assertions.assertTrue(repository.findAll().isEmpty())
        }
    }

    @Test
    fun `should read previously committed transactions within a session`() {
        val transaction = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(transaction)

        repository.begin()
        Assertions.assertEquals(listOf(transaction), repository.findAll())
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
        Assertions.assertThrows(IllegalStateException::class.java) {
            repository.commit()
        }


        Assertions.assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `should allow to group transactional operations within a block`() {
        val transaction1 = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        val transaction2 = Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT)

        val result = repository.withTransaction {
            repository.save(transaction1)
            repository.save(transaction2)
        }

        Assertions.assertEquals(transaction2, result)
        Assertions.assertEquals(listOf(transaction1, transaction2), repository.findAll())
    }

    @Test
    fun `should commit transactional operations in a block atomically`() {
        val transaction1 = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        Assertions.assertThrows(RuntimeException::class.java) {
            repository.withTransaction {
                repository.save(transaction1)
                throw RuntimeException()
            }
        }

        Assertions.assertTrue(repository.findAll().isEmpty())
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

        Assertions.assertEquals(listOf(transactionB), threadSafeStore.toList())
    }

    @Test
    fun `should not allow ABA vulnerability allowing commit on stale data`() {
        val store = ConcurrentLinkedQueue<Transaction>()
        val partialCommitVisible = CountDownLatch(1)
        val threadBReadDone = CountDownLatch(1)
        val threadARolledBack = CountDownLatch(1)
        val threadCCommitted = CountDownLatch(1)

        val poisonAmount = BigDecimal("999")

        val customRepo = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                if (transaction.amount == poisonAmount) {
                    partialCommitVisible.countDown()
                    threadBReadDone.await()
                    throw RuntimeException("simulated save failure")
                }
                store.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = store.toList()
            override fun delete(transaction: Transaction): Boolean = store.remove(transaction)
        }

        val repository = InMemoryTransactionalRepository(customRepo)

        // Seed: one deposit already committed in the underlying store
        val seed = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        store.add(seed)

        val phantomDeposit = Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT)
        val poison = Transaction(amount = poisonAmount, type = TransactionType.DEPOSIT)

        var threadBSawPhantom = false
        var threadBCommitSucceeded = false

        // Thread A: CAS(0,1) succeeds → saves phantomDeposit → poison throws
        //   catch: deletes phantomDeposit, decrementAndGet → version back to 0
        val threadA = Thread {
            repository.begin()
            repository.save(phantomDeposit)
            repository.save(poison)
            try {
                repository.commit()
            } catch (_: RuntimeException) { }
            threadARolledBack.countDown()
        }

        // Thread B: begins after A's CAS (readVersion=1), reads phantom data
        val threadB = Thread {
            partialCommitVisible.await()
            repository.begin()  // readVersion = 1 (A already bumped it)
            threadBSawPhantom = repository.findAll().contains(phantomDeposit)
            threadBReadDone.countDown()

            threadCCommitted.await()
            val item = Transaction(amount = BigDecimal("50"), type = TransactionType.DEPOSIT)
            repository.save(item)
            try {
                repository.commit()  // CAS(1, 2) — version is 1 again (ABA: 1→0→1)
                threadBCommitSucceeded = true
            } catch (_: OptimisticLockException) {
                threadBCommitSucceeded = false
            }
        }

        // Thread C: commits after A's rollback → CAS(0,1) bumps version back to 1
        val threadC = Thread {
            threadARolledBack.await()
            val item = Transaction(amount = BigDecimal("10"), type = TransactionType.DEPOSIT)
            repository.begin()
            repository.save(item)
            repository.commit()
            threadCCommitted.countDown()
        }

        threadA.start()
        threadB.start()
        threadC.start()
        threadA.join()
        threadB.join()
        threadC.join()

        // Thread B read phantomDeposit during A's partial commit
        Assertions.assertTrue(threadBSawPhantom, "Thread B has seen the phantom deposit")

        // Thread B's commit succeeded despite reading rolled-back data (ABA vulnerability)
        Assertions.assertFalse(threadBCommitSucceeded, "Thread B commit should not succeed")

        // The data Thread B based its read on is gone — phantomDeposit was rolled back
        Assertions.assertFalse(store.contains(phantomDeposit), "Phantom deposit should not be in the store")
    }

    @Test
    fun `should ensure snapshot isolation or repeatable reads`() {
        val threadBSaved = CountDownLatch(1)
        var firstRead: List<Transaction> = emptyList()
        var secondRead: List<Transaction> = emptyList()

        val threadA = Thread {
            repository.withTransaction {
                firstRead = repository.findAll()
                threadBSaved.await()
                secondRead = repository.findAll()
            }
        }
        val threadB = Thread {
            repository.withTransaction {
                repository.save(Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT))
            }
            threadBSaved.countDown()
        }

        threadA.start()
        threadB.start()
        threadA.join()
        threadB.join()

        Assertions.assertEquals(emptyList<Transaction>(), firstRead)
        Assertions.assertEquals(emptyList<Transaction>(), secondRead)
    }
}