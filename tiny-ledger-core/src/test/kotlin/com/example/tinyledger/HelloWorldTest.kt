package com.example.tinyledger

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HelloWorldTest {
    @Test
    fun `should greet by name`() {
        assertEquals(
            "Hello Alice",
            HelloWorld("Alice").hello()
        )
    }
}