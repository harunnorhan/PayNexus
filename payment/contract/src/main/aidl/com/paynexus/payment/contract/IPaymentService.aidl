package com.paynexus.payment.contract;

import com.paynexus.payment.contract.PaymentRequestParcel;
import com.paynexus.payment.contract.IPaymentResultCallback;

interface IPaymentService {
    int getContractVersion();
    // V3 only. Dispatch is not an acknowledgement of processing.
    oneway void submitPayment(in PaymentRequestParcel request, IPaymentResultCallback callback);
}
