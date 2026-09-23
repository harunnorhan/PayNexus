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
import com.paynexus.payment.domain.PaymentAmount

@Composable
internal fun AmountEntryScreen(
    state: AmountEntryUiState,
    onAmountChanged: (String) -> Unit,
    onConfirm: () -> Unit,
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
            Text(
                text = stringResource(R.string.amount_entry_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            AmountField(state, onAmountChanged, confirm)
            PayNexusButton(
                text = stringResource(R.string.amount_entry_confirm),
                onClick = confirm,
                enabled = state.isConfirmEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            state.confirmedAmount?.let { ConfirmedAmount(it) }
        }
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
            stringResource(R.string.amount_entry_no_payment),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun AmountEntryValidation.supportingTextResource(): Int = when (this) {
    AmountEntryValidation.Empty, AmountEntryValidation.Valid -> R.string.amount_entry_hint
    AmountEntryValidation.Incomplete -> R.string.amount_entry_incomplete
    AmountEntryValidation.InvalidFormat -> R.string.amount_entry_error_format
    AmountEntryValidation.TooManyFractionDigits -> R.string.amount_entry_error_fraction
    AmountEntryValidation.MustBePositive -> R.string.amount_entry_error_positive
    AmountEntryValidation.Overflow -> R.string.amount_entry_error_overflow
}
