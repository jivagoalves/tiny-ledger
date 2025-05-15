package com.example.tinyledger

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.math.BigDecimal

sealed class LedgerError {
    data class InsufficientBalance(val value: BigDecimal) : LedgerError()
}

class LedgerService(private val repository: LedgerRepository) {

    private var isSession: Boolean = false
    private val transactions: MutableList<Transaction> = mutableListOf()

    fun begin() {
        isSession = true
        transactions.clear()
    }

    fun commit() {
        transactions.forEach { addTransaction(it) }
        isSession = false
    }

    fun rollback() {
        transactions.clear()
        isSession = false
    }

    fun deposit(amount: BigDecimal): Either<LedgerError, Transaction> {
        val transaction = Transaction(amount = amount, type = TransactionType.DEPOSIT)
        if (isSession) {
            addTemporaryTransaction(transaction)
        } else {
            addTransaction(transaction)
        }
        return transaction.right()
    }

    fun withdrawal(amount: BigDecimal): Either<LedgerError, Transaction> {
        val balance = getBalance()
        if (amount > balance) return LedgerError.InsufficientBalance(balance).left()
        val transaction = Transaction(amount = amount, type = TransactionType.WITHDRAWAL)
        if (isSession) {
            addTemporaryTransaction(transaction)
        } else {
            addTransaction(transaction)
        }
        return transaction.right()
    }

    fun getBalance(): BigDecimal {
        val allTransactions = if (isSession) transactions else repository.findAll()
        return allTransactions.fold(BigDecimal.ZERO) { acc, t ->
            when (t.type) {
                TransactionType.DEPOSIT -> acc + t.amount
                TransactionType.WITHDRAWAL -> acc - t.amount
            }
        }
    }

    fun getHistory(): List<Transaction> =
        repository.findAll()

    private fun addTransaction(transaction: Transaction): Either<LedgerError, Transaction> {
        return repository.save(transaction).right()
    }

    private fun addTemporaryTransaction(transaction: Transaction) {
        transactions.add(transaction)
    }
}
