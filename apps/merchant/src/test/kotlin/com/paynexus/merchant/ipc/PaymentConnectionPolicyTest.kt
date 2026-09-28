package com.paynexus.merchant.ipc

import com.paynexus.merchant.ipc.PaymentConnectionPolicy.Failure
import com.paynexus.merchant.ipc.PaymentConnectionPolicy.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentConnectionPolicyTest {
    private val policy = PaymentConnectionPolicy()

    @Test
    fun `initially disconnected without a connection demand`() {
        assertEquals(State.Disconnected, policy.state)
        assertFalse(policy.isCurrent(PaymentConnectionPolicy.Attempt()))
    }

    @Test
    fun `binder acquisition does not establish readiness`() {
        val attempt = assertNotNull(policy.start())
        assertEquals(State.Binding, policy.state)
        assertTrue(policy.connected(attempt))
        assertEquals(State.CheckingCompatibility, policy.state)
    }

    @Test
    fun `supported version establishes readiness only after acquisition`() {
        val attempt = assertNotNull(policy.start())
        assertFalse(policy.versionReceived(attempt, 2))
        assertEquals(State.Binding, policy.state)
        assertTrue(policy.connected(attempt))
        assertTrue(policy.versionReceived(attempt, 2))
        assertEquals(State.Ready, policy.state)
        assertTrue(policy.isCurrent(attempt))
    }

    @Test
    fun `unsupported versions remain distinct terminal outcomes`() {
        for (version in listOf(Int.MIN_VALUE, -1, 0, 1, 3, Int.MAX_VALUE)) {
            policy.stop()
            val attempt = checking()
            assertTrue(policy.versionReceived(attempt, version))
            assertEquals(State.Incompatible(version), policy.state)
            assertFalse(policy.isCurrent(attempt))
            assertNull(policy.lost(attempt))
            assertNull(policy.start())
            assertEquals(State.Incompatible(version), policy.state)
        }
    }

    @Test
    fun `repeated demand cannot create another attempt in any active phase`() {
        val attempt = assertNotNull(policy.start())
        assertNull(policy.start())
        policy.connected(attempt)
        assertNull(policy.start())
        policy.versionReceived(attempt, 2)
        assertNull(policy.start())
        assertTrue(policy.isCurrent(attempt))
        assertEquals(State.Ready, policy.state)
    }

    @Test
    fun `duplicate connection and version delivery cannot restart a handshake`() {
        val attempt = checking()
        assertFalse(policy.connected(attempt))
        policy.versionReceived(attempt, 2)
        assertFalse(policy.connected(attempt))
        assertFalse(policy.versionReceived(attempt, 3))
        assertEquals(State.Ready, policy.state)
    }

    @Test
    fun `bind failure permission denial and null binding do not recover`() {
        for (failure in listOf(Failure.BindFailed, Failure.PermissionDenied, Failure.NullBinding)) {
            policy.stop()
            val attempt = assertNotNull(policy.start())
            assertTrue(policy.failed(attempt, failure))
            assertEquals(State.Unavailable(failure), policy.state)
            assertFalse(policy.isCurrent(attempt))
            assertNull(policy.lost(attempt))
            assertNull(policy.start())
            assertEquals(State.Unavailable(failure), policy.state)
        }
    }

    @Test
    fun `generic query failure and worker rejection do not recover`() {
        for (failure in listOf(Failure.VersionQueryFailed, Failure.WorkerUnavailable, Failure.PermissionDenied)) {
            policy.stop()
            val attempt = checking()
            assertTrue(policy.failed(attempt, failure))
            assertEquals(State.Unavailable(failure), policy.state)
            assertFalse(policy.versionReceived(attempt, 2))
            assertNull(policy.lost(attempt))
            assertNull(policy.start())
        }
    }

    @Test
    fun `loss before or during handshake permits one fresh attempt`() {
        for (acquired in listOf(false, true)) {
            policy.stop()
            val attempt = assertNotNull(policy.start())
            if (acquired) policy.connected(attempt)
            val recovery = assertNotNull(policy.lost(attempt))
            assertEquals(State.Unavailable(Failure.ConnectionLost), policy.state)
            assertFalse(policy.isCurrent(attempt))
            val replacement = assertNotNull(policy.recover(recovery))
            assertNotSame(attempt, replacement)
            assertEquals(State.Binding, policy.state)
            assertNull(policy.lost(replacement))
            assertEquals(State.Unavailable(Failure.ConnectionLost), policy.state)
        }
    }

    @Test
    fun `ready connection loss permits one recovery without replenishing the allowance`() {
        val first = ready()
        val recovery = assertNotNull(policy.lost(first))
        val second = assertNotNull(policy.recover(recovery))
        assertTrue(policy.connected(second))
        assertTrue(policy.versionReceived(second, 2))
        assertEquals(State.Ready, policy.state)
        assertNull(policy.lost(second))
        assertEquals(State.Unavailable(Failure.ConnectionLost), policy.state)
        assertNull(policy.start())
        assertNull(policy.recover(recovery))
    }

    @Test
    fun `duplicate loss notifications cannot consume or schedule another recovery`() {
        val first = ready()
        val recovery = assertNotNull(policy.lost(first))
        assertNull(policy.lost(first))
        assertNull(policy.start())
        val second = assertNotNull(policy.recover(recovery))
        assertNull(policy.recover(recovery))
        assertNull(policy.lost(first))
        assertTrue(policy.isCurrent(second))
        assertEquals(State.Binding, policy.state)
    }

    @Test
    fun `stop before queued recovery prevents another bind`() {
        val recovery = assertNotNull(policy.lost(ready()))
        policy.stop()
        assertNull(policy.recover(recovery))
        assertEquals(State.Disconnected, policy.state)
    }

    @Test
    fun `old recovery cannot overwrite a new lifecycle session`() {
        val recovery = assertNotNull(policy.lost(ready()))
        policy.stop()
        val current = ready()
        assertNull(policy.recover(recovery))
        assertTrue(policy.isCurrent(current))
        assertEquals(State.Ready, policy.state)
        assertNotNull(policy.lost(current))
    }

    @Test
    fun `old successful and unsupported results cannot change a replacement`() {
        val old = checking()
        val recovery = assertNotNull(policy.lost(old))
        val current = assertNotNull(policy.recover(recovery))
        policy.connected(current)
        assertFalse(policy.versionReceived(old, 2))
        assertFalse(policy.versionReceived(old, 3))
        assertEquals(State.CheckingCompatibility, policy.state)
        assertTrue(policy.versionReceived(current, 2))
        assertEquals(State.Ready, policy.state)
    }

    @Test
    fun `old connection failure and loss callbacks cannot change a replacement`() {
        val old = checking()
        val current = assertNotNull(policy.recover(assertNotNull(policy.lost(old))))
        assertFalse(policy.connected(old))
        for (failure in Failure.entries) assertFalse(policy.failed(old, failure))
        assertNull(policy.lost(old))
        assertTrue(policy.isCurrent(current))
        assertEquals(State.Binding, policy.state)
    }

    @Test
    fun `stop during handshake discards all late outcomes`() {
        val old = checking()
        policy.stop()
        assertFalse(policy.versionReceived(old, 2))
        assertFalse(policy.versionReceived(old, 3))
        assertFalse(policy.failed(old, Failure.VersionQueryFailed))
        assertFalse(policy.connected(old))
        assertNull(policy.lost(old))
        assertEquals(State.Disconnected, policy.state)
    }

    @Test
    fun `repeated stop is harmless before binding and after readiness`() {
        repeat(2) { policy.stop() }
        val old = ready()
        repeat(2) { policy.stop() }
        assertEquals(State.Disconnected, policy.state)
        assertFalse(policy.isCurrent(old))
        assertNull(policy.lost(old))
    }

    @Test
    fun `new started interval resets an exhausted recovery allowance`() {
        val first = ready()
        val second = assertNotNull(policy.recover(assertNotNull(policy.lost(first))))
        assertNull(policy.lost(second))
        policy.stop()
        val next = ready()
        assertNotSame(first, next)
        assertNotNull(policy.recover(assertNotNull(policy.lost(next))))
    }

    @Test
    fun `new started interval can recheck an incompatible installation`() {
        val old = checking()
        policy.versionReceived(old, 3)
        assertNull(policy.start())
        policy.stop()
        ready()
        assertEquals(State.Ready, policy.state)
    }

    @Test
    fun `failed recovery remains terminal until lifecycle restart`() {
        for (failure in listOf(Failure.BindFailed, Failure.NullBinding, Failure.PermissionDenied)) {
            policy.stop()
            val replacement = assertNotNull(policy.recover(assertNotNull(policy.lost(ready()))))
            policy.failed(replacement, failure)
            assertNull(policy.start())
            assertNull(policy.lost(replacement))
            assertEquals(State.Unavailable(failure), policy.state)
        }
    }

    private fun checking(): PaymentConnectionPolicy.Attempt = assertNotNull(policy.start()).also {
        assertTrue(policy.connected(it))
    }

    private fun ready(): PaymentConnectionPolicy.Attempt = checking().also {
        assertTrue(policy.versionReceived(it, 2))
    }
}
