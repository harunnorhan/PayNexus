package com.paynexus.paymentservice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import com.paynexus.payment.contract.IPaymentResultCallback
import com.paynexus.payment.contract.IPaymentService
import com.paynexus.payment.contract.PaymentIpcContract
import com.paynexus.payment.contract.PaymentRequestParcel
import com.paynexus.payment.contract.PaymentTransportValues
import com.paynexus.paymentservice.ipc.PaymentTransportMapper
import com.paynexus.paymentservice.ipc.SyntheticPaymentProcessor

class PaymentService : Service() {
    private val binder =
        object : IPaymentService.Stub() {
            override fun getContractVersion(): Int = PaymentIpcContract.CURRENT_VERSION

            override fun submitPayment(
                request: PaymentRequestParcel?,
                callback: IPaymentResultCallback?,
            ) {
                if (callback == null) return
                val validated = PaymentTransportMapper.request(request)
                try {
                    if (validated == null) {
                        callback.onRejected(PaymentTransportValues.INVALID_REQUEST)
                    } else {
                        val outcome = SyntheticPaymentProcessor.outcome(validated.amount)
                        callback.onResult(PaymentTransportMapper.result(validated, outcome))
                    }
                } catch (_: RemoteException) {
                    // One delivery attempt only; caller loss does not change the synthetic outcome.
                }
            }
        }

    override fun onBind(intent: Intent?): IBinder = binder
}
