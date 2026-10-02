package com.paynexus.server.domain.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class IdempotentPaymentProcessorTest {
    @Test
    fun `new key creates the authoritative record`() {
        val repository = InMemoryPaymentRepository()
        val processor = IdempotentPaymentProcessor(repository)

        val result = processor.process(request())

        val created = assertIs<PaymentProcessingResult.Created>(result)
        assertEquals(PaymentOutcome.APPROVED, created.record.outcome)
        assertEquals(request(), created.record.request)
    }

    @Test
    fun `same key and intent replays the stored authoritative outcome`() {
        val accepted = request(amountMinorUnits = 300L)
        val stored = StoredPaymentRecord(accepted, PaymentOutcome.FAILED)
        val processor =
            IdempotentPaymentProcessor(
                object : PaymentRepository {
                    override fun storeOrRead(candidate: StoredPaymentRecord) = StoreOrReadResult.Existing(stored)

                    override fun findByIdempotencyKey(idempotencyKey: String): StoredPaymentRecord? = stored
                },
            )

        val result = processor.process(accepted)

        val replayed = assertIs<PaymentProcessingResult.Replayed>(result)
        assertSame(stored, replayed.record)
        assertEquals(PaymentOutcome.FAILED, replayed.record.outcome)
    }

    @Test
    fun `same key with a different payment ID conflicts`() {
        val repository = InMemoryPaymentRepository()
        val processor = IdempotentPaymentProcessor(repository)
        processor.process(request(paymentId = "original-payment"))

        val result = processor.process(request(paymentId = "different-payment"))

        assertIs<PaymentProcessingResult.Conflict>(result)
        assertEquals(
            "original-payment",
            repository
                .record(IDEMPOTENCY_KEY)
                ?.request
                ?.intent
                ?.paymentId,
        )
    }

    @Test
    fun `same key with a different amount conflicts without mutating the original`() {
        val repository = InMemoryPaymentRepository()
        val processor = IdempotentPaymentProcessor(repository)
        val original = assertIs<PaymentProcessingResult.Created>(processor.process(request(amountMinorUnits = 300L)))

        val result = processor.process(request(amountMinorUnits = 301L))

        assertIs<PaymentProcessingResult.Conflict>(result)
        assertEquals(original.record, repository.record(IDEMPOTENCY_KEY))
    }

    @Test
    fun `synthetic outcomes and reasons remain exact`() {
        val outcomes =
            listOf(
                300L to PaymentOutcome.APPROVED,
                301L to PaymentOutcome.DECLINED,
                302L to PaymentOutcome.FAILED,
                Long.MAX_VALUE to PaymentOutcome.DECLINED,
            )

        outcomes.forEach { (amount, expected) ->
            val result =
                IdempotentPaymentProcessor(InMemoryPaymentRepository())
                    .process(request(idempotencyKey = "key-$amount", amountMinorUnits = amount))

            assertEquals(expected, assertIs<PaymentProcessingResult.Created>(result).record.outcome)
        }
        assertEquals(null, PaymentOutcome.APPROVED.reason)
        assertEquals(PaymentOutcomeReason.UNSPECIFIED, PaymentOutcome.DECLINED.reason)
        assertEquals(PaymentOutcomeReason.PROCESSING_ERROR, PaymentOutcome.FAILED.reason)
    }

    @Test
    fun `repository failure propagates without becoming a payment outcome`() {
        val failure = PaymentRepositoryException()
        val processor =
            IdempotentPaymentProcessor(
                object : PaymentRepository {
                    override fun storeOrRead(candidate: StoredPaymentRecord): StoreOrReadResult = throw failure

                    override fun findByIdempotencyKey(idempotencyKey: String): StoredPaymentRecord? = throw failure
                },
            )

        val thrown = assertFailsWith<PaymentRepositoryException> { processor.process(request()) }

        assertSame(failure, thrown)
    }

    private fun request(
        paymentId: String = "payment-id",
        idempotencyKey: String = IDEMPOTENCY_KEY,
        amountMinorUnits: Long = 300L,
    ): AcceptedPaymentRequest =
        AcceptedPaymentRequest(
            idempotencyKey = idempotencyKey,
            intent =
                PaymentIntent(
                    paymentId = paymentId,
                    amountMinorUnits = amountMinorUnits,
                    currency = PaymentCurrency.TRY,
                ),
        )

    private class InMemoryPaymentRepository : PaymentRepository {
        private val records = mutableMapOf<String, StoredPaymentRecord>()

        override fun storeOrRead(candidate: StoredPaymentRecord): StoreOrReadResult {
            val key = candidate.request.idempotencyKey
            val existing = records[key]
            return if (existing == null) {
                records[key] = candidate
                StoreOrReadResult.Created(candidate)
            } else {
                StoreOrReadResult.Existing(existing)
            }
        }

        override fun findByIdempotencyKey(idempotencyKey: String): StoredPaymentRecord? = records[idempotencyKey]

        fun record(idempotencyKey: String): StoredPaymentRecord? = findByIdempotencyKey(idempotencyKey)
    }

    private companion object {
        const val IDEMPOTENCY_KEY = "idempotency-key"
    }
}
