package com.paynexus.payment.domain

/** An exact quantity in minor units; payment positivity is enforced by [PaymentAmount]. */
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
)
