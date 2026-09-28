package com.paynexus.paymentservice.ipc

import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class SyntheticPaymentProcessorTest {
    @Test
    fun `three consecutive synthetic amounts demonstrate all outcomes`() {
        assertEquals(PaymentOutcome.Approved, outcome(300))
        assertEquals(PaymentOutcome.Declined(DeclineReason.UNSPECIFIED), outcome(301))
        assertEquals(PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR), outcome(302))
    }

    @Test
    fun `large Long amounts are not narrowed or overflowed`() {
        assertEquals(PaymentOutcome.Approved, outcome(Long.MAX_VALUE - 1))
        assertEquals(PaymentOutcome.Declined(DeclineReason.UNSPECIFIED), outcome(Long.MAX_VALUE))
        assertEquals(PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR), outcome(Int.MAX_VALUE.toLong() + 1))
    }

    @Test
    fun `interleaved repeated requests do not depend on previous outcomes`() {
        repeat(3) {
            assertEquals(PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR), outcome(2))
            assertEquals(PaymentOutcome.Approved, outcome(3))
            assertEquals(PaymentOutcome.Declined(DeclineReason.UNSPECIFIED), outcome(1))
        }
    }

    private fun outcome(units: Long) = SyntheticPaymentProcessor.outcome(PaymentAmount(Money(units, CurrencyCode.TRY)))
}
