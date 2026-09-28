package com.paynexus.merchant.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import com.paynexus.merchant.ipc.PaymentConnectionPolicy.Failure
import com.paynexus.payment.contract.IPaymentResultCallback
import com.paynexus.payment.contract.IPaymentService
import com.paynexus.payment.contract.PaymentRequestParcel
import com.paynexus.payment.contract.PaymentResultParcel
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentId
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Activity-owned connection and payment resources; remote dispatch stays off main. */
internal class PaymentServiceClient(
    context: Context,
    onSubmissionTerminal: (PaymentSubmissionState) -> Unit = {},
) {
    private val applicationContext = context.applicationContext
    private val component = ComponentName(
        "com.paynexus.paymentservice",
        "com.paynexus.paymentservice.PaymentService",
    )
    private val main = Handler(Looper.getMainLooper())
    private val policy = PaymentConnectionPolicy()
    private val worker = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1),
        { task -> Thread(task, "paynexus-ipc") },
    )
    private val payments = PaymentRequests(PaymentRequestPolicy(policy), worker, main, onSubmissionTerminal)
    val submissionState: PaymentSubmissionState
        get() = payments.state

    private var active: ConnectionAttempt? = null
    private var closed = false

    fun submitPayment(id: PaymentId, key: IdempotencyKey, amount: PaymentAmount): PaymentSubmissionAdmission {
        checkMainThread()
        val attempt = active?.takeUnless { closed || it.invalidated.get() }
        val proxy = attempt?.service
        return if (attempt == null || proxy == null) {
            PaymentSubmissionAdmission.NotReady
        } else {
            payments.submit(attempt.identity, proxy, attempt.invalidated, id, key, amount) {
                attempt.dead.set(true)
                lost(attempt)
            }
        }
    }

    fun bind() {
        checkMainThread()
        if (closed) return
        policy.start()?.let(::bindAttempt)
    }

    fun unbind() {
        checkMainThread()
        payments.abandon()
        policy.stop()
        main.removeCallbacksAndMessages(null)
        active?.let(::release)
    }

    fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        try {
            unbind()
        } finally {
            worker.shutdownNow()
        }
    }

    private fun bindAttempt(identity: PaymentConnectionPolicy.Attempt) {
        val attempt = ConnectionAttempt(identity)
        active = attempt
        val accepted = try {
            applicationContext.bindService(Intent().setComponent(component), attempt, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            fail(attempt, Failure.PermissionDenied, rejectedBind = true)
            return
        }
        if (!accepted) fail(attempt, Failure.BindFailed, rejectedBind = true)
    }

    private fun connected(attempt: ConnectionAttempt, binder: IBinder) {
        if (!attempt.isCurrent || attempt.binder != null || attempt.invalidated.get()) return
        val proxy = IPaymentService.Stub.asInterface(binder)
        if (proxy == null) {
            fail(attempt, Failure.NullBinding)
        } else {
            attempt.binder = binder
            attempt.service = proxy
            try {
                binder.linkToDeath(attempt.recipient, 0)
                attempt.linked = true
            } catch (_: RemoteException) {
                attempt.dead.set(true)
                lost(attempt)
                return
            }
            if (policy.connected(attempt.identity)) queryVersion(attempt, proxy)
        }
    }

    private fun queryVersion(attempt: ConnectionAttempt, proxy: IPaymentService) {
        val task = FutureTask<Unit> {
            if (!attempt.invalidated.get()) {
                val result = readVersion(proxy)
                if (!attempt.invalidated.get()) main.post { versionReceived(attempt, result) }
            }
        }
        attempt.query = task
        try {
            worker.execute(task)
        } catch (_: RejectedExecutionException) {
            fail(attempt, Failure.WorkerUnavailable)
        }
    }

    private fun versionReceived(attempt: ConnectionAttempt, result: VersionResult) {
        if (!attempt.isCurrent || attempt.invalidated.get()) return
        when (result) {
            is VersionResult.Version -> {
                if (policy.versionReceived(attempt.identity, result.value) &&
                    policy.state is PaymentConnectionPolicy.State.Incompatible
                ) {
                    release(attempt)
                }
            }

            VersionResult.Dead -> {
                attempt.dead.set(true)
                lost(attempt)
            }

            is VersionResult.Failed -> fail(attempt, result.reason)
        }
    }

    private fun lost(attempt: ConnectionAttempt) {
        if (!attempt.isCurrent) return
        payments.connectionLost()
        val recovery = policy.lost(attempt.identity)
        release(attempt)
        if (recovery != null) main.post { policy.recover(recovery)?.let(::bindAttempt) }
    }

    private fun fail(attempt: ConnectionAttempt, reason: Failure, rejectedBind: Boolean = false) {
        if (policy.failed(attempt.identity, reason)) release(attempt, rejectedBind)
    }

    private fun release(attempt: ConnectionAttempt, rejectedBind: Boolean = false) {
        if (active !== attempt) return
        payments.connectionLost()
        active = null
        attempt.invalidated.set(true)
        val binder = attempt.binder
        attempt.binder = null
        attempt.service = null
        attempt.query?.let {
            it.cancel(true)
            worker.remove(it)
        }
        attempt.query = null
        try {
            if (attempt.linked && !attempt.dead.get()) binder?.unlinkToDeath(attempt.recipient, 0)
        } finally {
            attempt.linked = false
            attempt.unbindRegistration(rejectedBind)
        }
    }

    private inner class ConnectionAttempt(val identity: PaymentConnectionPolicy.Attempt) : ServiceConnection {
        val isCurrent: Boolean
            get() = active === this && policy.isCurrent(identity)
        val invalidated = AtomicBoolean(false)
        val dead = AtomicBoolean(false)
        var binder: IBinder? = null
        var service: IPaymentService? = null
        var linked = false
        var query: FutureTask<Unit>? = null
        val recipient = IBinder.DeathRecipient {
            dead.set(true)
            invalidated.set(true)
            main.post { lost(this) }
        }

        fun unbindRegistration(rejectedBind: Boolean) {
            try {
                applicationContext.unbindService(this)
            } catch (failure: IllegalArgumentException) {
                // A rejected bind still needs cleanup, but Android may have no registration.
                if (!rejectedBind || failure.message?.startsWith("Service not registered:") != true) throw failure
            }
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            connected(this, service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            lost(this)
        }

        override fun onBindingDied(name: ComponentName) {
            lost(this)
        }

        override fun onNullBinding(name: ComponentName) {
            fail(this, Failure.NullBinding)
        }
    }

    private sealed interface VersionResult {
        data class Version(val value: Int) : VersionResult
        data object Dead : VersionResult
        data class Failed(val reason: Failure) : VersionResult
    }

    private companion object {
        fun checkMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) { "Connection lifecycle must run on the main thread" }
        }

        fun readVersion(proxy: IPaymentService): VersionResult = try {
            VersionResult.Version(proxy.contractVersion)
        } catch (_: DeadObjectException) {
            VersionResult.Dead
        } catch (_: RemoteException) {
            VersionResult.Failed(Failure.VersionQueryFailed)
        } catch (_: SecurityException) {
            VersionResult.Failed(Failure.PermissionDenied)
        } catch (_: RuntimeException) {
            // Malformed/unsupported remote replies must not make a connection usable.
            VersionResult.Failed(Failure.VersionQueryFailed)
        }
    }
}

