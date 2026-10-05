package com.paynexus.paymentservice.paymentserver

import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentOutcome

internal object PaymentServerResponseMapper {
    fun map(
        expectation: PaymentServerResponseExpectation,
        response: PaymentServerResponseDto,
    ): PaymentServerCallResult =
        when {
            response.paymentId != expectation.paymentId.value -> failure(ResponseIdentifier.PAYMENT_ID)
            response.idempotencyKey != expectation.idempotencyKey.value -> failure(ResponseIdentifier.IDEMPOTENCY_KEY)
            else -> mapOutcome(response)
        }

    private fun mapOutcome(response: PaymentServerResponseDto): PaymentServerCallResult =
        when (response.outcome to response.reason) {
            APPROVED to null -> {
                PaymentServerCallResult.Completed(PaymentOutcome.Approved)
            }

            DECLINED to UNSPECIFIED -> {
                PaymentServerCallResult.Completed(PaymentOutcome.Declined(DeclineReason.UNSPECIFIED))
            }

            FAILED to PROCESSING_ERROR -> {
                PaymentServerCallResult.Completed(PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR))
            }

            else -> {
                PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.InvalidOutcome)
            }
        }

    private fun failure(identifier: ResponseIdentifier): PaymentServerCallResult =
        PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.IdentifierMismatch(identifier))

    private const val APPROVED = "APPROVED"
    private const val DECLINED = "DECLINED"
    private const val FAILED = "FAILED"
    private const val UNSPECIFIED = "UNSPECIFIED"
    private const val PROCESSING_ERROR = "PROCESSING_ERROR"
}
