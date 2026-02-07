package com.example.tinyledger.repository

import com.example.tinyledger.Transaction

class InMemoryTransactionalRepository(val ledgerRepository: LedgerRepository) : TransactionalLedgerRepository {
    private val isTransactional = ThreadLocal.withInitial { false }

    private val pendingTransactions = ThreadLocal.withInitial { mutableListOf<Transaction>() }

    override fun begin() {
        isTransactional.set(true)
    }

    override fun commit() =
        try {
            pendingTransactions.get().forEach { ledgerRepository.save(it) }
        } catch (e: RuntimeException) {
            pendingTransactions.get().forEach { ledgerRepository.delete(it) }
            throw e
        } finally {
            reset()
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
        var result: T
        begin()
        try {
            result = transactionFn()
            commit()
            return result
        } finally {
            reset()
        }
    }

    private fun reset() {
        pendingTransactions.get().clear()
        isTransactional.set(false)
    }

}