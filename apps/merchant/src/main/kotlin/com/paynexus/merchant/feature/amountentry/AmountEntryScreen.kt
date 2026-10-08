package com.paynexus.merchant.feature.amountentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paynexus.designsystem.component.PayNexusButton
import com.paynexus.designsystem.theme.PayNexusSpacing
import com.paynexus.merchant.R

@Composable
internal fun AmountEntryScreen(
    state: AmountEntryUiState,
    onAmountChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onChangeAmount: () -> Unit,
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
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PayNexusSpacing.lg, vertical = PayNexusSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.md),
            ) {
                when (val payment = state.payment) {
                    MerchantPaymentUiState.Editing -> AmountEntryContent(
                        state = state,
                        onAmountChanged = onAmountChanged,
                        onConfirm = confirm,
                    )

                    is MerchantPaymentUiState.Confirmation -> ConfirmationContent(
                        confirmation = payment,
                        onChangeAmount = onChangeAmount,
                        onStartPayment = onStartPayment,
                    )

                    is MerchantPaymentUiState.Processing -> ProcessingContent(payment.amount)

                    is MerchantPaymentUiState.Result -> ResultContent(payment, onNewPayment)

                    is MerchantPaymentUiState.TransportFailure -> TransportFailureContent(payment, onNewPayment)
                }
            }
        }
    }
}

@Composable
private fun AmountEntryContent(
    state: AmountEntryUiState,
    onAmountChanged: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.sm),
    ) {
        PaymentTerminalVisual(modifier = Modifier.size(88.dp))
        Text(
            text = stringResource(R.string.amount_entry_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.amount_entry_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    AmountField(state, onAmountChanged, onConfirm)
    AmountEntryKeypad(
        input = state.input,
        onAmountChanged = onAmountChanged,
    )
    PayNexusButton(
        text = stringResource(R.string.amount_entry_confirm),
        onClick = onConfirm,
        enabled = state.isConfirmEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AmountField(
    state: AmountEntryUiState,
    onAmountChanged: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val supportingText = stringResource(state.validation.supportingTextResource())
    val clearDescription = stringResource(R.string.amount_entry_clear_description)
    val isError = when (state.validation) {
        AmountEntryValidation.Empty,
        AmountEntryValidation.Incomplete,
        AmountEntryValidation.Valid,
        -> false

        else -> true
    }
    OutlinedTextField(
        value = state.input,
        onValueChange = onAmountChanged,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (isError) error(supportingText)
            },
        prefix = { Text(stringResource(R.string.amount_entry_currency)) },
        placeholder = { Text(stringResource(R.string.amount_entry_placeholder)) },
        trailingIcon = if (state.input.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onAmountChanged("") },
                    modifier = Modifier.semantics { contentDescription = clearDescription },
                ) {
                    Text("×", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            null
        },
        supportingText = { Text(supportingText) },
        isError = isError,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
    )
}

private fun AmountEntryValidation.supportingTextResource(): Int = when (this) {
    AmountEntryValidation.Empty, AmountEntryValidation.Valid -> R.string.amount_entry_hint
    AmountEntryValidation.Incomplete -> R.string.amount_entry_incomplete
    AmountEntryValidation.InvalidFormat -> R.string.amount_entry_error_format
    AmountEntryValidation.TooManyFractionDigits -> R.string.amount_entry_error_fraction
    AmountEntryValidation.MustBePositive -> R.string.amount_entry_error_positive
    AmountEntryValidation.Overflow -> R.string.amount_entry_error_overflow
}
