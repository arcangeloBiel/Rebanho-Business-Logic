package com.jakatech.rebanhomais.kmp

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, Ktor!", response.bodyAsText())
    }

    @Test
    fun animaisSemTokenRespondem401() = testApplication {
        application {
            module()
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/animais").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/animais/qualquer-id").status)
    }

    @Test
    fun animaisComTokenInvalidoRespondem401() = testApplication {
        application {
            module()
        }
        val response = client.get("/animais") {
            header(HttpHeaders.Authorization, "Bearer token-falso")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}