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

internal fun interface PaymentServerClient : AutoCloseable {
    suspend fun submit(request: PaymentServerRequest): PaymentServerCallResult

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

internal sealed interface PaymentServerClientFailure {
    data object InvalidRequest : PaymentServerClientFailure

    data class UnexpectedHttpStatus(
        val statusCode: Int,
    ) : PaymentServerClientFailure

    data object MalformedResponse : PaymentServerClientFailure

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
