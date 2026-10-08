package com.paynexus.merchant.feature.amountentry

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paynexus.designsystem.component.PayNexusButton
import com.paynexus.designsystem.theme.PayNexusSpacing
import com.paynexus.merchant.R
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentOutcome

@Composable
internal fun ConfirmationContent(
    confirmation: MerchantPaymentUiState.Confirmation,
    onChangeAmount: () -> Unit,
    onStartPayment: () -> Unit,
) {
    PaymentFlowHeader(
        title = stringResource(R.string.payment_confirmation_title),
        onBack = onChangeAmount,
    )
    ConfirmationSummary(confirmation.amount)
    confirmation.failure?.let { failure ->
        Text(
            text = stringResource(failure.messageResource()),
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
    PayNexusButton(
        text = stringResource(R.string.payment_change_amount),
        onClick = onChangeAmount,
        modifier = Modifier.fillMaxWidth(),
        secondary = true,
    )
}

@Composable
private fun ConfirmationSummary(amount: PaymentAmount) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PayNexusSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.md),
        ) {
            PaymentTerminalVisual(modifier = Modifier.size(88.dp))
            Text(
                text = stringResource(R.string.payment_confirmation_amount_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.amount_entry_formatted_try, TryAmountFormatter.format(amount)),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(PayNexusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "ⓘ", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(PayNexusSpacing.md))
            Text(
                text = stringResource(R.string.payment_confirmation_demo),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
internal fun ProcessingContent(amount: PaymentAmount) {
    val progressDescription = stringResource(R.string.payment_processing_progress_description)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.md),
    ) {
        Spacer(Modifier.height(PayNexusSpacing.xl))
        Box(
            modifier = Modifier.size(184.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(176.dp)
                    .progressSemantics()
                    .semantics { contentDescription = progressDescription },
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
            PaymentTerminalVisual(modifier = Modifier.size(112.dp))
        }
        Text(
            text = stringResource(R.string.payment_processing_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.amount_entry_formatted_try, TryAmountFormatter.format(amount)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.payment_processing_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ResultContent(
    state: MerchantPaymentUiState.Result,
    onNewPayment: () -> Unit,
) {
    val presentation = state.outcome.presentation()
    val (containerColor, contentColor) = presentation.colors()
    PaymentResultContent(
        title = stringResource(presentation.title),
        amount = state.amount,
        detail = stringResource(presentation.detail),
        symbol = presentation.symbol,
        containerColor = containerColor,
        contentColor = contentColor,
        onNewPayment = onNewPayment,
    )
}

@Composable
internal fun TransportFailureContent(
    state: MerchantPaymentUiState.TransportFailure,
    onNewPayment: () -> Unit,
) {
    PaymentResultContent(
        title = stringResource(R.string.payment_transport_failure_title),
        amount = state.amount,
        detail = stringResource(state.failure.messageResource()),
        symbol = "?",
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onNewPayment = onNewPayment,
    )
}

@Composable
internal fun PaymentTerminalVisual(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(colorResource(R.color.merchant_brand_purple), MaterialTheme.shapes.large)
            .clearAndSetSemantics {},
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PaymentFlowHeader(
    title: String,
    onBack: () -> Unit,
) {
    val backDescription = stringResource(R.string.payment_change_amount_back_description)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .semantics { contentDescription = backDescription },
        ) {
            Text(text = "‹", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PaymentResultContent(
    title: String,
    amount: PaymentAmount,
    detail: String,
    symbol: String,
    containerColor: Color,
    contentColor: Color,
    onNewPayment: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PayNexusSpacing.lg, vertical = PayNexusSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(contentColor, MaterialTheme.shapes.extraLarge)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    color = MaterialTheme.colorScheme.surface,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.amount_entry_formatted_try, TryAmountFormatter.format(amount)),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
    PayNexusButton(
        text = stringResource(R.string.payment_new),
        onClick = onNewPayment,
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class ResultPresentation(
    val title: Int,
    val detail: Int,
    val symbol: String,
    val colorFamily: ResultColorFamily,
) {
    @Composable
    fun colors(): Pair<Color, Color> = when (colorFamily) {
        ResultColorFamily.Success ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        ResultColorFamily.Caution ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

        ResultColorFamily.Failure ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
}

private enum class ResultColorFamily {
    Success,
    Caution,
    Failure,
}

private fun PaymentOutcome.presentation(): ResultPresentation = when (this) {
    PaymentOutcome.Approved -> ResultPresentation(
        R.string.payment_approved_title,
        R.string.payment_approved_detail,
        "✓",
        ResultColorFamily.Success,
    )

    is PaymentOutcome.Declined -> when (reason) {
        DeclineReason.UNSPECIFIED -> ResultPresentation(
            R.string.payment_declined_title,
            R.string.payment_declined_detail,
            "!",
            ResultColorFamily.Caution,
        )
    }

    is PaymentOutcome.Failed -> when (failure) {
        PaymentFailure.PROCESSING_ERROR -> ResultPresentation(
            R.string.payment_failed_title,
            R.string.payment_failed_detail,
            "×",
            ResultColorFamily.Failure,
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
    MerchantTransportFailure.OutcomeUnavailable -> R.string.payment_transport_outcome_unavailable
    MerchantTransportFailure.ServiceUnavailable -> R.string.payment_transport_service_unavailable
    MerchantTransportFailure.Abandoned -> R.string.payment_transport_abandoned
}
