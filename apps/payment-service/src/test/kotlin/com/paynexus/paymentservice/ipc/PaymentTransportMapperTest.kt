package com.paynexus.paymentservice.ipc

import com.paynexus.payment.contract.PaymentRequestParcel
import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaymentTransportMapperTest {
    @Test
    fun `mapping preserves exact identifiers currency and Long values`() {
        for (units in listOf(1L, 300L, Int.MAX_VALUE.toLong() + 1, Long.MAX_VALUE)) {
            val mapped = assertNotNull(PaymentTransportMapper.request(request().copy(minorUnits = units)))
            assertEquals(" Synthetic-ID ", mapped.paymentId.value)
            assertEquals(" Synthetic-Key ", mapped.idempotencyKey.value)
            assertEquals(units, mapped.amount.money.minorUnits)
            assertEquals(CurrencyCode.TRY, mapped.amount.money.currency)
        }
    }

    @Test
    fun `missing request or identifiers are rejected`() {
        assertNull(PaymentTransportMapper.request(null))
        for (value in listOf(null, "", " ", "\t\n")) {
            assertNull(PaymentTransportMapper.request(request().copy(paymentId = value)))
            assertNull(PaymentTransportMapper.request(request().copy(idempotencyKey = value)))
        }
    }

    @Test
    fun `identifier wire bound is enforced independently of domain validation`() {
        val maximum = "x".repeat(256)
        assertNotNull(PaymentTransportMapper.request(request().copy(paymentId = maximum, idempotencyKey = maximum)))
        assertNull(PaymentTransportMapper.request(request().copy(paymentId = maximum + "x")))
        assertNull(PaymentTransportMapper.request(request().copy(idempotencyKey = maximum + "x")))
    }

    @Test
    fun `nonpositive amounts are rejected by payment domain construction`() {
        for (units in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNull(PaymentTransportMapper.request(request().copy(minorUnits = units)))
        }
    }

    @Test
    fun `currency never normalizes or falls back`() {
        for (currency in listOf(null, "", "try", " TRY ", "USD", "EUR")) {
            assertNull(PaymentTransportMapper.request(request().copy(currencyCode = currency)))
        }
    }

    @Test
    fun `all outcomes use explicit wire codes and echo exact identifiers`() {
        val request = assertNotNull(PaymentTransportMapper.request(request()))
        val cases =
            listOf(
                Triple(PaymentOutcome.Approved, 1, 0),
                Triple(PaymentOutcome.Declined(DeclineReason.UNSPECIFIED), 2, 1),
                Triple(PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR), 3, 2),
            )
        for ((outcome, code, reason) in cases) {
            val mapped = PaymentTransportMapper.result(request, outcome)
            assertEquals(" Synthetic-ID ", mapped.paymentId)
            assertEquals(" Synthetic-Key ", mapped.idempotencyKey)
            assertEquals(code, mapped.outcomeCode)
            assertEquals(reason, mapped.reasonCode)
        }
    }

    private fun request() = PaymentRequestParcel(" Synthetic-ID ", " Synthetic-Key ", 300L, "TRY")
}
