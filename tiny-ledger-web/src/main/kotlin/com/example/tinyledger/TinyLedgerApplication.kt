package com.example.tinyledger

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TinyLedgerApplication

fun main(args: Array<String>) {
	runApplication<TinyLedgerApplication>(*args)
}
