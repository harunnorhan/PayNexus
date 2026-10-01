package com.paynexus.paymentservice.ipc

import android.os.RemoteException
import com.paynexus.payment.contract.PaymentRequestParcel
import com.paynexus.payment.contract.PaymentResultParcel
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentOutcome
import com.paynexus.paymentservice.paymentserver.PaymentServerCallResult
import com.paynexus.paymentservice.paymentserver.PaymentServerClient
import com.paynexus.paymentservice.paymentserver.PaymentServerClientFailure
import com.paynexus.paymentservice.paymentserver.PaymentServerRequest
import com.paynexus.paymentservice.paymentserver.ResponseIdentifier
import kotlinx.coroutines.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentExecutionCoordinatorTest {
    @Test
    fun `valid request reaches one reused client with exact domain values`() {
        val client = RecordingClient { PaymentServerCallResult.Completed(PaymentOutcome.Approved) }
        val coordinator = PaymentExecutionCoordinator(client)
        try {
            val first = RecordingCallback()
            val second = RecordingCallback()

            coordinator.submit(request(), first.callback)
            first.await()
            coordinator.submit(request(amount = Long.MAX_VALUE), second.callback)
            second.await()

            assertEquals(2, client.requests.size)
            assertRequest(client.requests[0], 300L)
            assertRequest(client.requests[1], Long.MAX_VALUE)
            assertTrue(client.threadNames.all { it.startsWith("paynexus-payment-server") })
        } finally {
            coordinator.shutdown()
        }
        assertEquals(1, client.closeCount.get())
    }

    @Test
    fun `completed outcomes use the existing IPC result mapping`() {
        val outcomes =
            listOf(
                PaymentOutcome.Approved,
                PaymentOutcome.Declined(DeclineReason.UNSPECIFIED),
                PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR),
            )
        val expected =
            listOf(
                PaymentResultParcel(PAYMENT_ID, IDEMPOTENCY_KEY, 1, 0),
                PaymentResultParcel(PAYMENT_ID, IDEMPOTENCY_KEY, 2, 1),
                PaymentResultParcel(PAYMENT_ID, IDEMPOTENCY_KEY, 3, 2),
            )
        val next = AtomicInteger()
        val client = RecordingClient { PaymentServerCallResult.Completed(outcomes[next.getAndIncrement()]) }
        val coordinator = PaymentExecutionCoordinator(client)
        try {
            expected.forEach { parcel ->
                val callback = RecordingCallback()
                coordinator.submit(request(), callback.callback)
                callback.await()
                assertEquals(PaymentExecutionTerminal.Result(parcel), callback.singleTerminal())
            }
            assertEquals(3, client.requests.size)
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `invalid IPC input is rejected before worker execution`() {
        val client = RecordingClient { PaymentServerCallResult.Completed(PaymentOutcome.Approved) }
        val coordinator = PaymentExecutionCoordinator(client)
        try {
            val invalid =
                listOf(
                    null,
                    request().copy(paymentId = ""),
                    request().copy(idempotencyKey = " "),
                    request().copy(paymentId = "x".repeat(257)),
                    request().copy(minorUnits = 0L),
                    request().copy(currencyCode = "try"),
                )
            invalid.forEach { request ->
                val callback = RecordingCallback()
                coordinator.submit(request, callback.callback)
                assertEquals(PaymentExecutionTerminal.Rejected, callback.singleTerminal())
            }
            assertTrue(client.requests.isEmpty())
            assertTrue(client.threadNames.isEmpty())
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `every client failure becomes one generic technical terminal`() {
        val failures =
            listOf(
                PaymentServerClientFailure.InvalidRequest,
                PaymentServerClientFailure.UnexpectedHttpStatus(500),
                PaymentServerClientFailure.MalformedResponse,
                PaymentServerClientFailure.InvalidOutcome,
                PaymentServerClientFailure.IdentifierMismatch(ResponseIdentifier.PAYMENT_ID),
                PaymentServerClientFailure.Transport,
            )
        val next = AtomicInteger()
        val client = RecordingClient { PaymentServerCallResult.Unsuccessful(failures[next.getAndIncrement()]) }
        val coordinator = PaymentExecutionCoordinator(client)
        try {
            failures.forEach { _ ->
                val callback = RecordingCallback()
                coordinator.submit(request(), callback.callback)
                callback.await()
                assertEquals(PaymentExecutionTerminal.TechnicalFailure, callback.singleTerminal())
            }
            assertEquals(failures.size, client.requests.size)
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `one running and one queued request bound admission without caller runs`() {
        val client = BlockingClient()
        val coordinator = PaymentExecutionCoordinator(client)
        try {
            val first = RecordingCallback()
            val second = RecordingCallback()
            val rejected = RecordingCallback()

            coordinator.submit(request("payment-1"), first.callback)
            client.awaitStarted()
            coordinator.submit(request("payment-2"), second.callback)
            coordinator.submit(request("payment-3"), rejected.callback)

            assertEquals(PaymentExecutionTerminal.TechnicalFailure, rejected.singleTerminal())
            assertEquals(listOf("payment-1"), client.requests.map { it.paymentId.value })
            assertFalse(client.threadNames.contains(Thread.currentThread().name))

            client.release()
            first.await()
            second.await()
            assertEquals(listOf("payment-1", "payment-2"), client.requests.map { it.paymentId.value })
            assertEquals(1, rejected.terminals.size)
        } finally {
            client.release()
            coordinator.shutdown()
        }
    }

    @Test
    fun `result callback RemoteException is not retried and does not replay client work`() {
        val client = RecordingClient { PaymentServerCallResult.Completed(PaymentOutcome.Approved) }
        val coordinator = PaymentExecutionCoordinator(client)
        val callback = ThrowingCallback()
        try {
            coordinator.submit(request(), callback.callback)
            callback.await()
            assertEquals(1, callback.attempts.get())
            assertEquals(1, client.requests.size)
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `rejection callback RemoteException is not retried and never invokes client`() {
        val client = RecordingClient { PaymentServerCallResult.Completed(PaymentOutcome.Approved) }
        val coordinator = PaymentExecutionCoordinator(client)
        val callback = ThrowingCallback()
        try {
            coordinator.submit(request().copy(minorUnits = 0L), callback.callback)
            assertEquals(1, callback.attempts.get())
            assertTrue(client.requests.isEmpty())
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `technical callback RemoteException is not retried and does not replay client work`() {
        val client = RecordingClient { PaymentServerCallResult.Unsuccessful(PaymentServerClientFailure.Transport) }
        val coordinator = PaymentExecutionCoordinator(client)
        val callback = ThrowingCallback()
        try {
            coordinator.submit(request(), callback.callback)
            callback.await()
            assertEquals(1, callback.attempts.get())
            assertEquals(1, client.requests.size)
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `shutdown detaches active and queued callbacks and closes once`() {
        val client = BlockingClient(ignoreInterrupts = true)
        val coordinator = PaymentExecutionCoordinator(client)
        val active = RecordingCallback()
        val queued = RecordingCallback()
        coordinator.submit(request("payment-active"), active.callback)
        client.awaitStarted()
        coordinator.submit(request("payment-queued"), queued.callback)

        coordinator.shutdown()
        coordinator.shutdown()
        val afterShutdown = RecordingCallback()
        coordinator.submit(request("payment-rejected"), afterShutdown.callback)
        client.release()
        client.awaitFinished()

        assertTrue(active.terminals.isEmpty())
        assertTrue(queued.terminals.isEmpty())
        assertEquals(PaymentExecutionTerminal.TechnicalFailure, afterShutdown.singleTerminal())
        assertEquals(listOf("payment-active"), client.requests.map { it.paymentId.value })
        assertEquals(1, client.closeCount.get())
    }

    @Test
    fun `missing client fails closed without HTTP execution`() {
        val coordinator = PaymentExecutionCoordinator(null)
        val callback = RecordingCallback()
        try {
            coordinator.submit(request(), callback.callback)
            callback.await()
            assertEquals(PaymentExecutionTerminal.TechnicalFailure, callback.singleTerminal())
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `unexpected execution exception is sanitized as technical failure`() {
        val client = RecordingClient { error("synthetic client details") }
        val coordinator = PaymentExecutionCoordinator(client)
        val callback = RecordingCallback()
        try {
            coordinator.submit(request(), callback.callback)
            callback.await()
            assertEquals(PaymentExecutionTerminal.TechnicalFailure, callback.singleTerminal())
        } finally {
            coordinator.shutdown()
        }
    }

    @Test
    fun `client cancellation does not fabricate a terminal outcome`() {
        val cancelled = CountDownLatch(1)
        val client =
            RecordingClient {
                cancelled.countDown()
                throw CancellationException("synthetic cancellation")
            }
        val coordinator = PaymentExecutionCoordinator(client)
        val callback = RecordingCallback()
        try {
            coordinator.submit(request(), callback.callback)
            assertTrue(cancelled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(callback.terminals.isEmpty())
            assertEquals(1, client.requests.size)
        } finally {
            coordinator.shutdown()
        }
    }

    private fun assertRequest(
        request: PaymentServerRequest,
        amount: Long,
    ) {
        assertEquals(PAYMENT_ID, request.paymentId.value)
        assertEquals(IDEMPOTENCY_KEY, request.idempotencyKey.value)
        assertEquals(amount, request.amount.money.minorUnits)
        assertEquals("TRY", request.amount.money.currency.name)
    }

    private fun request(
        paymentId: String = PAYMENT_ID,
        amount: Long = 300L,
    ) = PaymentRequestParcel(paymentId, IDEMPOTENCY_KEY, amount, "TRY")

    private class RecordingClient(
        private val result: (PaymentServerRequest) -> PaymentServerCallResult,
    ) : PaymentServerClient {
        val requests = CopyOnWriteArrayList<PaymentServerRequest>()
        val threadNames = CopyOnWriteArrayList<String>()
        val closeCount = AtomicInteger()

        override suspend fun submit(request: PaymentServerRequest): PaymentServerCallResult {
            requests += request
            threadNames += Thread.currentThread().name
            return result(request)
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class BlockingClient(
        private val ignoreInterrupts: Boolean = false,
    ) : PaymentServerClient {
        val requests = CopyOnWriteArrayList<PaymentServerRequest>()
        val threadNames = CopyOnWriteArrayList<String>()
        val closeCount = AtomicInteger()
        private val started = CountDownLatch(1)
        private val release = CountDownLatch(1)
        private val finished = CountDownLatch(1)

        override suspend fun submit(request: PaymentServerRequest): PaymentServerCallResult {
            requests += request
            threadNames += Thread.currentThread().name
            if (requests.size == 1) {
                started.countDown()
                awaitRelease()
            }
            finished.countDown()
            return PaymentServerCallResult.Completed(PaymentOutcome.Approved)
        }

        override fun close() {
            closeCount.incrementAndGet()
        }

        fun awaitStarted() {
            assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        fun awaitFinished() {
            assertTrue(finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        fun release() {
            release.countDown()
        }

        private fun awaitRelease() {
            while (true) {
                try {
                    release.await()
                    return
                } catch (interrupted: InterruptedException) {
                    if (!ignoreInterrupts) throw interrupted
                }
            }
        }
    }

    private class RecordingCallback {
        val terminals = CopyOnWriteArrayList<PaymentExecutionTerminal>()
        private val delivered = CountDownLatch(1)
        val callback =
            PaymentTerminalCallback { terminal ->
                terminals += terminal
                delivered.countDown()
            }

        fun await() {
            assertTrue(delivered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        fun singleTerminal(): PaymentExecutionTerminal {
            assertEquals(1, terminals.size)
            return terminals.single()
        }
    }

    private class ThrowingCallback {
        val attempts = AtomicInteger()
        private val attempted = CountDownLatch(1)
        val callback =
            PaymentTerminalCallback {
                attempts.incrementAndGet()
                attempted.countDown()
                throw RemoteException("synthetic callback loss")
            }

        fun await() {
            assertTrue(attempted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    private companion object {
        const val PAYMENT_ID = " Mixed-Case Payment-ID "
        const val IDEMPOTENCY_KEY = " Mixed-Case Idempotency-Key "
        const val TIMEOUT_SECONDS = 5L
    }
}
