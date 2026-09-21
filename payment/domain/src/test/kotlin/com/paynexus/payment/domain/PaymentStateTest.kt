package com.paynexus.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PaymentStateTest {
    private val approved = PaymentState.Finished(PaymentOutcome.Approved)
    private val declined = PaymentState.Finished(declinedPaymentOutcome())
    private val failed = PaymentState.Finished(failedPaymentOutcome())

    @Test
    fun `created can begin processing`() {
        assertEquals(PaymentState.Processing, PaymentState.Created.transitionTo(PaymentState.Processing))
    }

    @Test
    fun `processing can finish approved`() {
        assertEquals(approved, PaymentState.Processing.transitionTo(approved))
    }

    @Test
    fun `processing can finish declined`() {
        assertEquals(declined, PaymentState.Processing.transitionTo(declined))
    }

    @Test
    fun `processing can finish failed`() {
        assertEquals(failed, PaymentState.Processing.transitionTo(failed))
    }

    @Test
    fun `all illegal transitions are rejected with source and target`() {
        val states = listOf(PaymentState.Created, PaymentState.Processing, approved, declined, failed)
        val legalPairs =
            setOf(
                PaymentState.Created to PaymentState.Processing,
                PaymentState.Processing to approved,
                PaymentState.Processing to declined,
                PaymentState.Processing to failed,
            )
        var rejected = 0
        states.forEach { from ->
            states.forEach { to ->
                if ((from to to) !in legalPairs) {
                    val error =
                        assertFailsWith<IllegalPaymentTransitionException>("$from -> $to") {
                            from.transitionTo(to)
                        }
                    assertSame(from, error.from)
                    assertSame(to, error.to)
                    rejected++
                }
            }
        }
        assertEquals(21, rejected)
    }

    @Test
    fun `failed transitions preserve terminal outcomes`() {
        listOf(approved, declined, failed).forEach { original ->
            val before = original.copy()
            assertFailsWith<IllegalPaymentTransitionException> {
                original.transitionTo(PaymentState.Processing)
            }
            assertEquals(before, original)
        }
    }

    @Test
    fun `successful transition preserves its original state`() {
        val original: PaymentState = PaymentState.Processing
        original.transitionTo(approved)
        assertEquals(PaymentState.Processing, original)
    }
}
