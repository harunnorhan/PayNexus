package com.paynexus.merchant.feature.amountentry

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.paynexus.designsystem.theme.PayNexusTheme
import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentOutcome

@Preview(name = "Empty", widthDp = 360, heightDp = 800)
@Preview(name = "Empty dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Empty compact", widthDp = 280, heightDp = 600)
@Composable
private fun AmountEntryEmptyPreview() {
    AmountEntryPreview(AmountEntryUiState())
}

@Preview(name = "Valid", widthDp = 360, heightDp = 800)
@Preview(name = "Valid wide", widthDp = 600, heightDp = 800)
@Composable
private fun AmountEntryValidPreview() {
    AmountEntryPreview(
        AmountEntryUiState(
            input = "11111,23",
            amount = previewAmount(1_111_123L),
            validation = AmountEntryValidation.Valid,
        ),
    )
}

@Preview(name = "Invalid", widthDp = 360, heightDp = 800)
@Composable
private fun AmountEntryInvalidPreview() {
    AmountEntryPreview(
        AmountEntryUiState(
            input = "zxc",
            validation = AmountEntryValidation.InvalidFormat,
        ),
    )
}

@Preview(name = "Confirmation", widthDp = 360, heightDp = 800)
@Preview(name = "Confirmation large font", widthDp = 320, heightDp = 800, fontScale = 2f)
@Preview(name = "Confirmation landscape", widthDp = 640, heightDp = 320)
@Composable
private fun PaymentConfirmationPreview() {
    val amount = previewAmount(1_234L)
    AmountEntryPreview(
        AmountEntryUiState(
            input = "12,34",
            amount = amount,
            validation = AmountEntryValidation.Valid,
            payment = MerchantPaymentUiState.Confirmation(amount),
        ),
    )
}

@Preview(name = "Confirmation Service not ready", widthDp = 360, heightDp = 800)
@Composable
private fun PaymentConfirmationFailurePreview() {
    val amount = previewAmount(1_234L)
    AmountEntryPreview(
        AmountEntryUiState(
            input = "12.34",
            amount = amount,
            validation = AmountEntryValidation.Valid,
            payment = MerchantPaymentUiState.Confirmation(amount, PaymentStartFailure.ServiceNotReady),
        ),
    )
}

@Preview(name = "Processing", widthDp = 360, heightDp = 800)
@Preview(name = "Processing dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaymentProcessingPreview() {
    val amount = previewAmount(300L)
    AmountEntryPreview(AmountEntryUiState(payment = MerchantPaymentUiState.Processing(amount)))
}

@Preview(name = "Approved", widthDp = 360, heightDp = 800)
@Preview(name = "Approved dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaymentApprovedPreview() {
    val amount = previewAmount(300L)
    AmountEntryPreview(AmountEntryUiState(payment = MerchantPaymentUiState.Result(amount, PaymentOutcome.Approved)))
}

@Preview(name = "Declined", widthDp = 360, heightDp = 800)
@Composable
private fun PaymentDeclinedPreview() {
    val amount = previewAmount(301L)
    val outcome = PaymentOutcome.Declined(DeclineReason.UNSPECIFIED)
    AmountEntryPreview(AmountEntryUiState(payment = MerchantPaymentUiState.Result(amount, outcome)))
}

@Preview(name = "Failed", widthDp = 360, heightDp = 800)
@Composable
private fun PaymentFailedPreview() {
    val amount = previewAmount(302L)
    val outcome = PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR)
    AmountEntryPreview(AmountEntryUiState(payment = MerchantPaymentUiState.Result(amount, outcome)))
}

@Preview(name = "Transport unavailable", widthDp = 360, heightDp = 800)
@Preview(
    name = "Transport unavailable dark",
    widthDp = 360,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PaymentTransportFailurePreview() {
    val amount = previewAmount(300L)
    val state = MerchantPaymentUiState.TransportFailure(amount, MerchantTransportFailure.OutcomeUnavailable)
    AmountEntryPreview(AmountEntryUiState(payment = state))
}

@Composable
private fun AmountEntryPreview(state: AmountEntryUiState) {
    PayNexusTheme {
        AmountEntryScreen(
            state = state,
            onAmountChanged = {},
            onConfirm = {},
            onChangeAmount = {},
            onStartPayment = {},
            onNewPayment = {},
        )
    }
}

private fun previewAmount(minorUnits: Long): PaymentAmount = PaymentAmount(Money(minorUnits, CurrencyCode.TRY))
