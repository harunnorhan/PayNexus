package com.paynexus.payment.domain

data class PaymentId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Payment ID must not be blank." }
    }
}
