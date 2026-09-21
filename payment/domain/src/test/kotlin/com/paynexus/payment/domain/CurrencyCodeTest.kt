package com.paynexus.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CurrencyCodeTest {
    @Test
    fun `supported currency is parsed explicitly`() {
        assertEquals(CurrencyCode.TRY, CurrencyCode.fromCode("TRY"))
    }

    @Test
    fun `unsupported and noncanonical currency codes are rejected`() {
        listOf("USD", "", " ", "try", " TRY", "TRY ").forEach { code ->
            assertFailsWith<IllegalArgumentException> { CurrencyCode.fromCode(code) }
        }
    }
}
