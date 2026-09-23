package com.paynexus.merchant.feature.amountentry

import com.paynexus.payment.domain.PaymentAmount

internal data class AmountEntryUiState(
    val input: String = "",
    val amount: PaymentAmount? = null,
    val validation: AmountEntryValidation = AmountEntryValidation.Empty,
    val confirmedAmount: PaymentAmount? = null,
) {
    val isConfirmEnabled: Boolean
        get() = amount != null && confirmedAmount == null
}

internal enum class AmountEntryValidation {
    Empty,
    Incomplete,
    Valid,
    InvalidFormat,
    TooManyFractionDigits,
    MustBePositive,
    Overflow,
}
