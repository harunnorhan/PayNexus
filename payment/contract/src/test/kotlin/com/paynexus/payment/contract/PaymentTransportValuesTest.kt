package com.paynexus.payment.contract

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentTransportValuesTest {
    @Test
    fun `existing wire values remain stable in V3`() {
        assertEquals(256, PaymentTransportValues.MAX_IDENTIFIER_LENGTH)
        assertEquals(1, PaymentTransportValues.APPROVED)
        assertEquals(2, PaymentTransportValues.DECLINED)
        assertEquals(3, PaymentTransportValues.FAILED)
        assertEquals(0, PaymentTransportValues.NONE)
        assertEquals(1, PaymentTransportValues.UNSPECIFIED_DECLINE)
        assertEquals(2, PaymentTransportValues.PROCESSING_ERROR)
        assertEquals(1, PaymentTransportValues.INVALID_REQUEST)
    }

    @Test
    fun `technical failure uses an explicit stable wire value`() {
        assertEquals(1, PaymentTransportValues.PAYMENT_OUTCOME_UNAVAILABLE)
    }
}
