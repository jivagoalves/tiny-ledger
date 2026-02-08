package com.example.tinyledger.repository

import com.example.tinyledger.Transaction
import java.util.concurrent.atomic.AtomicLong

class OptimisticLockException : RuntimeException("Concurrent modification detected")

class InMemoryTransactionalRepository(val ledgerRepository: LedgerRepository) : TransactionalLedgerRepository {
    private val isTransactional = ThreadLocal.withInitial { false }

    private val pendingTransactions = ThreadLocal.withInitial { mutableListOf<Transaction>() }

    private val version = AtomicLong(0)
    private val readVersion = ThreadLocal.withInitial { 0L }
    private val snapshot = ThreadLocal.withInitial<List<Transaction>> { emptyList() }

    override fun begin() {
        isTransactional.set(true)
        pendingTransactions.set(mutableListOf())
        readVersion.set(version.get())
        snapshot.set(ledgerRepository.findAll().toList())
    }

    override fun commit() {
        val pending = pendingTransactions.get()
        try {
            if (pending.isNotEmpty()) {
                if (!version.compareAndSet(readVersion.get(), readVersion.get() + 1)) {
                    throw OptimisticLockException()
                }
                try {
                    pending.forEach { ledgerRepository.save(it) }
                } catch (e: RuntimeException) {
                    pending.forEach { ledgerRepository.delete(it) }
                    throw e
                }
            }
        } finally {
            reset()
        }
    }

    override fun rollback() {
        reset()
    }

    override fun save(transaction: Transaction): Transaction =
        if (isTransactional.get()) {
            pendingTransactions.get().add(transaction)
            transaction
        } else ledgerRepository.save(transaction)

    override fun findAll(): List<Transaction> =
        if (isTransactional.get()) {
            snapshot.get() + pendingTransactions.get()
        } else {
            ledgerRepository.findAll()
        }

    override fun delete(transaction: Transaction): Boolean =
        pendingTransactions.get().remove(transaction)

    override fun <T> withTransaction(transactionFn: () -> T): T {
        if (isTransactional.get()) {
            return transactionFn()
        }
        while (true) {
            begin()
            try {
                val result = transactionFn()
                commit()
                return result
            } catch (_: OptimisticLockException) {
                reset()
            } catch (e: Exception) {
                reset()
                throw e
            }
        }
    }

    private fun reset() {
        isTransactional.set(false)
        pendingTransactions.apply {
            get().clear()
            remove()
        }
        readVersion.remove()
        snapshot.remove()
    }

}