package com.paynexus.merchant.feature.amountentry

import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentOutcome

internal data class AmountEntryUiState(
    val input: String = "",
    val amount: PaymentAmount? = null,
    val validation: AmountEntryValidation = AmountEntryValidation.Empty,
    val payment: MerchantPaymentUiState = MerchantPaymentUiState.Editing,
) {
    val isConfirmEnabled: Boolean
        get() = amount != null && payment == MerchantPaymentUiState.Editing
}

internal sealed interface MerchantPaymentUiState {
    data object Editing : MerchantPaymentUiState

    data class Confirmation(
        val amount: PaymentAmount,
        val failure: PaymentStartFailure? = null,
    ) : MerchantPaymentUiState

    data class Processing(val amount: PaymentAmount) : MerchantPaymentUiState

    data class Result(val amount: PaymentAmount, val outcome: PaymentOutcome) : MerchantPaymentUiState

    data class TransportFailure(
        val amount: PaymentAmount,
        val failure: MerchantTransportFailure,
    ) : MerchantPaymentUiState
}

internal enum class PaymentStartFailure {
    ServiceNotReady,
    AlreadyActive,
    InvalidRequest,
}

internal enum class MerchantTransportFailure {
    ConnectionLost,
    DispatchFailed,
    ProtocolFailure,
    RequestRejected,
    OutcomeUnavailable,
    ServiceUnavailable,
    Abandoned,
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
