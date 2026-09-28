package com.paynexus.merchant.ipc

import com.paynexus.payment.contract.PaymentResultParcel
import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaymentTransportMapperTest {
    @Test
    fun `request preserves caller strings and all positive Long boundaries`() {
        for (units in listOf(1L, 300L, Int.MAX_VALUE.toLong() + 1, Long.MAX_VALUE)) {
            val result = assertNotNull(
                PaymentTransportMapper.request(
                    PaymentId(" Synthetic-ID "),
                    IdempotencyKey(" Synthetic-Key "),
                    amount(units),
                ),
            )
            assertEquals(" Synthetic-ID ", result.paymentId)
            assertEquals(" Synthetic-Key ", result.idempotencyKey)
            assertEquals(units, result.minorUnits)
            assertEquals("TRY", result.currencyCode)
        }
    }

    @Test
    fun `identifier bound accepts 256 code units and rejects 257 without truncation`() {
        val maximum = "x".repeat(256)
        assertNotNull(PaymentTransportMapper.request(PaymentId(maximum), IdempotencyKey(maximum), amount(1)))
        assertNull(PaymentTransportMapper.request(PaymentId(maximum + "x"), IdempotencyKey(maximum), amount(1)))
        assertNull(PaymentTransportMapper.request(PaymentId(maximum), IdempotencyKey(maximum + "x"), amount(1)))
        val unicodeMaximum = "😀".repeat(128)
        assertNotNull(PaymentTransportMapper.request(PaymentId(unicodeMaximum), IdempotencyKey(maximum), amount(1)))
        assertNull(PaymentTransportMapper.request(PaymentId(unicodeMaximum + "x"), IdempotencyKey(maximum), amount(1)))
    }

    @Test
    fun `approved declined and failed wire pairs map to distinct domain outcomes`() {
        val cases = listOf(
            Triple(1, 0, PaymentOutcome.Approved),
            Triple(2, 1, PaymentOutcome.Declined(DeclineReason.UNSPECIFIED)),
            Triple(3, 2, PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR)),
        )
        for ((code, reason, expected) in cases) {
            val mapped = assertNotNull(
                PaymentTransportMapper.result(PaymentResultParcel(" ID ", " Key ", code, reason)),
            )
            assertEquals(PaymentId(" ID "), mapped.paymentId)
            assertEquals(IdempotencyKey(" Key "), mapped.idempotencyKey)
            assertEquals(expected, mapped.outcome)
        }
    }

    @Test
    fun `unknown and contradictory wire pairs are rejected`() {
        val valid = setOf(1 to 0, 2 to 1, 3 to 2)
        for (code in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 3, 4, Int.MAX_VALUE)) {
            for (reason in listOf(-1, 0, 1, 2, 3, Int.MAX_VALUE)) {
                if (code to reason !in valid) {
                    assertNull(PaymentTransportMapper.result(PaymentResultParcel("id", "key", code, reason)))
                }
            }
        }
    }

    @Test
    fun `missing blank and oversized result identifiers are rejected`() {
        assertNull(PaymentTransportMapper.result(null))
        for (value in listOf(null, "", " ", "\t\n", "x".repeat(257))) {
            assertNull(PaymentTransportMapper.result(PaymentResultParcel(value, "key", 1, 0)))
            assertNull(PaymentTransportMapper.result(PaymentResultParcel("id", value, 1, 0)))
        }
    }

    @Test
    fun `remote rejection codes never become payment outcomes`() {
        assertEquals(PaymentTransportFailure.RequestRejected, PaymentTransportMapper.rejection(1))
        for (code in listOf(Int.MIN_VALUE, -1, 0, 2, Int.MAX_VALUE)) {
            assertEquals(PaymentTransportFailure.InvalidResult, PaymentTransportMapper.rejection(code))
        }
    }

    private fun amount(units: Long) = PaymentAmount(Money(units, CurrencyCode.TRY))
}
