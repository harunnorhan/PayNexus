package com.paynexus.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MoneyTest {
    @Test
    fun `minor units and explicit currency are preserved exactly`() {
        listOf(Long.MIN_VALUE, -1L, 0L, 1L, 1250L, Long.MAX_VALUE).forEach { units ->
            val money = Money(units, CurrencyCode.TRY)
            assertEquals(units, money.minorUnits)
            assertEquals(CurrencyCode.TRY, money.currency)
        }
    }

    @Test
    fun `money has value equality`() {
        val money = Money(1250L, CurrencyCode.TRY)
        val equal = Money(1250L, CurrencyCode.TRY)
        assertEquals(money, equal)
        assertEquals(money.hashCode(), equal.hashCode())
        assertNotEquals(money, Money(1251L, CurrencyCode.TRY))
    }
}
