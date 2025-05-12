package com.example.tinyledger

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/ledger")
class LedgerController(private val ledgerService: LedgerService) {

    data class TransactionRequest(
        @field:DecimalMin("0.01", message = "Amount must be greater than zero")
        val amount: BigDecimal,
    )

    @PostMapping("/deposit")
    fun deposit(@Valid @RequestBody request: TransactionRequest): ResponseEntity<Transaction> =
        ledgerService.deposit(request.amount)
            .fold(
                { throw IllegalArgumentException("Unexpected error") },
                { ResponseEntity.ok(it) }
            )

    @PostMapping("/withdraw")
    fun withdraw(@Valid @RequestBody request: TransactionRequest): ResponseEntity<Any> =
        ledgerService.withdrawal(request.amount)
            .fold(
                { err ->
                    when (err) {
                        is LedgerError.InsufficientBalance ->
                            ResponseEntity
                                .badRequest()
                                .body("Insufficient balance: ${err.value}")
                    }
                },
                { ResponseEntity.ok(it) }
            )

    @GetMapping("/balance")
    fun balance(): ResponseEntity<BigDecimal> =
        ResponseEntity.ok(ledgerService.getBalance())

    @GetMapping("/history")
    fun history(): ResponseEntity<List<Transaction>> =
        ResponseEntity.ok(ledgerService.getHistory())
}