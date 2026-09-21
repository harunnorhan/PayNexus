package com.paynexus.payment.domain

sealed class PaymentState {
    data object Created : PaymentState()

    data object Processing : PaymentState()

    data class Finished(
        val outcome: PaymentOutcome,
    ) : PaymentState()

    fun transitionTo(next: PaymentState): PaymentState {
        val legal =
            when (this) {
                Created -> next == Processing
                Processing -> next is Finished
                is Finished -> false
            }
        if (!legal) {
            throw IllegalPaymentTransitionException(this, next)
        }
        return next
    }
}
