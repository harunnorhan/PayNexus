package com.paynexus.paymentservice.paymentserver

import kotlinx.serialization.Serializable

@Serializable
internal data class PaymentServerRequestDto(
    val paymentId: String,
    val idempotencyKey: String,
    val amountMinorUnits: Long,
    val currency: String,
)

@Serializable
internal data class PaymentServerResponseDto(
    val paymentId: String,
    val idempotencyKey: String,
    val outcome: String,
    val reason: String?,
)

@Serializable
internal data class PaymentServerErrorDto(
    val error: String,
)
