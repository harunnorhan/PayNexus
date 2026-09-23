package com.paynexus.paymentservice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.paynexus.payment.contract.IPaymentService
import com.paynexus.payment.contract.PaymentIpcContract

class PaymentService : Service() {
    private val binder =
        object : IPaymentService.Stub() {
            override fun getContractVersion(): Int = PaymentIpcContract.CURRENT_VERSION
        }

    override fun onBind(intent: Intent?): IBinder = binder
}
