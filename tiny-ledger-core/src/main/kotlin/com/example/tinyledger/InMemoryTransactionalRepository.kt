package com.example.tinyledger

import java.util.concurrent.ConcurrentLinkedQueue

class InMemoryTransactionalRepository(val ledgerRepository: LedgerRepository) : TransactionalLedgerRepository {
    private var isTransactional = false

    private val transactions = ConcurrentLinkedQueue<Transaction>()

    override fun begin() {
        isTransactional = true
    }

    override fun commit() {
        transactions.forEach { ledgerRepository.save(it) }
        isTransactional = false
    }

    override fun rollback() {
        transactions.clear()
        isTransactional = false
    }

    override fun save(transaction: Transaction): Transaction {
        return if (isTransactional) {
            transactions.add(transaction)
            transaction
        } else {
            ledgerRepository.save(transaction)
        }
    }

    override fun findAll(): List<Transaction> {
        return if (isTransactional) {
            ledgerRepository.findAll() + transactions.toList()
        } else {
            ledgerRepository.findAll()
        }
    }
}