package com.paynexus.paymentservice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.paynexus.payment.contract.IPaymentResultCallback
import com.paynexus.payment.contract.IPaymentService
import com.paynexus.payment.contract.PaymentIpcContract
import com.paynexus.payment.contract.PaymentRequestParcel
import com.paynexus.payment.contract.PaymentTransportValues
import com.paynexus.paymentservice.ipc.PaymentExecutionCoordinator
import com.paynexus.paymentservice.ipc.PaymentExecutionTerminal
import com.paynexus.paymentservice.ipc.PaymentTerminalCallback
import com.paynexus.paymentservice.paymentserver.KtorPaymentServerClient
import com.paynexus.paymentservice.paymentserver.PaymentServerClient
import io.ktor.http.Url

class PaymentService : Service() {
    private lateinit var coordinator: PaymentExecutionCoordinator
    private val binder =
        object : IPaymentService.Stub() {
            override fun getContractVersion(): Int = PaymentIpcContract.CURRENT_VERSION

            override fun submitPayment(
                request: PaymentRequestParcel?,
                callback: IPaymentResultCallback?,
            ) {
                if (callback == null) return
                coordinator.submit(
                    request,
                    PaymentTerminalCallback { terminal ->
                        when (terminal) {
                            is PaymentExecutionTerminal.Result -> {
                                callback.onResult(terminal.parcel)
                            }

                            PaymentExecutionTerminal.Rejected -> {
                                callback.onRejected(PaymentTransportValues.INVALID_REQUEST)
                            }

                            PaymentExecutionTerminal.TechnicalFailure -> {
                                callback.onTechnicalFailure(PaymentTransportValues.PAYMENT_OUTCOME_UNAVAILABLE)
                            }
                        }
                    },
                )
            }
        }

    override fun onCreate() {
        super.onCreate()
        coordinator = PaymentExecutionCoordinator(createPaymentServerClient())
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try {
            if (::coordinator.isInitialized) coordinator.shutdown()
        } finally {
            super.onDestroy()
        }
    }

    private fun createPaymentServerClient(): PaymentServerClient? {
        val endpoint = BuildConfig.PAYMENT_SERVER_BASE_URL
        if (endpoint.isBlank()) return null
        return try {
            KtorPaymentServerClient.create(Url(endpoint))
        } catch (_: Exception) {
            null
        }
    }
}
