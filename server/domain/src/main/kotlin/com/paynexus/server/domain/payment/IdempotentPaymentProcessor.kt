package com.paynexus.server.domain.payment

class IdempotentPaymentProcessor(
    private val repository: PaymentRepository,
) {
    fun process(request: AcceptedPaymentRequest): PaymentProcessingResult {
        val candidate =
            StoredPaymentRecord(
                request = request,
                outcome = SyntheticPaymentPolicy.outcomeFor(request.intent.amountMinorUnits),
            )
        return when (val stored = repository.storeOrRead(candidate)) {
            is StoreOrReadResult.Created -> PaymentProcessingResult.Created(stored.record)
            is StoreOrReadResult.Existing -> stored.toProcessingResult(request.intent)
        }
    }

    private fun StoreOrReadResult.Existing.toProcessingResult(intent: PaymentIntent): PaymentProcessingResult =
        if (record.request.intent == intent) {
            PaymentProcessingResult.Replayed(record)
        } else {
            PaymentProcessingResult.Conflict
        }
}

sealed interface PaymentProcessingResult {
    data class Created(
        val record: StoredPaymentRecord,
    ) : PaymentProcessingResult

    data class Replayed(
        val record: StoredPaymentRecord,
    ) : PaymentProcessingResult

    data object Conflict : PaymentProcessingResult
}

internal object SyntheticPaymentPolicy {
    fun outcomeFor(amountMinorUnits: Long): PaymentOutcome =
        when (amountMinorUnits % OUTCOME_COUNT) {
            0L -> PaymentOutcome.APPROVED
            1L -> PaymentOutcome.DECLINED
            else -> PaymentOutcome.FAILED
        }

    private const val OUTCOME_COUNT = 3L
}
