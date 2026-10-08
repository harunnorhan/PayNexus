package com.paynexus.merchant.feature.amountentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.paynexus.merchant.ipc.PaymentSubmissionAdmission
import com.paynexus.merchant.ipc.PaymentSubmissionState
import com.paynexus.merchant.ipc.PaymentTransportFailure
import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentId

internal class AmountEntryViewModel(
    private val identifiersFactory: PaymentIdentifiersFactory = UuidPaymentIdentifiersFactory,
) : ViewModel() {
    var uiState by mutableStateOf(AmountEntryUiState())
        private set
    private var ownedIdentifiers: PaymentIdentifiers? = null

    fun onAmountChanged(input: String) {
        if (uiState.payment !is MerchantPaymentUiState.Editing &&
            uiState.payment !is MerchantPaymentUiState.Confirmation
        ) {
            return
        }
        // Every edit, including an identical value, resumes editing and clears confirmation.
        uiState = when (val result = TryAmountParser.parse(input)) {
            AmountParseResult.Empty -> AmountEntryUiState(input = input)

            AmountParseResult.Incomplete -> AmountEntryUiState(input, validation = AmountEntryValidation.Incomplete)

            is AmountParseResult.Invalid -> AmountEntryUiState(input, validation = result.reason.toValidation())

            is AmountParseResult.Parsed -> if (result.minorUnits == 0L) {
                AmountEntryUiState(input, validation = AmountEntryValidation.MustBePositive)
            } else {
                AmountEntryUiState(
                    input = input,
                    amount = PaymentAmount(Money(result.minorUnits, CurrencyCode.TRY)),
                    validation = AmountEntryValidation.Valid,
                )
            }
        }
    }

    fun onConfirm() {
        if (uiState.isConfirmEnabled) {
            val amount = checkNotNull(uiState.amount)
            uiState = uiState.copy(payment = MerchantPaymentUiState.Confirmation(amount))
        }
    }

    fun onChangeAmount() {
        if (uiState.payment is MerchantPaymentUiState.Confirmation) {
            uiState = uiState.copy(payment = MerchantPaymentUiState.Editing)
        }
    }

    fun onStartPayment(
        submit: (PaymentId, IdempotencyKey, PaymentAmount) -> PaymentSubmissionAdmission,
    ) {
        val confirmation = uiState.payment as? MerchantPaymentUiState.Confirmation ?: return
        if (ownedIdentifiers != null) return
        val identifiers = identifiersFactory.create()
        ownedIdentifiers = identifiers
        uiState = uiState.copy(payment = MerchantPaymentUiState.Processing(confirmation.amount))
        when (submit(identifiers.paymentId, identifiers.idempotencyKey, confirmation.amount)) {
            PaymentSubmissionAdmission.Accepted -> Unit
            PaymentSubmissionAdmission.NotReady -> rejectStart(confirmation, PaymentStartFailure.ServiceNotReady)
            PaymentSubmissionAdmission.AlreadyActive -> rejectStart(confirmation, PaymentStartFailure.AlreadyActive)
            PaymentSubmissionAdmission.InvalidRequest -> rejectStart(confirmation, PaymentStartFailure.InvalidRequest)
        }
    }

    fun onSubmissionStateChanged(state: PaymentSubmissionState) {
        val processing = uiState.payment as? MerchantPaymentUiState.Processing ?: return
        val identifiers = ownedIdentifiers ?: return
        when (state) {
            is PaymentSubmissionState.Completed -> if (
                matches(state.paymentId, state.idempotencyKey, identifiers)
            ) {
                uiState = uiState.copy(payment = MerchantPaymentUiState.Result(processing.amount, state.outcome))
            }

            is PaymentSubmissionState.TransportFailed -> if (
                matches(state.paymentId, state.idempotencyKey, identifiers)
            ) {
                uiState = uiState.copy(
                    payment = MerchantPaymentUiState.TransportFailure(processing.amount, state.reason.toUiFailure()),
                )
            }

            is PaymentSubmissionState.Abandoned -> if (
                matches(state.paymentId, state.idempotencyKey, identifiers)
            ) {
                uiState = uiState.copy(
                    payment = MerchantPaymentUiState.TransportFailure(
                        processing.amount,
                        MerchantTransportFailure.Abandoned,
                    ),
                )
            }

            PaymentSubmissionState.Idle, is PaymentSubmissionState.Pending -> Unit
        }
    }

    fun onNewPayment() {
        if (uiState.payment !is MerchantPaymentUiState.Result &&
            uiState.payment !is MerchantPaymentUiState.TransportFailure
        ) {
            return
        }
        ownedIdentifiers = null
        uiState = AmountEntryUiState()
    }

    private fun rejectStart(
        confirmation: MerchantPaymentUiState.Confirmation,
        failure: PaymentStartFailure,
    ) {
        ownedIdentifiers = null
        uiState = uiState.copy(payment = confirmation.copy(failure = failure))
    }

    private fun matches(
        paymentId: PaymentId,
        idempotencyKey: IdempotencyKey,
        identifiers: PaymentIdentifiers,
    ): Boolean = paymentId == identifiers.paymentId && idempotencyKey == identifiers.idempotencyKey

    private fun PaymentTransportFailure.toUiFailure(): MerchantTransportFailure = when (this) {
        PaymentTransportFailure.ConnectionLost -> MerchantTransportFailure.ConnectionLost

        PaymentTransportFailure.DispatchFailed -> MerchantTransportFailure.DispatchFailed

        PaymentTransportFailure.InvalidResult -> MerchantTransportFailure.ProtocolFailure

        PaymentTransportFailure.RequestRejected -> MerchantTransportFailure.RequestRejected

        PaymentTransportFailure.OutcomeUnavailable -> MerchantTransportFailure.OutcomeUnavailable

        PaymentTransportFailure.PermissionDenied,
        PaymentTransportFailure.WorkerUnavailable,
        -> MerchantTransportFailure.ServiceUnavailable
    }

    private fun AmountInputError.toValidation(): AmountEntryValidation = when (this) {
        AmountInputError.InvalidFormat -> AmountEntryValidation.InvalidFormat
        AmountInputError.TooManyFractionDigits -> AmountEntryValidation.TooManyFractionDigits
        AmountInputError.Overflow -> AmountEntryValidation.Overflow
    }
}
