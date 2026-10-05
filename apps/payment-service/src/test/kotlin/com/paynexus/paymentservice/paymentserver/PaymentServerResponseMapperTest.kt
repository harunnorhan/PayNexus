package com.paynexus.paymentservice.paymentserver

import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentServerResponseMapperTest {
    @Test
    fun `valid response combinations map to domain outcomes`() {
        val cases =
            listOf(
                response(outcome = "APPROVED") to PaymentOutcome.Approved,
                response(outcome = "DECLINED", reason = "UNSPECIFIED") to
                    PaymentOutcome.Declined(DeclineReason.UNSPECIFIED),
                response(outcome = "FAILED", reason = "PROCESSING_ERROR") to
                    PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR),
            )

        for ((response, expected) in cases) {
            assertEquals(
                PaymentServerCallResult.Completed(expected),
                PaymentServerResponseMapper.map(expectation(), response),
            )
        }
    }

    @Test
    fun `unknown and contradictory response combinations are protocol failures`() {
        val invalidResponses =
            listOf(
                response(outcome = "UNKNOWN"),
                response(outcome = "APPROVED", reason = "UNSPECIFIED"),
                response(outcome = "DECLINED"),
                response(outcome = "DECLINED", reason = "PROCESSING_ERROR"),
                response(outcome = "FAILED"),
                response(outcome = "FAILED", reason = "UNSPECIFIED"),
            )

        for (response in invalidResponses) {
            assertEquals(
                PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.InvalidOutcome),
                PaymentServerResponseMapper.map(expectation(), response),
            )
        }
    }

    @Test
    fun `payment identifier mismatch is rejected before outcome acceptance`() {
        val response = response(paymentId = "different-payment", outcome = "APPROVED")

        assertEquals(
            PaymentServerCallResult.Unsuccessful(
                PaymentServerClientFailure.IdentifierMismatch(ResponseIdentifier.PAYMENT_ID),
            ),
            PaymentServerResponseMapper.map(expectation(), response),
        )
    }

    @Test
    fun `idempotency identifier mismatch is rejected before outcome acceptance`() {
        val response = response(idempotencyKey = "different-key", outcome = "APPROVED")

        assertEquals(
            PaymentServerCallResult.Unsuccessful(
                PaymentServerClientFailure.IdentifierMismatch(ResponseIdentifier.IDEMPOTENCY_KEY),
            ),
            PaymentServerResponseMapper.map(expectation(), response),
        )
    }

    private fun expectation() =
        PaymentServerResponseExpectation(
            paymentId = PaymentId(PAYMENT_ID),
            idempotencyKey = IdempotencyKey(IDEMPOTENCY_KEY),
        )

    private fun response(
        paymentId: String = PAYMENT_ID,
        idempotencyKey: String = IDEMPOTENCY_KEY,
        outcome: String,
        reason: String? = null,
    ) = PaymentServerResponseDto(paymentId, idempotencyKey, outcome, reason)

    private companion object {
        const val PAYMENT_ID = " Mixed-Case Payment-ID "
        const val IDEMPOTENCY_KEY = " Mixed-Case Idempotency-Key "
    }
}
