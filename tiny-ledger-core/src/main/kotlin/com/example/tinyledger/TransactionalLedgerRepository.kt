package com.example.tinyledger

interface TransactionalLedgerRepository: LedgerRepository {
    fun begin()
    fun commit()
    fun rollback()
}