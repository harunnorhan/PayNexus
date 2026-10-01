package com.paynexus.server.application

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplicationTest {
    @Test
    fun `health endpoint returns deterministic healthy JSON response`() =
        testApplication {
            application {
                module(testPaymentProcessor()::process)
            }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("application/json; charset=UTF-8", response.headers[HttpHeaders.ContentType])
            assertEquals("{\"status\":\"ok\",\"service\":\"paynexus-payment-server\"}", response.bodyAsText())
        }

    @Test
    fun `payment database path uses the local default only when configuration is absent`() {
        assertEquals("./data/paynexus-payments.db", resolvePaymentDatabasePath(emptyMap()).toString())
        assertEquals(
            "/tmp/paynexus-test.db",
            resolvePaymentDatabasePath(mapOf("PAYNEXUS_PAYMENT_DB_PATH" to "/tmp/paynexus-test.db")).toString(),
        )
    }

    @Test
    fun `blank configured payment database path fails startup configuration`() {
        assertFailsWith<IllegalStateException> {
            resolvePaymentDatabasePath(mapOf("PAYNEXUS_PAYMENT_DB_PATH" to "  "))
        }
    }
}
