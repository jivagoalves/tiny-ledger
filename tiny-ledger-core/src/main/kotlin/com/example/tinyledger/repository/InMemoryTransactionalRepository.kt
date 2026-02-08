package com.example.tinyledger.repository

import com.example.tinyledger.Transaction
import java.util.concurrent.atomic.AtomicLong

class OptimisticLockException : RuntimeException("Concurrent modification detected")

class InMemoryTransactionalRepository(private val ledgerRepository: LedgerRepository) : TransactionalLedgerRepository {

    private class TransactionContext(
        val snapshot: List<Transaction>,
        val readVersion: Long,
    ) {
        val saves = mutableListOf<Transaction>()
        val deletes = mutableListOf<Transaction>()

        fun hasPendingChanges() = saves.isNotEmpty() || deletes.isNotEmpty()
    }

    private val version = AtomicLong(0)
    private val activeTransactionContext = ThreadLocal<TransactionContext>()

    override fun begin() {
        activeTransactionContext.set(
            TransactionContext(
                snapshot = ledgerRepository.findAll().toList(),
                readVersion = version.get()
            )
        )
    }

    override fun commit() {
        val txCtx = activeTransactionContext.get() ?: return
        try {
            if (txCtx.hasPendingChanges()) {
                if (!compareAndSetVersion(txCtx)) {
                    throw OptimisticLockException()
                }
                applyChanges(txCtx)
            }
        } finally {
            reset()
        }
    }

    private fun compareAndSetVersion(txCtx: TransactionContext): Boolean =
        version.compareAndSet(txCtx.readVersion, txCtx.readVersion + 1)

    private fun applyChanges(txCtx: TransactionContext) {
        try {
            txCtx.saves.forEach { ledgerRepository.save(it) }
            txCtx.deletes.forEach { ledgerRepository.delete(it) }
        } catch (e: RuntimeException) {
            txCtx.saves.forEach { ledgerRepository.delete(it) }
            txCtx.deletes.forEach { ledgerRepository.save(it) }
            throw e
        }
    }

    override fun rollback() {
        reset()
    }

    override fun save(transaction: Transaction): Transaction {
        val txCtx = activeTransactionContext.get()
            ?: return ledgerRepository.save(transaction)
        txCtx.saves.add(transaction)
        return transaction
    }

    override fun findAll(): List<Transaction> {
        val txCtx = activeTransactionContext.get()
            ?: return ledgerRepository.findAll()
        return txCtx.snapshot.filter { it !in txCtx.deletes } + txCtx.saves
    }

    override fun delete(transaction: Transaction): Boolean {
        val txCtx = activeTransactionContext.get()
            ?: return ledgerRepository.delete(transaction)
        if (txCtx.saves.remove(transaction)) return true
        if (transaction in txCtx.snapshot) return txCtx.deletes.add(transaction)
        return false
    }

    override fun <T> withTransaction(transactionFn: () -> T): T {
        if (activeTransactionContext.get() != null) {
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
        activeTransactionContext.remove()
    }
}