package com.example.tinyledger

import com.example.tinyledger.repository.LedgerRepository
import com.example.tinyledger.repository.TransactionalLedgerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LedgerServiceTest {
    private val store = mutableListOf<Transaction>()

    val repository = object : TransactionalLedgerRepository {
        override fun save(transaction: Transaction): Transaction {
            store.add(transaction)
            return transaction
        }

        override fun findAll(): List<Transaction> = store
        override fun delete(transaction: Transaction): Boolean =
            store.remove(transaction)

        override fun begin() = Unit
        override fun commit() = Unit
        override fun rollback() = Unit
        override fun <T> withTransaction(transactionFn: () -> T): T = transactionFn()
    }

    val service = LedgerService(repository)

    @BeforeEach
    fun setUp() {
        store.clear()
    }

    @Test
    fun `should deposit amount and return transaction`() {
        val amount = BigDecimal("100.00")

        val result = service.deposit(amount)

        assertTrue(result.isRight())
        assertEquals(amount, result.getOrNull()!!.amount)
    }

    @Test
    fun `should withdraw if sufficient balance`() {
        service.deposit(BigDecimal("200.00"))
        val amount = BigDecimal("50.00")

        val result = service.withdrawal(amount)

        assertTrue(result.isRight())
        assertEquals(amount, result.getOrNull()!!.amount)
    }

    @Test
    fun `should not allow withdrawal if insufficient balance`() {
        val amount = BigDecimal("100.00")

        val result = service.withdrawal(amount)

        assertTrue(result.isLeft())
        assertEquals(LedgerError.InsufficientBalance(BigDecimal.ZERO), result.leftOrNull())
    }

    @Test
    fun `should compute correct balance`() {
        service.deposit(BigDecimal("100.00"))
        service.withdrawal(BigDecimal("30.00"))

        val balance = service.getBalance()

        assertEquals(BigDecimal("70.00"), balance)
    }

    @Test
    fun `should return the history of transactions`() {
        service.deposit(BigDecimal("100.00"))
        service.withdrawal(BigDecimal("30.00"))

        val history = service.getHistory()

        assertEquals(
            listOf(
                BigDecimal("100.00"),
                BigDecimal("30.00")
            ),
            history.map(Transaction::amount)
        )
        assertEquals(
            listOf(
                TransactionType.DEPOSIT,
                TransactionType.WITHDRAWAL
            ),
            history.map(Transaction::type)
        )
    }
}