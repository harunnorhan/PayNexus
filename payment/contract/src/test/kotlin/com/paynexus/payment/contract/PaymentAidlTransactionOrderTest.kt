package com.paynexus.payment.contract

import android.os.IBinder
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentAidlTransactionOrderTest {
    @Test
    fun `service transaction order remains unchanged`() {
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION,
            IPaymentService.Stub.TRANSACTION_getContractVersion,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 1,
            IPaymentService.Stub.TRANSACTION_submitPayment,
        )
    }

    @Test
    fun `technical failure is appended after existing callback transactions`() {
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION,
            IPaymentResultCallback.Stub.TRANSACTION_onResult,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 1,
            IPaymentResultCallback.Stub.TRANSACTION_onRejected,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 2,
            IPaymentResultCallback.Stub.TRANSACTION_onTechnicalFailure,
        )
    }
}
