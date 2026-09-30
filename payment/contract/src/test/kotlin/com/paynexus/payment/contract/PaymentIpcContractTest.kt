package com.paynexus.payment.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentIpcContractTest {
    @Test
    fun `current and minimum versions are strict V3`() {
        assertEquals(3, PaymentIpcContract.CURRENT_VERSION)
        assertEquals(3, PaymentIpcContract.MIN_SUPPORTED_VERSION)
        assertTrue(PaymentIpcContract.supports(3))
    }

    @Test
    fun `old versions cannot establish payment readiness`() {
        for (version in listOf(0, 1, 2)) assertFalse(PaymentIpcContract.supports(version))
    }

    @Test
    fun `unknown future versions are rejected`() {
        for (version in listOf(4, Int.MAX_VALUE)) assertFalse(PaymentIpcContract.supports(version))
    }

    @Test
    fun `negative versions are rejected`() {
        for (version in listOf(-1, Int.MIN_VALUE)) assertFalse(PaymentIpcContract.supports(version))
    }
}
