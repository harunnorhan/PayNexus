package com.paynexus.server.domain.payment

fun interface PaymentRepository {
    fun storeOrRead(candidate: StoredPaymentRecord): StoreOrReadResult
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
