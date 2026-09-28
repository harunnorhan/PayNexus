package com.paynexus.merchant.ipc

import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentId

/** Main-thread-owned request decisions; no Binder objects or remote execution are represented here. */
internal class PaymentRequestPolicy(private val connection: PaymentConnectionPolicy) {
    class Token(val attempt: PaymentConnectionPolicy.Attempt, val id: PaymentId, val key: IdempotencyKey)

    var state: PaymentSubmissionState = PaymentSubmissionState.Idle
        private set
    var active: Token? = null
        private set

    fun begin(
        attempt: PaymentConnectionPolicy.Attempt?,
        id: PaymentId,
        key: IdempotencyKey,
    ): PaymentSubmissionAdmission {
        val ready = attempt != null && connection.isCurrent(attempt) &&
            connection.state == PaymentConnectionPolicy.State.Ready
        return when {
            active != null -> PaymentSubmissionAdmission.AlreadyActive

            !ready -> PaymentSubmissionAdmission.NotReady

            else -> {
                active = Token(checkNotNull(attempt), id, key)
                state = PaymentSubmissionState.Pending(id, key)
                PaymentSubmissionAdmission.Accepted
            }
        }
    }

    fun isCurrent(token: Token): Boolean = active === token && connection.isCurrent(token.attempt)

    fun result(token: Token, result: PaymentTransportMapper.Result?): Boolean {
        if (!isCurrent(token)) return false
        val valid = result != null && result.paymentId == token.id && result.idempotencyKey == token.key
        state = if (valid) {
            PaymentSubmissionState.Completed(token.id, token.key, requireNotNull(result).outcome)
        } else {
            PaymentSubmissionState.TransportFailed(PaymentTransportFailure.InvalidResult)
        }
        active = null
        return true
    }

    fun fail(token: Token, reason: PaymentTransportFailure): Boolean {
        if (active !== token) return false
        active = null
        state = PaymentSubmissionState.TransportFailed(reason)
        return true
    }

    fun abandon() {
        if (active != null) {
            active = null
            state = PaymentSubmissionState.Abandoned
        }
    }
}
