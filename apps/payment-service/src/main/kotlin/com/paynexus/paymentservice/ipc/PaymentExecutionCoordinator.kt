package com.paynexus.paymentservice.ipc

import android.os.RemoteException
import com.paynexus.payment.contract.PaymentRequestParcel
import com.paynexus.payment.contract.PaymentResultParcel
import com.paynexus.paymentservice.paymentserver.PaymentServerCallResult
import com.paynexus.paymentservice.paymentserver.PaymentServerClient
import com.paynexus.paymentservice.paymentserver.PaymentServerRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal sealed interface PaymentExecutionTerminal {
    data class Result(
        val parcel: PaymentResultParcel,
    ) : PaymentExecutionTerminal

    data object Rejected : PaymentExecutionTerminal

    data object TechnicalFailure : PaymentExecutionTerminal
}

internal fun interface PaymentTerminalCallback {
    @Throws(RemoteException::class)
    fun onTerminal(terminal: PaymentExecutionTerminal)
}

/** Service-owned bounded orchestration from validated IPC input to one terminal callback attempt. */
internal class PaymentExecutionCoordinator(
    private val client: PaymentServerClient?,
) {
    private val lifecycleLock = Any()
    private val worker =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            { task -> Thread(task, WORKER_NAME) },
            ThreadPoolExecutor.AbortPolicy(),
        )
    private val work = ConcurrentHashMap.newKeySet<PaymentWork>()
    private var accepting = true

    fun submit(
        request: PaymentRequestParcel?,
        callback: PaymentTerminalCallback,
    ) {
        val validated = PaymentTransportMapper.request(request)
        if (validated == null) {
            CallbackOwnership(callback).deliver(PaymentExecutionTerminal.Rejected)
            return
        }

        val paymentWork = PaymentWork(validated, CallbackOwnership(callback))
        val rejected =
            synchronized(lifecycleLock) {
                if (!accepting) {
                    true
                } else {
                    work += paymentWork
                    try {
                        worker.execute(paymentWork)
                        false
                    } catch (_: RejectedExecutionException) {
                        work -= paymentWork
                        true
                    }
                }
            }
        if (rejected) paymentWork.reject()
    }

    fun shutdown() {
        synchronized(lifecycleLock) {
            if (!accepting) return
            accepting = false
            work.forEach(PaymentWork::abandon)
            worker.shutdownNow()
            work.clear()
            client?.close()
        }
    }

    private inner class PaymentWork(
        private val request: PaymentTransportMapper.Request,
        private val callback: CallbackOwnership,
    ) : Runnable {
        override fun run() {
            try {
                if (!callback.isOwned) return
                val configuredClient = client
                if (configuredClient == null) {
                    callback.deliver(PaymentExecutionTerminal.TechnicalFailure)
                    return
                }
                val serverRequest =
                    PaymentServerRequest(
                        paymentId = request.paymentId,
                        idempotencyKey = request.idempotencyKey,
                        amount = request.amount,
                    )
                when (val result = runBlocking { configuredClient.submit(serverRequest) }) {
                    is PaymentServerCallResult.Completed -> {
                        callback.deliver(
                            PaymentExecutionTerminal.Result(
                                PaymentTransportMapper.result(request, result.outcome),
                            ),
                        )
                    }

                    is PaymentServerCallResult.Unsuccessful -> {
                        callback.deliver(PaymentExecutionTerminal.TechnicalFailure)
                    }
                }
            } catch (_: CancellationException) {
                // Cancellation is terminal locally and never manufactures a payment outcome.
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                callback.deliver(PaymentExecutionTerminal.TechnicalFailure)
            } finally {
                work -= this
            }
        }

        fun reject() {
            callback.deliver(PaymentExecutionTerminal.TechnicalFailure)
        }

        fun abandon() {
            callback.abandon()
        }
    }

    private class CallbackOwnership(
        callback: PaymentTerminalCallback,
    ) {
        private val callback = AtomicReference<PaymentTerminalCallback?>(callback)

        val isOwned: Boolean
            get() = callback.get() != null

        fun deliver(terminal: PaymentExecutionTerminal) {
            val owned = callback.getAndSet(null) ?: return
            try {
                owned.onTerminal(terminal)
            } catch (_: RemoteException) {
                // One terminal delivery attempt only; never retry or change category.
            }
        }

        fun abandon() {
            callback.set(null)
        }
    }

    private companion object {
        const val WORKER_NAME = "paynexus-payment-server"
    }
}
