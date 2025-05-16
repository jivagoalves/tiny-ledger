package com.example.tinyledger.repository

interface TransactionalLedgerRepository: LedgerRepository {
    fun begin()
    fun commit()
    fun rollback()
}