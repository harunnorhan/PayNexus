package com.paynexus.payment.domain

data class PaymentAmount(
    val money: Money,
) {
    init {
        require(money.minorUnits > 0) { "Payment amount must be positive." }
    }
}
