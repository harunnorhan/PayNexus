package com.paynexus.merchant.feature.amountentry

import com.paynexus.payment.domain.PaymentAmount

internal object TryAmountFormatter {
    private const val MINOR_UNITS_PER_MAJOR = 100
    private const val FRACTION_DIGITS = 2

    fun format(amount: PaymentAmount): String {
        val minorUnits = amount.money.minorUnits
        val major = minorUnits / MINOR_UNITS_PER_MAJOR
        val fraction = (minorUnits % MINOR_UNITS_PER_MAJOR).toString().padStart(FRACTION_DIGITS, '0')
        return "$major.$fraction"
    }
}
