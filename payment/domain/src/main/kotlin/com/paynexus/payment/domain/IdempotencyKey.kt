package com.paynexus.payment.domain

data class IdempotencyKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Idempotency key must not be blank." }
    }
}
