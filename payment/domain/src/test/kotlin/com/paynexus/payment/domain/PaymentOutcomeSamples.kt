package com.paynexus.payment.domain

internal fun declinedPaymentOutcome(reason: DeclineReason = DeclineReason.UNSPECIFIED): PaymentOutcome.Declined =
    PaymentOutcome.Declined(reason)

internal fun failedPaymentOutcome(failure: PaymentFailure = PaymentFailure.PROCESSING_ERROR): PaymentOutcome.Failed =
    PaymentOutcome.Failed(failure)
