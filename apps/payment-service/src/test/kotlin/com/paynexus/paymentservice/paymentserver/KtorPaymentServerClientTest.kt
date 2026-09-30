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
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorPaymentServerClientTest {
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
    fun `unexpected http status is classified without decoding a business outcome`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.UnexpectedHttpStatus(500)),
            submit(responseStatus = HttpStatusCode.InternalServerError, responseBody = "server-error"),
        )
    }

    @Test
    fun `malformed success json is a protocol failure`() {
        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.MalformedResponse),
            submit(responseBody = "{"),
        )
    }

    @Test
    fun `unknown response property is rejected as contract drift`() {
        val body = response(outcome = "APPROVED").removeSuffix("}") + ",\"unknown\":\"value\"}"

        assertEquals(
            PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.MalformedResponse),
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
    fun `cancellation propagates instead of becoming a transport failure`() {
        val engine = MockEngine { throw CancellationException("synthetic cancellation") }

        assertFailsWith<CancellationException> {
            submit(engine = engine)
        }
    }

    private fun submit(
        amountMinorUnits: Long = 300L,
        responseStatus: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = response(outcome = "APPROVED"),
        engine: MockEngine = MockEngine { jsonResponse(responseBody, responseStatus) },
    ): PaymentServerCallResult {
        val client = KtorPaymentServerClient.create(BASE_URL, engine)
        return try {
            runBlocking {
                client.submit(request(amountMinorUnits))
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
        const val PAYMENT_ID = " Mixed-Case Payment-ID "
        const val IDEMPOTENCY_KEY = " Mixed-Case Idempotency-Key "
    }
}
