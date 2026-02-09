package com.example.tinyledger.repository

import com.example.tinyledger.Transaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines

class WalLedgerRepository(
    private val delegate: LedgerRepository,
    private val walPath: Path
) : LedgerRepository by delegate {
    init {
        walPath.createParentDirectories()
    }

    private val json = Json { encodeDefaults = true }
    private val lsn = AtomicLong(0)
    private val writeLock = ReentrantLock()

    override fun save(transaction: Transaction): Transaction {
        appendEntry(Entry(lsn = lsn.incrementAndGet(), status = Status.COMMITTED, transactions = listOf(transaction)))
        return delegate.save(transaction)
    }

    fun abort(transactions: List<Transaction>) {
        appendEntry(Entry(lsn = lsn.incrementAndGet(), status = Status.ABORTED, transactions = transactions))
    }

    fun recover(): List<Transaction> {
        val entries = readEntries()
        if (entries.isNotEmpty()) {
            lsn.set(entries.maxOf { it.lsn })
        }
        val transactions = entries
            .filter { it.status == Status.COMMITTED }
            .flatMap { it.transactions }
        delegate.findAll().forEach { delegate.delete(it) }
        transactions.forEach { delegate.save(it) }
        return transactions
    }

    private fun appendEntry(entry: Entry) = writeLock.withLock {
        val bytes = (json.encodeToString(entry) + "\n").toByteArray()
        val options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        Files.write(walPath, bytes, *options)
        FileOutputStream(walPath.toFile(), true).use { fos ->
            fos.fd.sync()
        }
    }

    private fun readEntries(): List<Entry> {
        if (!walPath.exists()) return emptyList()
        return walPath.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    json.decodeFromString<Entry>(line)
                } catch (_: Exception) {
                    null
                }
            }
    }

    enum class Status { COMMITTED, ABORTED }

    @Serializable
    data class Entry(
        val lsn: Long,
        val status: Status,
        val transactions: List<Transaction>
    )
}
