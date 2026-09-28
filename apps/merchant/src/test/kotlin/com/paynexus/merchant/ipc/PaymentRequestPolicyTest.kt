package com.paynexus.merchant.ipc

import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentRequestPolicyTest {
    private val connection = PaymentConnectionPolicy()
    private val policy = PaymentRequestPolicy(connection)
    private val id = PaymentId("synthetic-id")
    private val key = IdempotencyKey("synthetic-key")

    @Test
    fun `only a ready current connection admits submission`() {
        assertEquals(PaymentSubmissionAdmission.NotReady, policy.begin(null, id, key))
        val attempt = assertNotNull(connection.start())
        assertEquals(PaymentSubmissionAdmission.NotReady, policy.begin(attempt, id, key))
        connection.connected(attempt)
        assertEquals(PaymentSubmissionAdmission.NotReady, policy.begin(attempt, id, key))
        connection.versionReceived(attempt, 2)
        assertEquals(PaymentSubmissionAdmission.Accepted, policy.begin(attempt, id, key))
        assertEquals(PaymentSubmissionState.Pending(id, key), policy.state)
    }

    @Test
    fun `incompatible service cannot admit payment`() {
        val attempt = assertNotNull(connection.start())
        connection.connected(attempt)
        connection.versionReceived(attempt, 1)
        assertEquals(PaymentSubmissionAdmission.NotReady, policy.begin(attempt, id, key))
        assertEquals(PaymentSubmissionState.Idle, policy.state)
    }

    @Test
    fun `second submission preserves original ownership`() {
        val token = begin()
        assertEquals(PaymentSubmissionAdmission.AlreadyActive, policy.begin(token.attempt, PaymentId("other"), key))
        assertTrue(policy.isCurrent(token))
        assertEquals(PaymentSubmissionState.Pending(id, key), policy.state)
    }

    @Test
    fun `callback can complete immediately after ownership is established`() {
        val token = begin()
        assertTrue(policy.result(token, result()))
        assertEquals(PaymentSubmissionState.Completed(id, key, PaymentOutcome.Approved), policy.state)
        assertNull(policy.active)
    }

    @Test
    fun `duplicate terminal callbacks and late dispatch failures cannot replace success`() {
        val token = begin()
        policy.result(token, result())
        val completed = policy.state
        assertFalse(policy.result(token, null))
        assertFalse(policy.fail(token, PaymentTransportFailure.DispatchFailed))
        assertEquals(completed, policy.state)
    }

    @Test
    fun `old callback cannot complete a new request with reused identifiers`() {
        val old = begin()
        policy.result(old, result())
        assertEquals(PaymentSubmissionAdmission.Accepted, policy.begin(old.attempt, id, key))
        val current = assertNotNull(policy.active)
        assertFalse(policy.result(old, result()))
        assertFalse(policy.fail(old, PaymentTransportFailure.DispatchFailed))
        assertTrue(policy.isCurrent(current))
        assertTrue(policy.result(current, result()))
    }

    @Test
    fun `mismatched identifiers and malformed results cause protocol failure`() {
        val cases = listOf(
            null,
            result().copy(paymentId = PaymentId("other")),
            result().copy(idempotencyKey = IdempotencyKey("other")),
        )
        val attempt = ready()
        for (result in cases) {
            policy.begin(attempt, id, key)
            assertTrue(policy.result(assertNotNull(policy.active), result))
            assertEquals(PaymentSubmissionState.TransportFailed(PaymentTransportFailure.InvalidResult), policy.state)
        }
    }

    @Test
    fun `transport failures remain distinct and reject later business results`() {
        val attempt = ready()
        for (failure in PaymentTransportFailure.entries) {
            policy.begin(attempt, id, key)
            val token = assertNotNull(policy.active)
            assertTrue(policy.fail(token, failure))
            assertFalse(policy.result(token, result()))
            assertEquals(PaymentSubmissionState.TransportFailed(failure), policy.state)
        }
    }

    @Test
    fun `stop abandons ownership and rejects callbacks after restart`() {
        val old = begin()
        policy.abandon()
        connection.stop()
        assertEquals(PaymentSubmissionState.Abandoned, policy.state)
        assertFalse(policy.result(old, result()))
        val current = begin()
        assertFalse(policy.result(old, result()))
        assertTrue(policy.isCurrent(current))
    }

    @Test
    fun `connection recovery never recreates a pending payment`() {
        val old = begin()
        policy.fail(old, PaymentTransportFailure.ConnectionLost)
        val recovery = assertNotNull(connection.lost(old.attempt))
        val replacement = assertNotNull(connection.recover(recovery))
        connection.connected(replacement)
        connection.versionReceived(replacement, 2)
        assertNull(policy.active)
        assertFalse(policy.result(old, result()))
        assertEquals(PaymentSubmissionState.TransportFailed(PaymentTransportFailure.ConnectionLost), policy.state)
        assertNull(connection.lost(replacement))
    }

    @Test
    fun `obsolete connection cannot complete even before request cleanup`() {
        val old = begin()
        connection.lost(old.attempt)
        assertFalse(policy.result(old, result()))
        assertTrue(policy.fail(old, PaymentTransportFailure.ConnectionLost))
    }

    @Test
    fun `repeated abandonment is harmless and does not replace completed outcomes`() {
        policy.abandon()
        assertEquals(PaymentSubmissionState.Idle, policy.state)
        val token = begin()
        policy.result(token, result())
        repeat(2) { policy.abandon() }
        assertEquals(PaymentSubmissionState.Completed(id, key, PaymentOutcome.Approved), policy.state)
    }

    private fun ready(): PaymentConnectionPolicy.Attempt = assertNotNull(connection.start()).also {
        connection.connected(it)
        connection.versionReceived(it, 2)
    }

    private fun begin(): PaymentRequestPolicy.Token {
        assertEquals(PaymentSubmissionAdmission.Accepted, policy.begin(ready(), id, key))
        return assertNotNull(policy.active)
    }

    private fun result() = PaymentTransportMapper.Result(id, key, PaymentOutcome.Approved)
}
