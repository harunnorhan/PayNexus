package com.paynexus.server.application

import com.paynexus.server.application.payment.PAYNEXUS_IDEMPOTENCY_KEY_HEADER
import com.paynexus.server.application.payment.validateLookupIdempotencyKey
import com.paynexus.server.domain.payment.PaymentRepository
import com.paynexus.server.domain.payment.PaymentRepositoryException
import com.paynexus.server.domain.payment.StoredPaymentRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.get
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PaymentApiTest {
    @Test
    fun `first payment and same intent replay return the same stored response`() =
        testApplication {
            val repository = TestPaymentRepository()
            application { testModule(repository) }

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
            application { testModule() }
            client.postPayment(requestBody(paymentId = "\"original-payment\""))

            val conflict = client.postPayment(requestBody(paymentId = "\"different-payment\""))

            assertJsonResponse(conflict, HttpStatusCode.Conflict, """{"error":"IDEMPOTENCY_CONFLICT"}""")
        }

    @Test
    fun `same key with a different amount returns exact idempotency conflict`() =
        testApplication {
            application { testModule() }
            client.postPayment(requestBody(amountMinorUnits = "300"))

            val conflict = client.postPayment(requestBody(amountMinorUnits = "301"))

            assertJsonResponse(conflict, HttpStatusCode.Conflict, """{"error":"IDEMPOTENCY_CONFLICT"}""")
        }

    @Test
    fun `invalid request does not reach persistence`() =
        testApplication {
            val repository = TestPaymentRepository()
            application { testModule(repository) }

            val response = client.postPayment(requestBody(amountMinorUnits = "0"))

            assertInvalidResponse(response)
            assertEquals(0, repository.storeCalls.get())
        }

    @Test
    fun `maximum Long amount replays without narrowing`() =
        testApplication {
            application { testModule() }

            val first = client.postPayment(requestBody(amountMinorUnits = Long.MAX_VALUE.toString()))
            val replay = client.postPayment(requestBody(amountMinorUnits = Long.MAX_VALUE.toString()))

            val expected = paymentResponseBody(outcome = "DECLINED", reason = "UNSPECIFIED")
            assertJsonResponse(first, HttpStatusCode.OK, expected)
            assertJsonResponse(replay, HttpStatusCode.OK, expected)
        }

    @Test
    fun `repository failure returns sanitized internal error`() =
        testApplication {
            val repository =
                object : PaymentRepository {
                    override fun storeOrRead(candidate: StoredPaymentRecord) =
                        throw PaymentRepositoryException(
                            IllegalStateException("jdbc:sqlite:/private/path SQL supplied-payment-id"),
                        )

                    override fun findByIdempotencyKey(idempotencyKey: String) = null
                }
            application { testModule(repository) }

            val response = client.postPayment(requestBody())

            assertJsonResponse(response, HttpStatusCode.InternalServerError, """{"error":"INTERNAL_ERROR"}""")
        }

    @Test
    fun `lookup returns the exact authoritative stored response`() =
        testApplication {
            val repository = TestPaymentRepository()
            application { testModule(repository) }
            val paymentId = " Mixed-Case Payment-ID "
            val idempotencyKey = " Mixed-Case Idempotency-Key "
            client.postPayment(
                requestBody(
                    paymentId = "\"$paymentId\"",
                    idempotencyKey = "\"$idempotencyKey\"",
                    amountMinorUnits = "302",
                ),
            )

            val response = client.getPayment(listOf(idempotencyKey))

            assertJsonResponse(
                response,
                HttpStatusCode.OK,
                paymentResponseBody(
                    paymentId = paymentId,
                    idempotencyKey = idempotencyKey,
                    outcome = "FAILED",
                    reason = "PROCESSING_ERROR",
                ),
            )
            assertEquals(1, repository.findCalls.get())
        }

    @Test
    fun `unknown valid lookup returns exact payment not found response`() =
        testApplication {
            application { testModule() }

            val response = client.getPayment(listOf("unknown-key"))

            assertJsonResponse(response, HttpStatusCode.NotFound, """{"error":"PAYMENT_NOT_FOUND"}""")
        }

    @Test
    fun `missing blank and oversized lookup headers are rejected`() =
        testApplication {
            val repository = TestPaymentRepository()
            application { testModule(repository) }

            val responses =
                listOf(
                    "missing" to client.getPayment(),
                    "empty" to client.getPayment(listOf("")),
                    "whitespace" to client.getPayment(listOf(" \t")),
                    "oversized" to client.getPayment(listOf("x".repeat(257))),
                )

            responses.forEach { (context, response) -> assertInvalidResponse(response, context) }
            assertEquals(0, repository.findCalls.get())
        }

    @Test
    fun `observable duplicate lookup header values are rejected`() {
        assertNull(validateLookupIdempotencyKey(listOf("first-key", "second-key")))
    }

    @Test
    fun `test client combined duplicate header is treated as one exact value`() =
        testApplication {
            application { testModule() }

            val response = client.getPayment(listOf("first-key", "second-key"))

            assertJsonResponse(response, HttpStatusCode.NotFound, """{"error":"PAYMENT_NOT_FOUND"}""")
        }

    @Test
    fun `lookup accepts exactly 256 UTF-16 code units`() =
        testApplication {
            val repository = TestPaymentRepository()
            application { testModule(repository) }
            val idempotencyKey = "x".repeat(256)
            client.postPayment(requestBody(idempotencyKey = "\"$idempotencyKey\""))

            val response = client.getPayment(listOf(idempotencyKey))

            assertJsonResponse(
                response,
                HttpStatusCode.OK,
                paymentResponseBody(idempotencyKey = idempotencyKey, outcome = "APPROVED"),
            )
        }

    @Test
    fun `lookup preserves case and nonblank surrounding whitespace`() =
        testApplication {
            application { testModule() }
            val exactKey = " Mixed-Case-Key "
            client.postPayment(requestBody(idempotencyKey = "\"$exactKey\""))

            val exact = client.getPayment(listOf(exactKey))
            val differentCase = client.getPayment(listOf(" mixed-case-key "))
            val trimmed = client.getPayment(listOf("Mixed-Case-Key"))

            assertJsonResponse(
                exact,
                HttpStatusCode.OK,
                paymentResponseBody(idempotencyKey = exactKey, outcome = "APPROVED"),
            )
            assertJsonResponse(differentCase, HttpStatusCode.NotFound, """{"error":"PAYMENT_NOT_FOUND"}""")
            assertJsonResponse(trimmed, HttpStatusCode.NotFound, """{"error":"PAYMENT_NOT_FOUND"}""")
        }

    @Test
    fun `lookup ignores a JSON body and reads only the exact header`() =
        testApplication {
            application { testModule() }
            client.postPayment(requestBody())

            val response = client.getPayment(listOf("idempotency-key"), body = "{")

            assertJsonResponse(response, HttpStatusCode.OK, paymentResponseBody(outcome = "APPROVED"))
        }

    @Test
    fun `lookup repository failure returns only sanitized internal error`() =
        testApplication {
            val lookupKey = "supplied-sensitive-lookup-key"
            val failureDetails = "jdbc:sqlite:/private/path SELECT payment-id"
            application {
                module(
                    processPayment = testPaymentProcessor()::process,
                    findPaymentByIdempotencyKey = {
                        throw PaymentRepositoryException(IllegalStateException(failureDetails))
                    },
                )
            }

            val response = client.getPayment(listOf(lookupKey))
            val body = response.bodyAsText()

            assertJsonResponse(response, HttpStatusCode.InternalServerError, """{"error":"INTERNAL_ERROR"}""")
            assertFalse(body.contains(lookupKey))
            assertFalse(body.contains(failureDetails))
        }

    @Test
    fun `approved payment preserves exact identifiers and returns deterministic JSON`() =
        testApplication {
            application { testModule() }

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
            application { testModule() }

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
            application { testModule() }

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
            application { testModule() }

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
            application { testModule() }
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
            application { testModule() }
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
            application { testModule() }

            listOf("0", "-1", Long.MIN_VALUE.toString()).forEach { amount ->
                assertInvalidResponse(client.postPayment(requestBody(amountMinorUnits = amount)))
            }
        }

    @Test
    fun `blank identifiers are rejected`() =
        testApplication {
            application { testModule() }

            listOf("\"\"", "\" \"", "\"\\t\\n\"").forEach { blank ->
                assertInvalidResponse(client.postPayment(requestBody(paymentId = blank)))
                assertInvalidResponse(client.postPayment(requestBody(idempotencyKey = blank)))
            }
        }

    @Test
    fun `unsupported and noncanonical currencies are rejected`() =
        testApplication {
            application { testModule() }

            listOf("USD", "try", " TRY", "TRY ", "").forEach { currency ->
                assertInvalidResponse(client.postPayment(requestBody(currency = "\"$currency\"")))
            }
        }

    @Test
    fun `malformed JSON and an empty body are rejected`() =
        testApplication {
            application { testModule() }

            listOf("", "{", "not-json").forEach { body ->
                assertInvalidResponse(client.postPayment(body))
            }
        }

    @Test
    fun `every missing required field is rejected`() =
        testApplication {
            application { testModule() }

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
            application { testModule() }

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
            application { testModule() }

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
            application { testModule() }

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

    private suspend fun HttpClient.getPayment(
        idempotencyKeys: List<String> = emptyList(),
        body: String? = null,
    ): HttpResponse =
        get("/v1/payments") {
            idempotencyKeys.forEach { headers.append(PAYNEXUS_IDEMPOTENCY_KEY_HEADER, it) }
            if (body != null) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(body)
            }
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
