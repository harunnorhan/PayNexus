package com.paynexus.payment.domain

class IllegalPaymentTransitionException(
    val from: PaymentState,
    val to: PaymentState,
) : IllegalStateException("Illegal payment state transition.")
