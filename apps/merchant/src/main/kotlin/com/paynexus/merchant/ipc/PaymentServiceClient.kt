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
import com.paynexus.payment.contract.IPaymentService
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Activity-owned resources; only the version transaction runs on the worker. */
internal class PaymentServiceClient(context: Context) {
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
        { task -> Thread(task, "paynexus-contract-version") },
    )
    private var active: ConnectionAttempt? = null
    private var closed = false

    fun bind() {
        checkMainThread()
        if (closed) return
        policy.start()?.let(::bindAttempt)
    }

    fun unbind() {
        checkMainThread()
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
        if (!isCurrent(attempt) || attempt.binder != null || attempt.invalidated.get()) return
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
        if (!isCurrent(attempt) || attempt.invalidated.get()) return
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
        if (!isCurrent(attempt)) return
        val recovery = policy.lost(attempt.identity)
        release(attempt)
        if (recovery != null) main.post { policy.recover(recovery)?.let(::bindAttempt) }
    }

    private fun fail(attempt: ConnectionAttempt, reason: Failure, rejectedBind: Boolean = false) {
        if (policy.failed(attempt.identity, reason)) release(attempt, rejectedBind)
    }

    private fun release(attempt: ConnectionAttempt, rejectedBind: Boolean = false) {
        if (active !== attempt) return
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

    private fun isCurrent(owner: ConnectionAttempt): Boolean = active === owner && policy.isCurrent(owner.identity)

    private inner class ConnectionAttempt(val identity: PaymentConnectionPolicy.Attempt) : ServiceConnection {
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
