package com.paynexus.paymentservice.paymentserver

import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome

internal data class PaymentServerRequest(
    val paymentId: PaymentId,
    val idempotencyKey: IdempotencyKey,
    val amount: PaymentAmount,
)

internal data class PaymentServerResponseExpectation(
    val paymentId: PaymentId,
    val idempotencyKey: IdempotencyKey,
)

internal interface PaymentServerClient : AutoCloseable {
    suspend fun submit(request: PaymentServerRequest): PaymentServerCallResult

    suspend fun lookup(
        paymentId: PaymentId,
        idempotencyKey: IdempotencyKey,
    ): PaymentServerLookupResult

    override fun close() = Unit
}

internal sealed interface PaymentServerCallResult {
    data class Completed(
        val outcome: PaymentOutcome,
    ) : PaymentServerCallResult

    data class Unsuccessful(
        val failure: PaymentServerClientFailure,
    ) : PaymentServerCallResult
}

internal sealed interface PaymentServerLookupResult {
    data class Found(
        val outcome: PaymentOutcome,
    ) : PaymentServerLookupResult

    data object NotFound : PaymentServerLookupResult

    data class Unsuccessful(
        val failure: PaymentServerClientFailure,
    ) : PaymentServerLookupResult
}

internal sealed interface PaymentServerClientFailure {
    data object Timeout : PaymentServerClientFailure

    data object InvalidRequest : PaymentServerClientFailure

    data object IdempotencyConflict : PaymentServerClientFailure

    data class UnexpectedHttpStatus(
        val statusCode: Int,
    ) : PaymentServerClientFailure

    data class MalformedResponse(
        val statusCode: Int,
    ) : PaymentServerClientFailure

    data object InvalidOutcome : PaymentServerClientFailure

    data class IdentifierMismatch(
        val identifier: ResponseIdentifier,
    ) : PaymentServerClientFailure

    data object Transport : PaymentServerClientFailure
}

internal enum class ResponseIdentifier {
    PAYMENT_ID,
    IDEMPOTENCY_KEY,
}
