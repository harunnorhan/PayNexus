package com.paynexus.paymentservice.paymentserver

import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorPaymentServerClientTest {
    @Test
    fun `default client policy applies five second request timeout to post and lookup`() {
        val observedTimeouts = mutableListOf<Pair<HttpMethod, Long?>>()
        val engine =
            MockEngine { request ->
                observedTimeouts +=
                    request.method to
                    request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                jsonResponse(response(outcome = "APPROVED"))
            }
        val client = KtorPaymentServerClient.create(BASE_URL, engine)

        try {
            runBlocking {
                client.submit(request(300L))
                client.lookup(PaymentId(PAYMENT_ID), IdempotencyKey(IDEMPOTENCY_KEY))
            }
        } finally {
            client.close()
        }

        assertEquals(
            listOf<Pair<HttpMethod, Long?>>(
                HttpMethod.Post to 5_000L,
                HttpMethod.Get to 5_000L,
            ),
            observedTimeouts,
        )
    }

    @Test
    fun `three synthetic amounts map to exact business outcomes`() {
        val cases =
            listOf(
                Triple(300L, response(outcome = "APPROVED"), PaymentOutcome.Approved),
                Triple(
                    301L,
                    response(outcome = "DECLINED", reason = "UNSPECIFIED"),
                    PaymentOutcome.Declined(DeclineReason.UNSPECIFIED),
                ),
                Triple(
                    302L,
                    response(outcome = "FAILED", reason = "PROCESSING_ERROR"),
                    PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR),
                ),
            )

        for ((amount, response, outcome) in cases) {
            val result = submit(amountMinorUnits = amount, responseBody = response)
            assertEquals(PaymentServerCallResult.Completed(outcome), result)
        }
    }

    @Test
    fun `request uses exact method path content type fields and values`() {
        val engine =
            MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/v1/payments", request.url.encodedPath)
                assertEquals(ContentType.Application.Json, request.body.contentType)

                val body = Json.parseToJsonElement(request.body.text()).jsonObject
                assertEquals(
                    setOf("paymentId", "idempotencyKey", "amountMinorUnits", "currency"),
                    body.keys,
                )
                assertEquals(JsonPrimitive(PAYMENT_ID), body["paymentId"])
                assertEquals(JsonPrimitive(IDEMPOTENCY_KEY), body["idempotencyKey"])
                assertEquals(JsonPrimitive(300L), body["amountMinorUnits"])
                assertEquals(JsonPrimitive("TRY"), body["currency"])
                jsonResponse(response(outcome = "APPROVED"))
            }

        assertEquals(PaymentServerCallResult.Completed(PaymentOutcome.Approved), submit(engine = engine))
    }

    @Test
    fun `maximum Long and exact mixed-case padded identifiers are preserved`() {
        val engine =
            MockEngine { request ->
                val body = Json.parseToJsonElement(request.body.text()).jsonObject
                assertEquals(JsonPrimitive(PAYMENT_ID), body["paymentId"])
                assertEquals(JsonPrimitive(IDEMPOTENCY_KEY), body["idempotencyKey"])
                assertEquals(JsonPrimitive(Long.MAX_VALUE), body["amountMinorUnits"])
                jsonResponse(response(outcome = "DECLINED", reason = "UNSPECIFIED"))
            }

        val result = submit(amountMinorUnits = Long.MAX_VALUE, engine = engine)

        assertEquals(
            PaymentServerCallResult.Completed(PaymentOutcome.Declined(DeclineReason.UNSPECIFIED)),
            result,
        )
    }

    @Test
    fun `http 400 invalid request remains separate from business outcomes`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.InvalidRequest),
            submit(responseStatus = HttpStatusCode.BadRequest, responseBody = """{"error":"INVALID_REQUEST"}"""),
        )
    }

    @Test
    fun `malformed and wrong http 400 bodies preserve bad request status`() {
        val bodies =
            listOf(
                "{",
                """{"error":"WRONG_ERROR"}""",
                """{"error":"INVALID_REQUEST","unknown":"value"}""",
                "",
            )

        bodies.forEach { body ->
            assertEquals(
                PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.MalformedResponse(400)),
                submit(responseStatus = HttpStatusCode.BadRequest, responseBody = body),
                body,
            )
        }
    }

    @Test
    fun `exact http 409 idempotency conflict is classified explicitly`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.IdempotencyConflict),
            submit(
                responseStatus = HttpStatusCode.Conflict,
                responseBody = """{"error":"IDEMPOTENCY_CONFLICT"}""",
            ),
        )
    }

    @Test
    fun `malformed and wrong http 409 bodies preserve conflict status`() {
        val bodies =
            listOf(
                "{",
                """{"error":"WRONG_ERROR"}""",
                """{"error":"IDEMPOTENCY_CONFLICT","unknown":"value"}""",
                "",
            )

        bodies.forEach { body ->
            assertEquals(
                PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.MalformedResponse(409)),
                submit(responseStatus = HttpStatusCode.Conflict, responseBody = body),
                body,
            )
        }
    }

    @Test
    fun `unexpected http status is classified without decoding a business outcome`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.UnexpectedHttpStatus(500)),
            submit(responseStatus = HttpStatusCode.InternalServerError, responseBody = "server-error"),
        )
    }

    @Test
    fun `submit returns every redirect without a hidden second post`() {
        REDIRECT_STATUSES.forEach { status ->
            val handlerEntries = AtomicInteger()
            val requestedUrls = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    requestedUrls += request.url.toString()
                    if (handlerEntries.incrementAndGet() == 1) {
                        respond(
                            content = "",
                            status = status,
                            headers = headersOf(HttpHeaders.Location, REDIRECT_TARGET),
                        )
                    } else {
                        jsonResponse(response(outcome = "APPROVED"))
                    }
                }

            assertEquals(
                PaymentServerCallResult.Unsuccessful(
                    PaymentServerClientFailure.UnexpectedHttpStatus(status.value),
                ),
                submit(engine = engine),
                status.toString(),
            )
            assertEquals(1, handlerEntries.get(), "No second POST is allowed for $status")
            assertEquals(listOf(PAYMENT_ENDPOINT), requestedUrls, status.toString())
            assertTrue(REDIRECT_TARGET !in requestedUrls, status.toString())
        }
    }

    @Test
    fun `malformed success json is a protocol failure`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.MalformedResponse(200)),
            submit(responseBody = "{"),
        )
    }

    @Test
    fun `unknown response property is rejected as contract drift`() {
        val body = response(outcome = "APPROVED").removeSuffix("}") + ",\"unknown\":\"value\"}"

        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.MalformedResponse(200)),
            submit(responseBody = body),
        )
    }

    @Test
    fun `unknown outcome is an invalid outcome protocol failure`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.InvalidOutcome),
            submit(responseBody = response(outcome = "UNKNOWN")),
        )
    }

    @Test
    fun `contradictory outcome and reason are an invalid outcome protocol failure`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.InvalidOutcome),
            submit(responseBody = response(outcome = "APPROVED", reason = "UNSPECIFIED")),
        )
    }

    @Test
    fun `mismatched payment identifier is rejected`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(
                PaymentServerClientFailure.IdentifierMismatch(ResponseIdentifier.PAYMENT_ID),
            ),
            submit(responseBody = response(paymentId = "different-payment", outcome = "APPROVED")),
        )
    }

    @Test
    fun `mismatched idempotency identifier is rejected`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(
                PaymentServerClientFailure.IdentifierMismatch(ResponseIdentifier.IDEMPOTENCY_KEY),
            ),
            submit(responseBody = response(idempotencyKey = "different-key", outcome = "APPROVED")),
        )
    }

    @Test
    fun `mock engine exception is a transport failure without exposing details`() {
        val engine = MockEngine { throw IOException("synthetic transport details") }

        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.Transport),
            submit(engine = engine),
        )
    }

    @Test
    fun `post request timeout is typed and does not retry`() {
        val handlerEntries = AtomicInteger()
        val engine =
            MockEngine {
                handlerEntries.incrementAndGet()
                awaitCancellation()
            }

        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.Timeout),
            submit(engine = engine, requestTimeoutMillis = TEST_REQUEST_TIMEOUT_MILLIS),
        )
        assertEquals(1, handlerEntries.get())
    }

    @Test
    fun `cancellation propagates instead of becoming a transport failure`() {
        val engine = MockEngine { throw CancellationException("synthetic cancellation") }

        assertFailsWith<CancellationException> {
            submit(engine = engine)
        }
    }

    @Test
    fun `lookup uses exact method path header and no payment body`() {
        val engine =
            MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("/v1/payments", request.url.encodedPath)
                assertTrue(request.url.parameters.isEmpty())
                assertEquals(IDEMPOTENCY_KEY, request.headers[PAYNEXUS_HEADER])
                assertEquals(null, request.body.contentType)
                assertTrue(request.body is OutgoingContent.NoContent)
                jsonResponse(response(outcome = "APPROVED"))
            }

        assertEquals(
            PaymentServerLookupResult.Found(PaymentOutcome.Approved),
            lookup(engine = engine),
        )
    }

    @Test
    fun `lookup maps all exact supported outcomes`() {
        val cases =
            listOf(
                response(outcome = "APPROVED") to PaymentOutcome.Approved,
                response(outcome = "DECLINED", reason = "UNSPECIFIED") to
                    PaymentOutcome.Declined(DeclineReason.UNSPECIFIED),
                response(outcome = "FAILED", reason = "PROCESSING_ERROR") to
                    PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR),
            )

        cases.forEach { (body, outcome) ->
            assertEquals(PaymentServerLookupResult.Found(outcome), lookup(responseBody = body))
        }
    }

    @Test
    fun `lookup rejects malformed invalid and mismatched success responses`() {
        val cases =
            listOf(
                "{" to PaymentServerClientFailure.MalformedResponse(200),
                response(outcome = "APPROVED").removeSuffix("}") + ",\"unknown\":\"value\"}" to
                    PaymentServerClientFailure.MalformedResponse(200),
                response(outcome = "UNKNOWN") to PaymentServerClientFailure.InvalidOutcome,
                response(outcome = "APPROVED", reason = "UNSPECIFIED") to
                    PaymentServerClientFailure.InvalidOutcome,
                response(paymentId = "different-payment", outcome = "APPROVED") to
                    PaymentServerClientFailure.IdentifierMismatch(ResponseIdentifier.PAYMENT_ID),
                response(idempotencyKey = "different-key", outcome = "APPROVED") to
                    PaymentServerClientFailure.IdentifierMismatch(ResponseIdentifier.IDEMPOTENCY_KEY),
            )

        cases.forEach { (body, failure) ->
            assertEquals(
                PaymentServerLookupResult.Unsuccessful(failure),
                lookup(responseBody = body),
                body,
            )
        }
    }

    @Test
    fun `only exact payment not found response becomes lookup not found`() {
        assertEquals(
            PaymentServerLookupResult.NotFound,
            lookup(HttpStatusCode.NotFound, """{"error":"PAYMENT_NOT_FOUND"}"""),
        )

        val invalidBodies =
            listOf(
                "{",
                """{"error":"WRONG_ERROR"}""",
                """{"error":"PAYMENT_NOT_FOUND","unknown":"value"}""",
                "",
            )
        invalidBodies.forEach { body ->
            assertEquals(
                PaymentServerLookupResult.Unsuccessful(PaymentServerClientFailure.MalformedResponse(404)),
                lookup(HttpStatusCode.NotFound, body),
                body,
            )
        }
    }

    @Test
    fun `lookup 400 and 500 remain unsuccessful`() {
        assertEquals(
            PaymentServerLookupResult.Unsuccessful(PaymentServerClientFailure.InvalidRequest),
            lookup(HttpStatusCode.BadRequest, """{"error":"INVALID_REQUEST"}"""),
        )
        assertEquals(
            PaymentServerLookupResult.Unsuccessful(PaymentServerClientFailure.UnexpectedHttpStatus(500)),
            lookup(HttpStatusCode.InternalServerError, """{"error":"INTERNAL_ERROR"}"""),
        )
    }

    @Test
    fun `lookup returns every redirect without a hidden second get`() {
        REDIRECT_STATUSES.forEach { status ->
            val handlerEntries = AtomicInteger()
            val requestedUrls = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    requestedUrls += request.url.toString()
                    if (handlerEntries.incrementAndGet() == 1) {
                        respond(
                            content = "",
                            status = status,
                            headers = headersOf(HttpHeaders.Location, REDIRECT_TARGET),
                        )
                    } else {
                        jsonResponse(response(outcome = "APPROVED"))
                    }
                }

            assertEquals(
                PaymentServerLookupResult.Unsuccessful(
                    PaymentServerClientFailure.UnexpectedHttpStatus(status.value),
                ),
                lookup(engine = engine),
                status.toString(),
            )
            assertEquals(1, handlerEntries.get(), "No second GET is allowed for $status")
            assertEquals(listOf(PAYMENT_ENDPOINT), requestedUrls, status.toString())
            assertTrue(REDIRECT_TARGET !in requestedUrls, status.toString())
        }
    }

    @Test
    fun `lookup transport failure is unsuccessful without exposing details`() {
        val engine = MockEngine { throw IOException("synthetic lookup transport details") }

        assertEquals(
            PaymentServerLookupResult.Unsuccessful(PaymentServerClientFailure.Transport),
            lookup(engine = engine),
        )
    }

    @Test
    fun `lookup request timeout is typed and does not retry`() {
        val handlerEntries = AtomicInteger()
        val engine =
            MockEngine {
                handlerEntries.incrementAndGet()
                awaitCancellation()
            }

        assertEquals(
            PaymentServerLookupResult.Unsuccessful(PaymentServerClientFailure.Timeout),
            lookup(engine = engine, requestTimeoutMillis = TEST_REQUEST_TIMEOUT_MILLIS),
        )
        assertEquals(1, handlerEntries.get())
    }

    @Test
    fun `lookup cancellation propagates`() {
        val engine = MockEngine { throw CancellationException("synthetic lookup cancellation") }

        assertFailsWith<CancellationException> {
            lookup(engine = engine)
        }
    }

    private fun submit(
        amountMinorUnits: Long = 300L,
        responseStatus: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = response(outcome = "APPROVED"),
        engine: MockEngine = MockEngine { jsonResponse(responseBody, responseStatus) },
        requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ): PaymentServerCallResult {
        val client = KtorPaymentServerClient.create(BASE_URL, engine, requestTimeoutMillis)
        return try {
            runBlocking {
                client.submit(request(amountMinorUnits))
            }
        } finally {
            client.close()
        }
    }

    private fun lookup(
        responseStatus: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = response(outcome = "APPROVED"),
        engine: MockEngine = MockEngine { jsonResponse(responseBody, responseStatus) },
        requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ): PaymentServerLookupResult {
        val client = KtorPaymentServerClient.create(BASE_URL, engine, requestTimeoutMillis)
        return try {
            runBlocking {
                client.lookup(PaymentId(PAYMENT_ID), IdempotencyKey(IDEMPOTENCY_KEY))
            }
        } finally {
            client.close()
        }
    }

    private fun request(amountMinorUnits: Long) =
        PaymentServerRequest(
            paymentId = PaymentId(PAYMENT_ID),
            idempotencyKey = IdempotencyKey(IDEMPOTENCY_KEY),
            amount = PaymentAmount(Money(amountMinorUnits, CurrencyCode.TRY)),
        )

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun OutgoingContent.text(): String =
        when (this) {
            is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
            else -> error("Expected a byte-array request body.")
        }

    private fun response(
        paymentId: String = PAYMENT_ID,
        idempotencyKey: String = IDEMPOTENCY_KEY,
        outcome: String,
        reason: String? = null,
    ): String {
        val encodedReason = reason?.let { "\"$it\"" } ?: "null"
        return """{"paymentId":"$paymentId","idempotencyKey":"$idempotencyKey",""" +
            """"outcome":"$outcome","reason":$encodedReason}"""
    }

    private companion object {
        val BASE_URL = Url("https://payment-server.test")
        val REDIRECT_STATUSES =
            listOf(
                HttpStatusCode.MovedPermanently,
                HttpStatusCode.Found,
                HttpStatusCode.SeeOther,
                HttpStatusCode.TemporaryRedirect,
                HttpStatusCode.PermanentRedirect,
            )
        const val PAYMENT_ID = " Mixed-Case Payment-ID "
        const val IDEMPOTENCY_KEY = " Mixed-Case Idempotency-Key "
        const val PAYNEXUS_HEADER = "PayNexus-Idempotency-Key"
        const val PAYMENT_ENDPOINT = "https://payment-server.test/v1/payments"
        const val REDIRECT_TARGET = "https://redirect-target.test/replayed-payment"
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 5_000L
        const val TEST_REQUEST_TIMEOUT_MILLIS = 50L
    }
}