/** Android resources for one payment; connection recovery never calls submit. */
private class PaymentRequests(
    private val policy: PaymentRequestPolicy,
    private val worker: ThreadPoolExecutor,
    private val main: Handler,
    private val onSubmissionTerminal: (PaymentSubmissionState) -> Unit,
) {
    val state: PaymentSubmissionState
        get() = policy.state
    private var active: Resources? = null

    fun submit(
        attempt: PaymentConnectionPolicy.Attempt,
        proxy: IPaymentService,
        connectionInvalidated: AtomicBoolean,
        id: PaymentId,
        key: IdempotencyKey,
        amount: PaymentAmount,
        onDead: () -> Unit,
    ): PaymentSubmissionAdmission {
        val parcel = PaymentTransportMapper.request(id, key, amount) ?: return PaymentSubmissionAdmission.InvalidRequest
        val admission = policy.begin(attempt, id, key)
        if (admission == PaymentSubmissionAdmission.Accepted) {
            val token = checkNotNull(policy.active)
            val resources = Resources(token)
            active = resources
            resources.callback = ResultCallback { result ->
                main.post {
                    if (!connectionInvalidated.get() && policy.isCurrent(token)) {
                        when (result) {
                            is CallbackResult.Result -> {
                                val mapped = PaymentTransportMapper.result(result.parcel)
                                if (policy.result(token, mapped)) publishTerminal()
                            }

                            is CallbackResult.Rejected -> {
                                if (policy.fail(token, result.failure)) publishTerminal()
                            }
                        }
                        clear(resources)
                    }
                }
            }
            dispatch(resources, proxy, parcel, connectionInvalidated, onDead)
        }
        return admission
    }

    fun connectionLost() {
        active?.let {
            if (policy.fail(it.token, PaymentTransportFailure.ConnectionLost)) publishTerminal()
            clear(it)
        }
    }

    fun abandon() {
        if (policy.abandon()) publishTerminal()
        active?.let(::clear)
    }

    private fun dispatch(
        resources: Resources,
        proxy: IPaymentService,
        parcel: PaymentRequestParcel,
        connectionInvalidated: AtomicBoolean,
        onDead: () -> Unit,
    ) {
        val callback = checkNotNull(resources.callback)
        val task = FutureTask<Unit> {
            if (!resources.invalidated.get() && !connectionInvalidated.get()) {
                val failure = send(proxy, parcel, callback)
                if (failure == PaymentTransportFailure.ConnectionLost) connectionInvalidated.set(true)
                if (failure != null) {
                    main.post {
                        if (failure == PaymentTransportFailure.ConnectionLost) onDead()
                        if (policy.fail(resources.token, failure)) {
                            publishTerminal()
                            clear(resources)
                        }
                    }
                }
            }
        }
        resources.task = task
        try {
            worker.execute(task)
        } catch (_: RejectedExecutionException) {
            if (policy.fail(resources.token, PaymentTransportFailure.WorkerUnavailable)) publishTerminal()
            clear(resources)
        }
    }

    private fun publishTerminal() {
        onSubmissionTerminal(policy.state)
    }

    private fun clear(resources: Resources) {
        if (active !== resources) return
        active = null
        resources.invalidated.set(true)
        resources.callback?.detach()
        resources.callback = null
        resources.task?.let {
            it.cancel(true)
            worker.remove(it)
        }
        resources.task = null
    }

    private class Resources(val token: PaymentRequestPolicy.Token) {
        val invalidated = AtomicBoolean(false)
        var callback: ResultCallback? = null
        var task: FutureTask<Unit>? = null
    }

    private companion object {
        fun send(
            proxy: IPaymentService,
            request: PaymentRequestParcel,
            callback: IPaymentResultCallback,
        ): PaymentTransportFailure? = try {
            proxy.submitPayment(request, callback)
            null
        } catch (_: DeadObjectException) {
            PaymentTransportFailure.ConnectionLost
        } catch (_: RemoteException) {
            PaymentTransportFailure.DispatchFailed
        } catch (_: SecurityException) {
            PaymentTransportFailure.PermissionDenied
        } catch (_: RuntimeException) {
            // Unsupported/malformed transactions never manufacture a payment outcome.
            PaymentTransportFailure.DispatchFailed
        }
    }
}

private sealed interface CallbackResult {
    data class Result(val parcel: PaymentResultParcel?) : CallbackResult
    data class Rejected(val failure: PaymentTransportFailure) : CallbackResult
}

/** Detachment releases the client even if a remote peer retains this Binder. */
private class ResultCallback(receiver: (CallbackResult) -> Unit) : IPaymentResultCallback.Stub() {
    private val receiver = AtomicReference<((CallbackResult) -> Unit)?>(receiver)

    override fun onResult(result: PaymentResultParcel?) {
        receiver.getAndSet(null)?.invoke(CallbackResult.Result(result))
    }

    override fun onRejected(rejectionCode: Int) {
        val failure = PaymentTransportMapper.rejection(rejectionCode)
        receiver.getAndSet(null)?.invoke(CallbackResult.Rejected(failure))
    }

    fun detach() {
        receiver.set(null)
    }
}
