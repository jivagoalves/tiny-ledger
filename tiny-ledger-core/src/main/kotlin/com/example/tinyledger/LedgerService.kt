package com.example.tinyledger

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.example.tinyledger.repository.LedgerRepository
import java.math.BigDecimal

sealed class LedgerError {
    data class InsufficientBalance(val value: BigDecimal) : LedgerError()
}

class LedgerService(private val repository: LedgerRepository) {

    fun deposit(amount: BigDecimal): Either<LedgerError, Transaction> =
        addTransaction(Transaction(amount = amount, type = TransactionType.DEPOSIT))

    fun withdrawal(amount: BigDecimal): Either<LedgerError, Transaction> {
        val balance = getBalance()
        if (amount > balance) return LedgerError.InsufficientBalance(balance).left()
        return addTransaction(Transaction(amount = amount, type = TransactionType.WITHDRAWAL))
    }

    fun getBalance(): BigDecimal =
        repository.findAll().fold(BigDecimal.ZERO) { acc, t ->
            when (t.type) {
                TransactionType.DEPOSIT -> acc + t.amount
                TransactionType.WITHDRAWAL -> acc - t.amount
            }
        }

    fun getHistory(): List<Transaction> =
        repository.findAll()

    private fun addTransaction(transaction: Transaction): Either<LedgerError, Transaction> {
        return repository.save(transaction).right()
    }
}
