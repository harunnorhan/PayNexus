package com.paynexus.merchant.ipc

import com.paynexus.payment.contract.PaymentRequestParcel
import com.paynexus.payment.contract.PaymentResultParcel
import com.paynexus.payment.contract.PaymentTransportValues
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome

internal object PaymentTransportMapper {
    data class Result(val paymentId: PaymentId, val idempotencyKey: IdempotencyKey, val outcome: PaymentOutcome)

    fun request(
        id: PaymentId,
        key: IdempotencyKey,
        amount: PaymentAmount,
    ): PaymentRequestParcel? {
        val valid = PaymentTransportValues.isValidIdentifier(id.value) &&
            PaymentTransportValues.isValidIdentifier(key.value)
        return if (valid) {
            PaymentRequestParcel(id.value, key.value, amount.money.minorUnits, amount.money.currency.name)
        } else {
            null
        }
    }

    fun rejection(code: Int): PaymentTransportFailure = if (code == PaymentTransportValues.INVALID_REQUEST) {
        PaymentTransportFailure.RequestRejected
    } else {
        PaymentTransportFailure.InvalidResult
    }

    fun result(parcel: PaymentResultParcel?): Result? {
        if (parcel == null || !PaymentTransportValues.isValidIdentifier(parcel.paymentId) ||
            !PaymentTransportValues.isValidIdentifier(parcel.idempotencyKey)
        ) {
            return null
        }
        val outcome = when (parcel.outcomeCode to parcel.reasonCode) {
            PaymentTransportValues.APPROVED to PaymentTransportValues.NONE -> PaymentOutcome.Approved

            PaymentTransportValues.DECLINED to PaymentTransportValues.UNSPECIFIED_DECLINE ->
                PaymentOutcome.Declined(DeclineReason.UNSPECIFIED)

            PaymentTransportValues.FAILED to PaymentTransportValues.PROCESSING_ERROR ->
                PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR)

            else -> null
        }
        return outcome?.let {
            Result(
                PaymentId(requireNotNull(parcel.paymentId)),
                IdempotencyKey(requireNotNull(parcel.idempotencyKey)),
                it,
            )
        }
    }
}
