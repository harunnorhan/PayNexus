package com.paynexus.payment.contract

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentIpcContractTest {
    @Test
    fun `current version is supported`() {
        assertTrue(PaymentIpcContract.supports(1))
    }

    @Test
    fun `version below minimum is rejected`() {
        assertFalse(PaymentIpcContract.supports(0))
    }

    @Test
    fun `unknown future version is rejected`() {
        assertFalse(PaymentIpcContract.supports(2))
    }

    @Test
    fun `negative version is rejected`() {
        assertFalse(PaymentIpcContract.supports(-1))
    }
}
