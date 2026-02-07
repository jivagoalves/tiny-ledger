package com.example.tinyledger

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.example.tinyledger.repository.LedgerRepository
import com.example.tinyledger.repository.TransactionalLedgerRepository
import java.math.BigDecimal

sealed class LedgerError {
    data class InsufficientBalance(val value: BigDecimal) : LedgerError()
}

class LedgerService(private val repository: TransactionalLedgerRepository) {

    fun deposit(amount: BigDecimal): Either<LedgerError, Transaction> = repository.withTransaction {
        addTransaction(Transaction(amount = amount, type = TransactionType.DEPOSIT))
    }

    fun withdrawal(amount: BigDecimal): Either<LedgerError, Transaction> = repository.withTransaction {
        val balance = getBalance()
        if (amount > balance)
            return@withTransaction LedgerError.InsufficientBalance(balance).left()
        return@withTransaction addTransaction(Transaction(amount = amount, type = TransactionType.WITHDRAWAL))
    }

    fun getBalance(): BigDecimal = repository.withTransaction {
        repository.findAll().fold(BigDecimal.ZERO) { sum, t ->
            when (t.type) {
                TransactionType.DEPOSIT -> sum + t.amount
                TransactionType.WITHDRAWAL -> sum - t.amount
            }
        }
    }

    fun getHistory(): List<Transaction> = repository.withTransaction {
        repository.findAll()
    }

    private fun addTransaction(transaction: Transaction): Either<LedgerError, Transaction> {
        return repository.save(transaction).right()
    }
}
