package com.paynexus.payment.contract

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentIpcContractTest {
    @Test
    fun `current version supports payment transport`() {
        assertTrue(PaymentIpcContract.supports(2))
    }

    @Test
    fun `old versions cannot establish payment readiness`() {
        for (version in listOf(0, 1)) assertFalse(PaymentIpcContract.supports(version))
    }

    @Test
    fun `unknown future versions are rejected`() {
        for (version in listOf(3, Int.MAX_VALUE)) assertFalse(PaymentIpcContract.supports(version))
    }

    @Test
    fun `negative versions are rejected`() {
        for (version in listOf(-1, Int.MIN_VALUE)) assertFalse(PaymentIpcContract.supports(version))
    }
}
