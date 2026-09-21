package com.paynexus.payment.domain

sealed interface PaymentOutcome {
    data object Approved : PaymentOutcome

    data class Declined(
        val reason: DeclineReason,
    ) : PaymentOutcome

    data class Failed(
        val failure: PaymentFailure,
    ) : PaymentOutcome
}
