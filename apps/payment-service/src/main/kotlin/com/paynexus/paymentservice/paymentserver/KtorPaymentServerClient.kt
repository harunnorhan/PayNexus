package com.paynexus.paymentservice.paymentserver

import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentId
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/** Owns one configured [HttpClient] and releases it from [close]. */
internal class KtorPaymentServerClient private constructor(
    baseUrl: Url,
    private val httpClient: HttpClient,
) : PaymentServerClient,
    AutoCloseable {
    private val paymentEndpoint = Url(baseUrl.toString().trimEnd('/') + PAYMENT_PATH)

    override suspend fun submit(request: PaymentServerRequest): PaymentServerCallResult {
        val response =
            requestResponse {
                httpClient.post(paymentEndpoint) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(request.toDto())
                }
            } ?: return failed(PaymentServerClientFailure.Transport)

        return when (response.status) {
            HttpStatusCode.OK -> {
                decodeSuccess(response, request.expectation())
            }

            HttpStatusCode.BadRequest -> {
                decodeSubmitError(response, INVALID_REQUEST) {
                    PaymentServerClientFailure.InvalidRequest
                }
            }

            HttpStatusCode.Conflict -> {
                decodeSubmitError(response, IDEMPOTENCY_CONFLICT) {
                    PaymentServerClientFailure.IdempotencyConflict
                }
            }

            else -> {
                failed(PaymentServerClientFailure.UnexpectedHttpStatus(response.status.value))
            }
        }
    }

    override suspend fun lookup(
        paymentId: PaymentId,
        idempotencyKey: IdempotencyKey,
    ): PaymentServerLookupResult {
        val response =
            requestResponse {
                httpClient.get(paymentEndpoint) {
                    header(PAYNEXUS_IDEMPOTENCY_KEY_HEADER, idempotencyKey.value)
                }
            } ?: return lookupFailed(PaymentServerClientFailure.Transport)

        return when (response.status) {
            HttpStatusCode.OK -> {
                decodeLookupSuccess(
                    response,
                    PaymentServerResponseExpectation(paymentId, idempotencyKey),
                )
            }

            HttpStatusCode.NotFound -> {
                decodeLookupNotFound(response)
            }

            HttpStatusCode.BadRequest -> {
                lookupFailed(
                    requireNotNull(
                        decodeError(response, INVALID_REQUEST) { PaymentServerClientFailure.InvalidRequest },
                    ),
                )
            }

            else -> {
                lookupFailed(PaymentServerClientFailure.UnexpectedHttpStatus(response.status.value))
            }
        }
    }

    override fun close() {
        httpClient.close()
    }

    private suspend fun decodeSuccess(
        response: HttpResponse,
        expectation: PaymentServerResponseExpectation,
    ): PaymentServerCallResult =
        decodeSuccessBody(response) { body ->
            PaymentServerResponseMapper.map(expectation, body)
        }

    private suspend fun decodeLookupSuccess(
        response: HttpResponse,
        expectation: PaymentServerResponseExpectation,
    ): PaymentServerLookupResult =
        when (val result = decodeSuccess(response, expectation)) {
            is PaymentServerCallResult.Completed -> PaymentServerLookupResult.Found(result.outcome)
            is PaymentServerCallResult.Unsuccessful -> lookupFailed(result.failure)
        }

    private suspend fun decodeLookupNotFound(response: HttpResponse): PaymentServerLookupResult {
        val failure = decodeError(response, PAYMENT_NOT_FOUND) { null }
        return if (failure == null) PaymentServerLookupResult.NotFound else lookupFailed(failure)
    }

    private suspend fun decodeSubmitError(
        response: HttpResponse,
        expectedError: String,
        exactFailure: () -> PaymentServerClientFailure,
    ): PaymentServerCallResult = failed(requireNotNull(decodeError(response, expectedError, exactFailure)))

    private suspend fun decodeError(
        response: HttpResponse,
        expectedError: String,
        exactFailure: () -> PaymentServerClientFailure?,
    ): PaymentServerClientFailure? =
        try {
            val body = response.body<PaymentServerErrorDto>()
            if (body.error == expectedError) {
                exactFailure()
            } else {
                PaymentServerClientFailure.MalformedResponse(response.status.value)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            PaymentServerClientFailure.MalformedResponse(response.status.value)
        }

    private suspend fun decodeSuccessBody(
        response: HttpResponse,
        map: (PaymentServerResponseDto) -> PaymentServerCallResult,
    ): PaymentServerCallResult =
        try {
            map(response.body())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            failed(PaymentServerClientFailure.Transport)
        } catch (_: SerializationException) {
            failed(PaymentServerClientFailure.MalformedResponse(response.status.value))
        } catch (_: Exception) {
            failed(PaymentServerClientFailure.MalformedResponse(response.status.value))
        }

    private suspend fun requestResponse(request: suspend () -> HttpResponse): HttpResponse? =
        try {
            request()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }

    internal companion object {
        fun create(baseUrl: Url): KtorPaymentServerClient =
            KtorPaymentServerClient(
                baseUrl = baseUrl,
                httpClient = HttpClient(Android) { configurePaymentServerClient() },
            )

        /** Transfers ownership of [ownedEngine] to the returned client. */
        fun create(
            baseUrl: Url,
            ownedEngine: HttpClientEngine,
        ): KtorPaymentServerClient =
            KtorPaymentServerClient(
                baseUrl = baseUrl,
                httpClient = HttpClient(ownedEngine) { configurePaymentServerClient() },
            )
    }
}

private fun HttpClientConfig<*>.configurePaymentServerClient() {
    expectSuccess = false
    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                explicitNulls = true
                ignoreUnknownKeys = false
                isLenient = false
                coerceInputValues = false
            },
        )
    }
}

private fun PaymentServerRequest.toDto(): PaymentServerRequestDto =
    PaymentServerRequestDto(
        paymentId = paymentId.value,
        idempotencyKey = idempotencyKey.value,
        amountMinorUnits = amount.money.minorUnits,
        currency = TRY,
    )

private fun PaymentServerRequest.expectation(): PaymentServerResponseExpectation =
    PaymentServerResponseExpectation(paymentId, idempotencyKey)

private fun failed(failure: PaymentServerClientFailure) = PaymentServerCallResult.Unsuccessful(failure)

private fun lookupFailed(failure: PaymentServerClientFailure) = PaymentServerLookupResult.Unsuccessful(failure)

private const val PAYMENT_PATH = "/v1/payments"
private const val INVALID_REQUEST = "INVALID_REQUEST"
private const val IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
private const val PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND"
private const val PAYNEXUS_IDEMPOTENCY_KEY_HEADER = "PayNexus-Idempotency-Key"
private const val TRY = "TRY"
