package com.example.tinyledger

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class LedgerIT @Autowired constructor(
    val mockMvc: MockMvc,
) {
    @Test
    fun `deposit and get balance`() {
        // Perform deposit
        mockMvc.post("/api/ledger/deposit") {
            contentType = MediaType.APPLICATION_JSON
            content = """{ "amount": 100.0 }"""
        }.andExpect {
            status { isOk() }
        }

        // Get balance
        val result = mockMvc.get("/api/ledger/balance")
            .andExpect {
                status { isOk() }
            }
            .andReturn()

        val response = result.response.contentAsString
        Assertions.assertEquals("100.0", response)
    }

    @Test
    fun `withdraw fails with insufficient balance`() {
        mockMvc.post("/api/ledger/withdraw") {
            contentType = MediaType.APPLICATION_JSON
            content = """{ "amount": 100.0 }"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `deposit fails with negative amount`() {
        mockMvc.post("/api/ledger/deposit") {
            contentType = MediaType.APPLICATION_JSON
            content = """{ "amount": -100.0 }"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}