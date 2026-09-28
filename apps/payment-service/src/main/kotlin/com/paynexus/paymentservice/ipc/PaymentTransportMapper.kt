package com.paynexus.paymentservice.ipc

import com.paynexus.payment.contract.PaymentRequestParcel
import com.paynexus.payment.contract.PaymentResultParcel
import com.paynexus.payment.contract.PaymentTransportValues
import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome

internal object PaymentTransportMapper {
    data class Request(
        val paymentId: PaymentId,
        val idempotencyKey: IdempotencyKey,
        val amount: PaymentAmount,
    )

    fun request(parcel: PaymentRequestParcel?): Request? =
        try {
            requireNotNull(parcel)
            require(PaymentTransportValues.isValidIdentifier(parcel.paymentId))
            require(PaymentTransportValues.isValidIdentifier(parcel.idempotencyKey))
            Request(
                PaymentId(requireNotNull(parcel.paymentId)),
                IdempotencyKey(requireNotNull(parcel.idempotencyKey)),
                PaymentAmount(Money(parcel.minorUnits, CurrencyCode.fromCode(requireNotNull(parcel.currencyCode)))),
            )
        } catch (_: IllegalArgumentException) {
            null
        }

    fun result(
        request: Request,
        outcome: PaymentOutcome,
    ): PaymentResultParcel {
        val (code, reason) =
            when (outcome) {
                PaymentOutcome.Approved -> {
                    PaymentTransportValues.APPROVED to PaymentTransportValues.NONE
                }

                is PaymentOutcome.Declined -> {
                    when (outcome.reason) {
                        DeclineReason.UNSPECIFIED -> {
                            PaymentTransportValues.DECLINED to PaymentTransportValues.UNSPECIFIED_DECLINE
                        }
                    }
                }

                is PaymentOutcome.Failed -> {
                    when (outcome.failure) {
                        PaymentFailure.PROCESSING_ERROR -> {
                            PaymentTransportValues.FAILED to PaymentTransportValues.PROCESSING_ERROR
                        }
                    }
                }
            }
        return PaymentResultParcel(request.paymentId.value, request.idempotencyKey.value, code, reason)
    }
}
