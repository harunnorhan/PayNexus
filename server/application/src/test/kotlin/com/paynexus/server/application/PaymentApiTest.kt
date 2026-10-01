package com.paynexus.server.application

import com.paynexus.server.domain.payment.IdempotentPaymentProcessor
import com.paynexus.server.domain.payment.PaymentRepository
import com.paynexus.server.domain.payment.PaymentRepositoryException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentApiTest {
    @Test
    fun `first payment and same intent replay return the same stored response`() =
        testApplication {
            val repository = TestPaymentRepository()
            application { module(testPaymentProcessor(repository)::process) }

            val first = client.postPayment(requestBody(amountMinorUnits = "301"))
            val replay = client.postPayment(requestBody(amountMinorUnits = "301"))

            val expected = paymentResponseBody(outcome = "DECLINED", reason = "UNSPECIFIED")
            assertJsonResponse(first, HttpStatusCode.OK, expected)
            assertJsonResponse(replay, HttpStatusCode.OK, expected)
            assertEquals(2, repository.storeCalls.get())
        }

    @Test
    fun `same key with a different payment ID returns exact idempotency conflict`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }
            client.postPayment(requestBody(paymentId = "\"original-payment\""))

            val conflict = client.postPayment(requestBody(paymentId = "\"different-payment\""))

            assertJsonResponse(conflict, HttpStatusCode.Conflict, """{"error":"IDEMPOTENCY_CONFLICT"}""")
        }

    @Test
    fun `same key with a different amount returns exact idempotency conflict`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }
            client.postPayment(requestBody(amountMinorUnits = "300"))

            val conflict = client.postPayment(requestBody(amountMinorUnits = "301"))

            assertJsonResponse(conflict, HttpStatusCode.Conflict, """{"error":"IDEMPOTENCY_CONFLICT"}""")
        }

    @Test
    fun `invalid request does not reach persistence`() =
        testApplication {
            val repository = TestPaymentRepository()
            application { module(testPaymentProcessor(repository)::process) }

            val response = client.postPayment(requestBody(amountMinorUnits = "0"))

            assertInvalidResponse(response)
            assertEquals(0, repository.storeCalls.get())
        }

    @Test
    fun `maximum Long amount replays without narrowing`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            val first = client.postPayment(requestBody(amountMinorUnits = Long.MAX_VALUE.toString()))
            val replay = client.postPayment(requestBody(amountMinorUnits = Long.MAX_VALUE.toString()))

            val expected = paymentResponseBody(outcome = "DECLINED", reason = "UNSPECIFIED")
            assertJsonResponse(first, HttpStatusCode.OK, expected)
            assertJsonResponse(replay, HttpStatusCode.OK, expected)
        }

    @Test
    fun `repository failure returns sanitized internal error`() =
        testApplication {
            val processor =
                IdempotentPaymentProcessor(
                    PaymentRepository {
                        throw PaymentRepositoryException(
                            IllegalStateException("jdbc:sqlite:/private/path SQL supplied-payment-id"),
                        )
                    },
                )
            application { module(processor::process) }

            val response = client.postPayment(requestBody())

            assertJsonResponse(response, HttpStatusCode.InternalServerError, """{"error":"INTERNAL_ERROR"}""")
        }

    @Test
    fun `approved payment preserves exact identifiers and returns deterministic JSON`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            val response =
                client.postPayment(
                    requestBody(
                        paymentId = "\" Mixed-Case Payment-ID \"",
                        idempotencyKey = "\" Mixed-Case Idempotency-Key \"",
                    ),
                )

            assertJsonResponse(
                response = response,
                status = HttpStatusCode.OK,
                body =
                    paymentResponseBody(
                        paymentId = " Mixed-Case Payment-ID ",
                        idempotencyKey = " Mixed-Case Idempotency-Key ",
                        outcome = "APPROVED",
                    ),
            )
        }

    @Test
    fun `declined payment returns explicit unspecified reason`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            val response = client.postPayment(requestBody(amountMinorUnits = "301"))

            assertJsonResponse(
                response,
                HttpStatusCode.OK,
                paymentResponseBody(outcome = "DECLINED", reason = "UNSPECIFIED"),
            )
        }

    @Test
    fun `failed payment returns explicit processing error reason`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            val response = client.postPayment(requestBody(amountMinorUnits = "302"))

            assertJsonResponse(
                response,
                HttpStatusCode.OK,
                paymentResponseBody(outcome = "FAILED", reason = "PROCESSING_ERROR"),
            )
        }

    @Test
    fun `maximum Long amount is accepted without narrowing`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            val response = client.postPayment(requestBody(amountMinorUnits = Long.MAX_VALUE.toString()))

            assertJsonResponse(
                response,
                HttpStatusCode.OK,
                paymentResponseBody(outcome = "DECLINED", reason = "UNSPECIFIED"),
            )
        }

    @Test
    fun `identifiers at the 256 code unit boundary are accepted exactly`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }
            val identifier = "x".repeat(256)

            val response =
                client.postPayment(
                    requestBody(
                        paymentId = "\"$identifier\"",
                        idempotencyKey = "\"$identifier\"",
                    ),
                )

            assertJsonResponse(
                response,
                HttpStatusCode.OK,
                """{"paymentId":"$identifier","idempotencyKey":"$identifier","outcome":"APPROVED","reason":null}""",
            )
        }

    @Test
    fun `identifiers over the transport boundary are rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }
            val oversized = "x".repeat(257)

            listOf(
                requestBody(paymentId = "\"$oversized\""),
                requestBody(idempotencyKey = "\"$oversized\""),
            ).forEach { body ->
                assertInvalidResponse(client.postPayment(body), context = body)
            }
        }

    @Test
    fun `zero and negative amounts are rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            listOf("0", "-1", Long.MIN_VALUE.toString()).forEach { amount ->
                assertInvalidResponse(client.postPayment(requestBody(amountMinorUnits = amount)))
            }
        }

    @Test
    fun `blank identifiers are rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            listOf("\"\"", "\" \"", "\"\\t\\n\"").forEach { blank ->
                assertInvalidResponse(client.postPayment(requestBody(paymentId = blank)))
                assertInvalidResponse(client.postPayment(requestBody(idempotencyKey = blank)))
            }
        }

    @Test
    fun `unsupported and noncanonical currencies are rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            listOf("USD", "try", " TRY", "TRY ", "").forEach { currency ->
                assertInvalidResponse(client.postPayment(requestBody(currency = "\"$currency\"")))
            }
        }

    @Test
    fun `malformed JSON and an empty body are rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            listOf("", "{", "not-json").forEach { body ->
                assertInvalidResponse(client.postPayment(body))
            }
        }

    @Test
    fun `every missing required field is rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            listOf(
                """{"idempotencyKey":"idempotency-key","amountMinorUnits":300,"currency":"TRY"}""",
                """{"paymentId":"payment-id","amountMinorUnits":300,"currency":"TRY"}""",
                """{"paymentId":"payment-id","idempotencyKey":"idempotency-key","currency":"TRY"}""",
                """{"paymentId":"payment-id","idempotencyKey":"idempotency-key","amountMinorUnits":300}""",
            ).forEach { body ->
                assertInvalidResponse(client.postPayment(body))
            }
        }

    @Test
    fun `explicit null required fields are rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            listOf(
                requestBody(paymentId = "null"),
                requestBody(idempotencyKey = "null"),
                requestBody(amountMinorUnits = "null"),
                requestBody(currency = "null"),
            ).forEach { body ->
                assertInvalidResponse(client.postPayment(body))
            }
        }

    @Test
    fun `wrong JSON field types and integers outside Long range are rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            listOf(
                requestBody(paymentId = "123"),
                requestBody(idempotencyKey = "true"),
                requestBody(amountMinorUnits = "\"300\""),
                requestBody(amountMinorUnits = "300.0"),
                requestBody(amountMinorUnits = "9223372036854775808"),
                requestBody(currency = "123"),
            ).forEach { body ->
                assertInvalidResponse(client.postPayment(body), context = body)
            }
        }

    @Test
    fun `unknown JSON properties are rejected`() =
        testApplication {
            application { module(testPaymentProcessor()::process) }

            val response =
                client.postPayment(
                    requestBody().removeSuffix("}") + ""","unknown":"value"}""",
                )

            assertInvalidResponse(response)
        }

    private suspend fun HttpClient.postPayment(body: String): HttpResponse =
        post("/v1/payments") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }

    private suspend fun assertInvalidResponse(
        response: HttpResponse,
        context: String? = null,
    ) {
        assertJsonResponse(response, HttpStatusCode.BadRequest, """{"error":"INVALID_REQUEST"}""", context)
    }

    private suspend fun assertJsonResponse(
        response: HttpResponse,
        status: HttpStatusCode,
        body: String,
        context: String? = null,
    ) {
        assertEquals(status, response.status, context)
        val contentType = ContentType.parse(requireNotNull(response.headers[HttpHeaders.ContentType]))
        assertEquals("application", contentType.contentType)
        assertEquals("json", contentType.contentSubtype)
        assertEquals(body, response.bodyAsText())
    }

    private fun requestBody(
        paymentId: String = "\"payment-id\"",
        idempotencyKey: String = "\"idempotency-key\"",
        amountMinorUnits: String = "300",
        currency: String = "\"TRY\"",
    ): String = """{"paymentId":$paymentId,"idempotencyKey":$idempotencyKey,"amountMinorUnits":$amountMinorUnits,"currency":$currency}"""

    private fun paymentResponseBody(
        paymentId: String = "payment-id",
        idempotencyKey: String = "idempotency-key",
        outcome: String,
        reason: String? = null,
    ): String {
        val encodedReason = reason?.let { "\"$it\"" } ?: "null"
        return """{"paymentId":"$paymentId","idempotencyKey":"$idempotencyKey",""" +
            """"outcome":"$outcome","reason":$encodedReason}"""
    }
}
