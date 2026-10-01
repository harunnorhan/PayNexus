package com.paynexus.server.application

import com.paynexus.server.domain.payment.IdempotentPaymentProcessor
import com.paynexus.server.domain.payment.PaymentRepository
import com.paynexus.server.domain.payment.StoreOrReadResult
import com.paynexus.server.domain.payment.StoredPaymentRecord
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class TestPaymentRepository : PaymentRepository {
    private val records = ConcurrentHashMap<String, StoredPaymentRecord>()
    val storeCalls = AtomicInteger()

    override fun storeOrRead(candidate: StoredPaymentRecord): StoreOrReadResult {
        storeCalls.incrementAndGet()
        val existing = records.putIfAbsent(candidate.request.idempotencyKey, candidate)
        return if (existing == null) {
            StoreOrReadResult.Created(candidate)
        } else {
            StoreOrReadResult.Existing(existing)
        }
    }
}

internal fun testPaymentProcessor(repository: PaymentRepository = TestPaymentRepository()): IdempotentPaymentProcessor =
    IdempotentPaymentProcessor(repository)
