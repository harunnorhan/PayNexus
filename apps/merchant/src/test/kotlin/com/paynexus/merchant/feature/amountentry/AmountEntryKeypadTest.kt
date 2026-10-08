package com.paynexus.merchant.feature.amountentry

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountEntryKeypadTest {
    @Test
    fun `digit appends to the canonical input string`() {
        assertEquals("127", appendAmountEntryKeypadInput("12", "7"))
    }

    @Test
    fun `double zero appends two exact zero characters`() {
        assertEquals("1200", appendAmountEntryKeypadInput("12", "00"))
    }

    @Test
    fun `backspace removes the final input character`() {
        assertEquals("12,3", backspaceAmountEntryKeypadInput("12,34"))
    }

    @Test
    fun `backspace on empty input remains empty`() {
        assertEquals("", backspaceAmountEntryKeypadInput(""))
    }

    @Test
    fun `append preserves invalid and unnormalized input exactly`() {
        assertEquals(" 12,.00", appendAmountEntryKeypadInput(" 12,.", "00"))
    }
}
