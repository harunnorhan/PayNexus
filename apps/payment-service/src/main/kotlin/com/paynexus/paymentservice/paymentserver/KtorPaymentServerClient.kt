package com.paynexus.paymentservice.paymentserver

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
            try {
                httpClient.post(paymentEndpoint) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(request.toDto())
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return failed(PaymentServerClientFailure.Transport)
            }

        return when (response.status) {
            HttpStatusCode.OK -> decodeSuccess(response, request)
            HttpStatusCode.BadRequest -> decodeInvalidRequest(response)
            else -> failed(PaymentServerClientFailure.UnexpectedHttpStatus(response.status.value))
        }
    }

    override fun close() {
        httpClient.close()
    }

    private suspend fun decodeSuccess(
        response: HttpResponse,
        request: PaymentServerRequest,
    ): PaymentServerCallResult =
        decode(response) { body: PaymentServerResponseDto ->
            PaymentServerResponseMapper.map(request, body)
        }

    private suspend fun decodeInvalidRequest(response: HttpResponse): PaymentServerCallResult =
        decode(response) { body: PaymentServerErrorDto ->
            if (body.error == INVALID_REQUEST) {
                failed(PaymentServerClientFailure.InvalidRequest)
            } else {
                failed(PaymentServerClientFailure.MalformedResponse)
            }
        }

    private suspend inline fun <reified T> decode(
        response: HttpResponse,
        map: (T) -> PaymentServerCallResult,
    ): PaymentServerCallResult =
        try {
            map(response.body())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            failed(PaymentServerClientFailure.Transport)
        } catch (_: SerializationException) {
            failed(PaymentServerClientFailure.MalformedResponse)
        } catch (_: Exception) {
            failed(PaymentServerClientFailure.MalformedResponse)
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

private fun failed(failure: PaymentServerClientFailure) = PaymentServerCallResult.Unsuccessful(failure)

private const val PAYMENT_PATH = "/v1/payments"
private const val INVALID_REQUEST = "INVALID_REQUEST"
private const val TRY = "TRY"
