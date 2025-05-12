package com.example.tinyledger

import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant

val serial = AtomicLong()

enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL
}

data class Transaction(
    val id: Long = serial.incrementAndGet(),
    val timestamp: Instant = Instant.now(),
    val amount: BigDecimal,
    val type: TransactionType,
)