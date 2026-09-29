package com.paynexus.server.application.payment

internal data class AcceptedPaymentRequest(
    val paymentId: String,
    val idempotencyKey: String,
    val amountMinorUnits: Long,
)

internal sealed interface SyntheticPaymentOutcome {
    data object Approved : SyntheticPaymentOutcome

    data object Declined : SyntheticPaymentOutcome

    data object Failed : SyntheticPaymentOutcome
}

internal object PaymentRequestValidator {
    fun validate(
        paymentId: String?,
        idempotencyKey: String?,
        amountMinorUnits: Long?,
        currency: String?,
    ): AcceptedPaymentRequest? {
        val acceptedPaymentId = paymentId?.takeIf { it.isValidIdentifier() }
        val acceptedIdempotencyKey = idempotencyKey?.takeIf { it.isValidIdentifier() }
        val acceptedAmount = amountMinorUnits?.takeIf { it > 0L }
        return when {
            acceptedPaymentId == null || acceptedIdempotencyKey == null -> {
                null
            }

            acceptedAmount == null || currency != TRY_CURRENCY -> {
                null
            }

            else -> {
                AcceptedPaymentRequest(
                    paymentId = acceptedPaymentId,
                    idempotencyKey = acceptedIdempotencyKey,
                    amountMinorUnits = acceptedAmount,
                )
            }
        }
    }

    private fun String.isValidIdentifier(): Boolean = isNotBlank() && length <= MAX_IDENTIFIER_LENGTH

    private const val TRY_CURRENCY = "TRY"
    private const val MAX_IDENTIFIER_LENGTH = 256
}

/** Deterministic transport demonstration only; no bank or acquirer operation occurs. */
internal object SyntheticPaymentProcessor {
    fun process(request: AcceptedPaymentRequest): SyntheticPaymentOutcome =
        when (request.amountMinorUnits % OUTCOME_COUNT) {
            0L -> SyntheticPaymentOutcome.Approved
            1L -> SyntheticPaymentOutcome.Declined
            else -> SyntheticPaymentOutcome.Failed
        }

    private const val OUTCOME_COUNT = 3L
}
