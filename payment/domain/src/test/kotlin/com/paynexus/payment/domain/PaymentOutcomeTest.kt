package com.paynexus.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class PaymentOutcomeTest {
    @Test
    fun `approval is explicit`() {
        val outcome: PaymentOutcome = PaymentOutcome.Approved
        assertIs<PaymentOutcome.Approved>(outcome)
    }

    @Test
    fun `decline retains business reason`() {
        val outcome: PaymentOutcome = PaymentOutcome.Declined(DeclineReason.UNSPECIFIED)
        val declined = assertIs<PaymentOutcome.Declined>(outcome)
        assertEquals(DeclineReason.UNSPECIFIED, declined.reason)
        assertEquals(PaymentOutcome.Declined(DeclineReason.UNSPECIFIED), declined)
    }

    @Test
    fun `failure retains technical classification`() {
        val outcome: PaymentOutcome = PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR)
        val failed = assertIs<PaymentOutcome.Failed>(outcome)
        assertEquals(PaymentFailure.PROCESSING_ERROR, failed.failure)
        assertEquals(PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR), failed)
    }

    @Test
    fun `business decline and technical failure remain distinct`() {
        val declined: PaymentOutcome = PaymentOutcome.Declined(DeclineReason.UNSPECIFIED)
        val failed: PaymentOutcome = PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR)
        assertNotEquals(declined, failed)
        assertNotEquals(PaymentOutcome.Approved, declined)
        assertNotEquals(PaymentOutcome.Approved, failed)
    }
}
