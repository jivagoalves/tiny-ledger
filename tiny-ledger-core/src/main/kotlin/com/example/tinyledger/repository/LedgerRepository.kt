package com.example.tinyledger.repository

import com.example.tinyledger.Transaction

interface LedgerRepository {
    fun save(transaction: Transaction): Transaction
    fun findAll(): List<Transaction>
    fun delete(transaction: Transaction): Boolean
}