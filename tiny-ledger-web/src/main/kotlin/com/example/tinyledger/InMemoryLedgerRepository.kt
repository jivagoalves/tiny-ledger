package com.example.tinyledger

import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentLinkedQueue

@Repository
class InMemoryLedgerRepository : LedgerRepository {
    private val store = ConcurrentLinkedQueue<Transaction>()

    override fun save(transaction: Transaction): Transaction {
        store.add(transaction)
        return transaction
    }

    override fun findAll(): List<Transaction> = store.toList()
}
