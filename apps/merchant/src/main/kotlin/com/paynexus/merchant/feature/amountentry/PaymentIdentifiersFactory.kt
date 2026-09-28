package com.paynexus.merchant.feature.amountentry

import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentId
import java.util.UUID

internal data class PaymentIdentifiers(
    val paymentId: PaymentId,
    val idempotencyKey: IdempotencyKey,
)

internal fun interface PaymentIdentifiersFactory {
    fun create(): PaymentIdentifiers
}

internal object UuidPaymentIdentifiersFactory : PaymentIdentifiersFactory {
    override fun create(): PaymentIdentifiers = PaymentIdentifiers(
        paymentId = PaymentId(UUID.randomUUID().toString()),
        idempotencyKey = IdempotencyKey(UUID.randomUUID().toString()),
    )
}
