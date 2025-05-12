package com.example.tinyledger.api

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@WebMvcTest
@DisplayName("/hello")
class HelloWorldControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `GET - should return 200`() {
        mockMvc
            .perform(get("/hello")
                .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk)
    }
}