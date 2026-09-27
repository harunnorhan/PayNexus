package com.paynexus.merchant.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.paynexus.payment.contract.IPaymentService

/** Activity-owned connection; lifecycle calls and platform callbacks run on the main thread. */
internal class PaymentServiceClient(context: Context) {
    private val applicationContext = context.applicationContext
    private val component =
        ComponentName(
            "com.paynexus.paymentservice",
            "com.paynexus.paymentservice.PaymentService",
        )
    private var state = ConnectionState.Disconnected
    private var service: IPaymentService? = null

    // Registration ownership is separate from whether a usable proxy has arrived.
    private var connection: ServiceConnection? = null

    fun bind() {
        if (state != ConnectionState.Disconnected) return

        val attempt = createConnection()
        connection = attempt
        state = ConnectionState.Binding
        val accepted =
            try {
                applicationContext.bindService(Intent().setComponent(component), attempt, Context.BIND_AUTO_CREATE)
            } catch (_: SecurityException) {
                cleanupFailedAttempt()
                return
            }
        if (!accepted) cleanupFailedAttempt()
    }

    fun unbind() {
        val registeredConnection = connection
        connection = null
        service = null
        state = ConnectionState.Disconnected
        if (registeredConnection != null) applicationContext.unbindService(registeredConnection)
    }

    private fun cleanupFailedAttempt() {
        // Android requires cleanup even after bindService returns false. A rejected
        // attempt may instead have no registration; tolerate only that cleanup case.
        try {
            unbind()
        } catch (failure: IllegalArgumentException) {
            if (failure.message?.startsWith("Service not registered:") != true) throw failure
        }
    }

    private fun createConnection(): ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (connection !== this) return
            service = IPaymentService.Stub.asInterface(binder)
            if (service == null) {
                unbind()
            } else {
                state = ConnectionState.Connected
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (connection === this) unbind()
        }

        override fun onBindingDied(name: ComponentName) {
            if (connection === this) unbind()
        }

        override fun onNullBinding(name: ComponentName) {
            if (connection === this) unbind()
        }
    }

    private enum class ConnectionState {
        Disconnected,
        Binding,
        Connected,
    }
}
