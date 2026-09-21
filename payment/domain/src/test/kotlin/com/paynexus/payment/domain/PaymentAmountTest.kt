package com.paynexus.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentAmountTest {
    @Test
    fun `positive payment amounts preserve money`() {
        listOf(1L, 1250L, Long.MAX_VALUE).forEach { units ->
            val money = Money(units, CurrencyCode.TRY)
            assertEquals(money, PaymentAmount(money).money)
        }
    }

    @Test
    fun `zero and negative payment amounts are rejected`() {
        listOf(0L, -1L, Long.MIN_VALUE).forEach { units ->
            assertFailsWith<IllegalArgumentException> {
                PaymentAmount(Money(units, CurrencyCode.TRY))
            }
        }
    }

    @Test
    fun `copy cannot bypass payment amount validation`() {
        val original = PaymentAmount(Money(1L, CurrencyCode.TRY))
        listOf(0L, -1L, Long.MIN_VALUE).forEach { units ->
            assertFailsWith<IllegalArgumentException> {
                original.copy(money = Money(units, CurrencyCode.TRY))
            }
        }
        assertEquals(1L, original.money.minorUnits)
    }
}
