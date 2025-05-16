package com.example.tinyledger.repository

import com.example.tinyledger.Transaction
import java.util.concurrent.ConcurrentLinkedQueue

class InMemoryTransactionalRepository(val ledgerRepository: LedgerRepository) : TransactionalLedgerRepository {
    private var isTransactional = false

    private val transactions = ConcurrentLinkedQueue<Transaction>()

    override fun begin() {
        isTransactional = true
    }

    override fun commit() =
        try {
            transactions.forEach { ledgerRepository.save(it) }
        } catch (_: RuntimeException) {
            transactions.forEach { ledgerRepository.delete(it) }
        } finally {
            rollback()
        }

    override fun rollback() {
        transactions.clear()
        isTransactional = false
    }

    override fun save(transaction: Transaction): Transaction =
        if (isTransactional) {
            transactions.add(transaction)
            transaction
        } else ledgerRepository.save(transaction)

    override fun findAll(): List<Transaction> =
        if (isTransactional) {
            ledgerRepository.findAll() + transactions.toList()
        } else {
            ledgerRepository.findAll()
        }

    override fun delete(transaction: Transaction): Boolean =
        transactions.remove(transaction)
}