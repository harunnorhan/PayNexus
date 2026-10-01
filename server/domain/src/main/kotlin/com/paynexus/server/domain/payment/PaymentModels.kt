package com.paynexus.server.domain.payment

enum class PaymentCurrency {
    TRY,
}

data class PaymentIntent(
    val paymentId: String,
    val amountMinorUnits: Long,
    val currency: PaymentCurrency,
) {
    init {
        require(paymentId.isValidIdentifier()) { "Payment ID is invalid." }
        require(amountMinorUnits > 0L) { "Payment amount must be positive." }
    }
}

data class AcceptedPaymentRequest(
    val idempotencyKey: String,
    val intent: PaymentIntent,
) {
    init {
        require(idempotencyKey.isValidIdentifier()) { "Idempotency key is invalid." }
    }
}

enum class PaymentOutcomeReason {
    UNSPECIFIED,
    PROCESSING_ERROR,
}

enum class PaymentOutcome(
    val reason: PaymentOutcomeReason?,
) {
    APPROVED(reason = null),
    DECLINED(reason = PaymentOutcomeReason.UNSPECIFIED),
    FAILED(reason = PaymentOutcomeReason.PROCESSING_ERROR),
}

data class StoredPaymentRecord(
    val request: AcceptedPaymentRequest,
    val outcome: PaymentOutcome,
)

private fun String.isValidIdentifier(): Boolean = isNotBlank() && length <= MAX_IDENTIFIER_LENGTH

private const val MAX_IDENTIFIER_LENGTH = 256
