package com.paynexus.merchant.feature.amountentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount

internal class AmountEntryViewModel : ViewModel() {
    var uiState by mutableStateOf(AmountEntryUiState())
        private set

    fun onAmountChanged(input: String) {
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
            uiState = uiState.copy(confirmedAmount = uiState.amount)
        }
    }

    private fun AmountInputError.toValidation(): AmountEntryValidation = when (this) {
        AmountInputError.InvalidFormat -> AmountEntryValidation.InvalidFormat
        AmountInputError.TooManyFractionDigits -> AmountEntryValidation.TooManyFractionDigits
        AmountInputError.Overflow -> AmountEntryValidation.Overflow
    }
}
