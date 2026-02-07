package com.example.tinyledger.repository

import com.example.tinyledger.Transaction
import java.util.concurrent.atomic.AtomicLong

class OptimisticLockException : RuntimeException("Concurrent modification detected")

class InMemoryTransactionalRepository(val ledgerRepository: LedgerRepository) : TransactionalLedgerRepository {
    private val isTransactional = ThreadLocal.withInitial { false }

    private val pendingTransactions = ThreadLocal.withInitial { mutableListOf<Transaction>() }

    private val version = AtomicLong(0)
    private val readVersion = ThreadLocal.withInitial { 0L }

    override fun begin() {
        isTransactional.set(true)
        readVersion.set(version.get())
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
            ledgerRepository.findAll() + pendingTransactions.get()
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
        pendingTransactions.get().clear()
        isTransactional.set(false)
    }

}