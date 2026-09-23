package com.paynexus.merchant.feature.amountentry

import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import kotlin.test.Test
import kotlin.test.assertEquals

class TryAmountFormatterTest {
    @Test
    fun `smallest amount has two fractional digits`() {
        val amount = PaymentAmount(Money(1L, CurrencyCode.TRY))
        assertEquals("0.01", TryAmountFormatter.format(amount))
    }

    @Test
    fun `one fractional digit is padded`() {
        val amount = PaymentAmount(Money(10L, CurrencyCode.TRY))
        assertEquals("0.10", TryAmountFormatter.format(amount))
    }

    @Test
    fun `whole amount retains two zeros`() {
        val amount = PaymentAmount(Money(1200L, CurrencyCode.TRY))
        assertEquals("12.00", TryAmountFormatter.format(amount))
    }

    @Test
    fun `two fractional digits remain exact`() {
        val amount = PaymentAmount(Money(1234L, CurrencyCode.TRY))
        assertEquals("12.34", TryAmountFormatter.format(amount))
    }

    @Test
    fun `maximum amount formats without overflow`() {
        val amount = PaymentAmount(Money(Long.MAX_VALUE, CurrencyCode.TRY))
        assertEquals("92233720368547758.07", TryAmountFormatter.format(amount))
    }
}
