package com.example.tinyledger

interface LedgerRepository {
    fun save(transaction: Transaction): Transaction
    fun findAll(): List<Transaction>
}