package com.paynexus.paymentservice.ipc

import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentOutcome

/** Deterministic transport demonstration only; no bank or acquirer operation occurs. */
internal object SyntheticPaymentProcessor {
    fun outcome(amount: PaymentAmount): PaymentOutcome =
        when (amount.money.minorUnits % OUTCOME_COUNT) {
            0L -> PaymentOutcome.Approved
            1L -> PaymentOutcome.Declined(DeclineReason.UNSPECIFIED)
            else -> PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR)
        }

    private const val OUTCOME_COUNT = 3L
}
