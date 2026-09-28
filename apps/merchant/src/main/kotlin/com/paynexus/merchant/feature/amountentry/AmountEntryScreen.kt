package com.paynexus.merchant.feature.amountentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.paynexus.designsystem.component.PayNexusButton
import com.paynexus.designsystem.theme.PayNexusSpacing
import com.paynexus.merchant.R
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentOutcome

@Composable
internal fun AmountEntryScreen(
    state: AmountEntryUiState,
    onAmountChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onStartPayment: () -> Unit,
    onNewPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val confirm = {
        if (state.isConfirmEnabled) {
            onConfirm()
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(PayNexusSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.md),
        ) {
            when (val payment = state.payment) {
                MerchantPaymentUiState.Editing,
                is MerchantPaymentUiState.Confirmation,
                -> AmountEntryContent(state, onAmountChanged, confirm, onStartPayment)

                is MerchantPaymentUiState.Processing -> ProcessingContent(payment.amount)

                is MerchantPaymentUiState.Result -> ResultContent(payment, onNewPayment)

                is MerchantPaymentUiState.TransportFailure -> TransportFailureContent(payment, onNewPayment)
            }
        }
    }
}

@Composable
private fun AmountEntryContent(
    state: AmountEntryUiState,
    onAmountChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onStartPayment: () -> Unit,
) {
    Text(
        text = stringResource(R.string.amount_entry_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
    AmountField(state, onAmountChanged, onConfirm)
    PayNexusButton(
        text = stringResource(R.string.amount_entry_confirm),
        onClick = onConfirm,
        enabled = state.isConfirmEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
    (state.payment as? MerchantPaymentUiState.Confirmation)?.let { confirmation ->
        ConfirmedAmount(confirmation.amount)
        confirmation.failure?.let {
            Text(
                text = stringResource(it.messageResource()),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        PayNexusButton(
            text = stringResource(R.string.payment_start),
            onClick = onStartPayment,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AmountField(state: AmountEntryUiState, onAmountChanged: (String) -> Unit, onConfirm: () -> Unit) {
    val supportingText = stringResource(state.validation.supportingTextResource())
    val isError = when (state.validation) {
        AmountEntryValidation.Empty, AmountEntryValidation.Incomplete, AmountEntryValidation.Valid -> false
        else -> true
    }
    OutlinedTextField(
        value = state.input,
        onValueChange = onAmountChanged,
        modifier = Modifier.fillMaxWidth().semantics {
            if (isError) error(supportingText)
        },
        label = { Text(stringResource(R.string.amount_entry_label)) },
        supportingText = { Text(supportingText) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
    )
}

@Composable
private fun ConfirmedAmount(amount: PaymentAmount) {
    val formattedAmount = stringResource(R.string.amount_entry_formatted_try, TryAmountFormatter.format(amount))
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.sm),
    ) {
        Text(stringResource(R.string.amount_entry_ready, formattedAmount), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.payment_confirmation_demo),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProcessingContent(amount: PaymentAmount) {
    PaymentStatusContent(
        title = stringResource(R.string.payment_processing_title),
        amount = amount,
        detail = stringResource(R.string.payment_processing_detail),
    )
}

@Composable
private fun ResultContent(
    state: MerchantPaymentUiState.Result,
    onNewPayment: () -> Unit,
) {
    val (title, detail) = when (val outcome = state.outcome) {
        PaymentOutcome.Approved -> stringResource(R.string.payment_approved_title) to
            stringResource(R.string.payment_approved_detail)

        is PaymentOutcome.Declined -> when (outcome.reason) {
            DeclineReason.UNSPECIFIED -> stringResource(R.string.payment_declined_title) to
                stringResource(R.string.payment_declined_detail)
        }

        is PaymentOutcome.Failed -> when (outcome.failure) {
            PaymentFailure.PROCESSING_ERROR -> stringResource(R.string.payment_failed_title) to
                stringResource(R.string.payment_failed_detail)
        }
    }
    PaymentStatusContent(title, state.amount, detail, onNewPayment)
}

@Composable
private fun TransportFailureContent(
    state: MerchantPaymentUiState.TransportFailure,
    onNewPayment: () -> Unit,
) {
    PaymentStatusContent(
        title = stringResource(R.string.payment_transport_failure_title),
        amount = state.amount,
        detail = stringResource(state.failure.messageResource()),
        onNewPayment = onNewPayment,
    )
}

@Composable
private fun PaymentStatusContent(
    title: String,
    amount: PaymentAmount,
    detail: String,
    onNewPayment: (() -> Unit)? = null,
) {
    val formattedAmount = stringResource(R.string.amount_entry_formatted_try, TryAmountFormatter.format(amount))
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.sm),
    ) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Text(formattedAmount, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    onNewPayment?.let {
        PayNexusButton(
            text = stringResource(R.string.payment_new),
            onClick = it,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun PaymentStartFailure.messageResource(): Int = when (this) {
    PaymentStartFailure.ServiceNotReady -> R.string.payment_start_not_ready
    PaymentStartFailure.AlreadyActive -> R.string.payment_start_already_active
    PaymentStartFailure.InvalidRequest -> R.string.payment_start_invalid
}

private fun MerchantTransportFailure.messageResource(): Int = when (this) {
    MerchantTransportFailure.ConnectionLost -> R.string.payment_transport_connection_lost
    MerchantTransportFailure.DispatchFailed -> R.string.payment_transport_dispatch_failed
    MerchantTransportFailure.ProtocolFailure -> R.string.payment_transport_protocol_failure
    MerchantTransportFailure.RequestRejected -> R.string.payment_transport_rejected
    MerchantTransportFailure.ServiceUnavailable -> R.string.payment_transport_service_unavailable
    MerchantTransportFailure.Abandoned -> R.string.payment_transport_abandoned
}

private fun AmountEntryValidation.supportingTextResource(): Int = when (this) {
    AmountEntryValidation.Empty, AmountEntryValidation.Valid -> R.string.amount_entry_hint
    AmountEntryValidation.Incomplete -> R.string.amount_entry_incomplete
    AmountEntryValidation.InvalidFormat -> R.string.amount_entry_error_format
    AmountEntryValidation.TooManyFractionDigits -> R.string.amount_entry_error_fraction
    AmountEntryValidation.MustBePositive -> R.string.amount_entry_error_positive
    AmountEntryValidation.Overflow -> R.string.amount_entry_error_overflow
}
