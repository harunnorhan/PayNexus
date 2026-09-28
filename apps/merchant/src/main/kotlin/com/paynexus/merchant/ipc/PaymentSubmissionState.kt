package com.paynexus.merchant.ipc

import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome

/** Local transport observation; failures and abandonment do not establish a remote payment outcome. */
internal sealed interface PaymentSubmissionState {
    data object Idle : PaymentSubmissionState
    data class Pending(val paymentId: PaymentId, val idempotencyKey: IdempotencyKey) : PaymentSubmissionState
    data class Completed(
        val paymentId: PaymentId,
        val idempotencyKey: IdempotencyKey,
        val outcome: PaymentOutcome,
    ) : PaymentSubmissionState
    data class TransportFailed(val reason: PaymentTransportFailure) : PaymentSubmissionState
    data object Abandoned : PaymentSubmissionState
}

internal enum class PaymentTransportFailure {
    WorkerUnavailable,
    DispatchFailed,
    ConnectionLost,
    InvalidResult,
    RequestRejected,
    PermissionDenied,
}

internal enum class PaymentSubmissionAdmission {
    Accepted,
    NotReady,
    AlreadyActive,
    InvalidRequest,
}
