package com.paynexus.merchant.feature.amountentry

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.paynexus.designsystem.theme.PayNexusTheme
import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount

@Preview(name = "Empty", widthDp = 360, heightDp = 640)
@Composable
private fun AmountEntryEmptyPreview() {
    AmountEntryPreview(AmountEntryUiState())
}

@Preview(name = "ValidWhole", widthDp = 360, heightDp = 640)
@Composable
private fun AmountEntryValidWholePreview() {
    AmountEntryPreview(AmountEntryUiState("12", PaymentAmount(Money(1200L, CurrencyCode.TRY)), AmountEntryValidation.Valid))
}

@Preview(name = "FractionalComma", widthDp = 360, heightDp = 640)
@Composable
private fun AmountEntryFractionalCommaPreview() {
    AmountEntryPreview(AmountEntryUiState("12,34", PaymentAmount(Money(1234L, CurrencyCode.TRY)), AmountEntryValidation.Valid))
}

@Preview(name = "Incomplete", widthDp = 360, heightDp = 640)
@Composable
private fun AmountEntryIncompletePreview() {
    AmountEntryPreview(AmountEntryUiState("12.", validation = AmountEntryValidation.Incomplete))
}

@Preview(name = "InvalidZero", widthDp = 360, heightDp = 640)
@Composable
private fun AmountEntryInvalidZeroPreview() {
    AmountEntryPreview(AmountEntryUiState("0", validation = AmountEntryValidation.MustBePositive))
}

@Preview(name = "InvalidFormat", widthDp = 360, heightDp = 640)
@Composable
private fun AmountEntryInvalidFormatPreview() {
    AmountEntryPreview(AmountEntryUiState("12abc", validation = AmountEntryValidation.InvalidFormat))
}

@Preview(name = "Overflow", widthDp = 360, heightDp = 640)
@Composable
private fun AmountEntryOverflowPreview() {
    AmountEntryPreview(AmountEntryUiState("92233720368547758.08", validation = AmountEntryValidation.Overflow))
}

@Preview(name = "Confirmed", widthDp = 360, heightDp = 640)
@Preview(name = "Dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Narrow", widthDp = 280, heightDp = 480)
@Preview(name = "LargeFont", widthDp = 280, heightDp = 480, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 640, heightDp = 280)
@Preview(name = "Wide", widthDp = 600, heightDp = 800)
@Composable
private fun AmountEntryConfirmedPreview() {
    val amount = PaymentAmount(Money(1234L, CurrencyCode.TRY))
    AmountEntryPreview(AmountEntryUiState("12.34", amount, AmountEntryValidation.Valid, amount))
}

@Composable
private fun AmountEntryPreview(state: AmountEntryUiState) {
    PayNexusTheme {
        AmountEntryScreen(state = state, onAmountChanged = {}, onConfirm = {})
    }
}
