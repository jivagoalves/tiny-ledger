package com.example.tinyledger.repository

import com.example.tinyledger.Transaction
import com.example.tinyledger.TransactionType
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.readLines
import kotlin.io.path.appendText

class WalLedgerRepositoryTest {
    private lateinit var store: MutableList<Transaction>
    private lateinit var fakeLedgerRepository: LedgerRepository
    private val json = Json { encodeDefaults = true }

    @BeforeEach
    fun setUp() {
        store = mutableListOf()
        fakeLedgerRepository = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                store.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = store
            override fun delete(transaction: Transaction): Boolean = store.remove(transaction)
        }
    }

    @Test
    fun `should save transaction to WAL file`() {
        val tmpPath = createTempFile("wal", ".json")
        val repository = WalLedgerRepository(fakeLedgerRepository, tmpPath)
        val trx = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.save(trx)

        val lines = tmpPath.readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
        val entry = json.decodeFromString<WalLedgerRepository.Entry>(lines[0])
        assertEquals(WalLedgerRepository.Status.COMMITTED, entry.status)
        assertEquals(1, entry.transactions.size)
        assertEquals(trx, entry.transactions[0])
    }

    @Test
    fun `should save transaction to underlying delegate repo`() {
        val tmpPath = createTempFile("wal", ".json")
        val repository = WalLedgerRepository(fakeLedgerRepository, tmpPath)
        val trx = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.save(trx)

        assertEquals(listOf(trx), fakeLedgerRepository.findAll())
    }

    @Test
    fun `should append multiple entries to WAL file`() {
        val tmpPath = createTempFile("wal", ".json")
        val repository = WalLedgerRepository(fakeLedgerRepository, tmpPath)
        val trxOne = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        val trxTwo = Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT)

        repository.save(trxOne)
        repository.save(trxTwo)

        val lines = tmpPath.readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertDoesNotThrow { json.decodeFromString<WalLedgerRepository.Entry>(lines[0]) }
        assertDoesNotThrow { json.decodeFromString<WalLedgerRepository.Entry>(lines[1]) }
    }

    @Test
    fun `should recover transactions from WAL after restart`() {
        val tmpPath = createTempFile("wal", ".json")
        val repository = WalLedgerRepository(fakeLedgerRepository, tmpPath)
        val trx = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(trx)

        val freshStore = mutableListOf<Transaction>()
        val freshDelegate = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                freshStore.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = freshStore
            override fun delete(transaction: Transaction): Boolean = freshStore.remove(transaction)
        }
        val newRepository = WalLedgerRepository(freshDelegate, tmpPath)
        val recovered = newRepository.recover()

        assertEquals(listOf(trx), recovered)
        assertEquals(listOf(trx), freshDelegate.findAll())
    }

    @Test
    fun `should skip ABORTED entries during recovery`() {
        val tmpPath = createTempFile("wal", ".json")
        val committedTrx = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        val abortedTrx = Transaction(amount = BigDecimal("200"), type = TransactionType.WITHDRAWAL)

        val committedEntry = WalLedgerRepository.Entry(lsn = 1, status = WalLedgerRepository.Status.COMMITTED, transactions = listOf(committedTrx))
        val abortedEntry = WalLedgerRepository.Entry(lsn = 2, status = WalLedgerRepository.Status.ABORTED, transactions = listOf(abortedTrx))
        tmpPath.appendText(json.encodeToString(committedEntry) + "\n")
        tmpPath.appendText(json.encodeToString(abortedEntry) + "\n")

        val repository = WalLedgerRepository(fakeLedgerRepository, tmpPath)
        val recovered = repository.recover()

        assertEquals(listOf(committedTrx), recovered)
        assertEquals(listOf(committedTrx), fakeLedgerRepository.findAll())
    }

    @Test
    fun `should skip corrupt WAL lines during recovery`() {
        val tmpPath = createTempFile("wal", ".json")
        val trx = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        val validEntry = WalLedgerRepository.Entry(lsn = 1, status = WalLedgerRepository.Status.COMMITTED, transactions = listOf(trx))
        tmpPath.appendText(json.encodeToString(validEntry) + "\n")
        tmpPath.appendText("this is garbage data\n")

        val repository = WalLedgerRepository(fakeLedgerRepository, tmpPath)
        val recovered = repository.recover()

        assertEquals(listOf(trx), recovered)
        assertEquals(listOf(trx), fakeLedgerRepository.findAll())
    }

    @Test
    fun `should initialize LSN from WAL on recovery`() {
        val tmpPath = createTempFile("wal", ".json")
        val repository = WalLedgerRepository(fakeLedgerRepository, tmpPath)
        val trx = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)
        repository.save(trx)

        val firstLine = tmpPath.readLines().first { it.isNotBlank() }
        val firstLsn = json.decodeFromString<WalLedgerRepository.Entry>(firstLine).lsn

        val freshStore = mutableListOf<Transaction>()
        val freshDelegate = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                freshStore.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = freshStore
            override fun delete(transaction: Transaction): Boolean = freshStore.remove(transaction)
        }
        val newRepository = WalLedgerRepository(freshDelegate, tmpPath)
        newRepository.recover()

        val trx2 = Transaction(amount = BigDecimal("200"), type = TransactionType.DEPOSIT)
        newRepository.save(trx2)

        val lines = tmpPath.readLines().filter { it.isNotBlank() }
        val lastEntry = json.decodeFromString<WalLedgerRepository.Entry>(lines.last())
        assertTrue(lastEntry.lsn > firstLsn, "New LSN ${lastEntry.lsn} should be greater than recovered LSN $firstLsn")
    }

    @Test
    fun `concurrent saves should produce intact WAL entries`() {
        val tmpPath = createTempFile("wal", ".json")
        val threadSafeStore = CopyOnWriteArrayList<Transaction>()
        val threadSafeDelegate = object : LedgerRepository {
            override fun save(transaction: Transaction): Transaction {
                threadSafeStore.add(transaction)
                return transaction
            }
            override fun findAll(): List<Transaction> = threadSafeStore.toList()
            override fun delete(transaction: Transaction): Boolean = threadSafeStore.remove(transaction)
        }
        val repository = WalLedgerRepository(threadSafeDelegate, tmpPath)
        val threadCount = 10
        val savesPerThread = 20
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)

        val futures = (1..threadCount).map {
            executor.submit {
                latch.await()
                repeat(savesPerThread) {
                    repository.save(Transaction(amount = BigDecimal("1"), type = TransactionType.DEPOSIT))
                }
            }
        }
        latch.countDown()
        futures.forEach { it.get() }
        executor.shutdown()

        val expectedCount = threadCount * savesPerThread
        val lines = tmpPath.readLines().filter { it.isNotBlank() }
        assertEquals(expectedCount, lines.size, "Expected $expectedCount WAL lines")
        val entries = lines.map { json.decodeFromString<WalLedgerRepository.Entry>(it) }
        assertEquals(expectedCount, entries.size, "All lines should be parseable w/o corrupt entries")
        assertEquals(expectedCount, entries.map { it.lsn }.toSet().size, "All LSNs should be unique")
    }

    @Test
    fun `should create WAL file and parent directories on first save`() {
        val tmpDir = createTempDirectory("wal-test")
        val walPath = tmpDir.resolve("nested/dir/wal.json")
        val repository = WalLedgerRepository(fakeLedgerRepository, walPath)
        val trx = Transaction(amount = BigDecimal("100"), type = TransactionType.DEPOSIT)

        repository.save(trx)

        val lines = walPath.readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
    }
}
