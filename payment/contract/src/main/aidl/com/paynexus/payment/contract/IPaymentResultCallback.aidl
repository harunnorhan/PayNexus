package com.paynexus.payment.contract;

import com.paynexus.payment.contract.PaymentResultParcel;

// One callback instance belongs to exactly one submission.
oneway interface IPaymentResultCallback {
    void onResult(in PaymentResultParcel result);
    void onRejected(int rejectionCode);
    // No confirmed business outcome is available; remote processing may or may not have occurred.
    void onTechnicalFailure(int failureCode);
}
