package com.paynexus.server.application.payment

import com.paynexus.server.domain.payment.AcceptedPaymentRequest
import com.paynexus.server.domain.payment.PaymentCurrency
import com.paynexus.server.domain.payment.PaymentIntent

internal object PaymentRequestValidator {
    fun validate(
        paymentId: String?,
        idempotencyKey: String?,
        amountMinorUnits: Long?,
        currency: String?,
    ): AcceptedPaymentRequest? {
        val acceptedPaymentId = paymentId?.takeIf { it.isValidIdentifier() }
        val acceptedIdempotencyKey = validateIdempotencyKey(idempotencyKey)
        val acceptedAmount = amountMinorUnits?.takeIf { it > 0L }
        return when {
            acceptedPaymentId == null || acceptedIdempotencyKey == null -> {
                null
            }

            acceptedAmount == null || currency != PaymentCurrency.TRY.name -> {
                null
            }

            else -> {
                AcceptedPaymentRequest(
                    idempotencyKey = acceptedIdempotencyKey,
                    intent =
                        PaymentIntent(
                            paymentId = acceptedPaymentId,
                            amountMinorUnits = acceptedAmount,
                            currency = PaymentCurrency.TRY,
                        ),
                )
            }
        }
    }

    fun validateIdempotencyKey(idempotencyKey: String?): String? = idempotencyKey?.takeIf { it.isValidIdentifier() }

    private fun String.isValidIdentifier(): Boolean = isNotBlank() && length <= MAX_IDENTIFIER_LENGTH

    private const val MAX_IDENTIFIER_LENGTH = 256
}
