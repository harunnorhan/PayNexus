package com.paynexus.server.domain.payment

interface PaymentRepository {
    fun storeOrRead(candidate: StoredPaymentRecord): StoreOrReadResult

    fun findByIdempotencyKey(idempotencyKey: String): StoredPaymentRecord?
}

sealed interface StoreOrReadResult {
    data class Created(
        val record: StoredPaymentRecord,
    ) : StoreOrReadResult

    data class Existing(
        val record: StoredPaymentRecord,
    ) : StoreOrReadResult
}

class PaymentRepositoryException(
    cause: Throwable? = null,
) : RuntimeException("Payment repository operation failed.", cause)
