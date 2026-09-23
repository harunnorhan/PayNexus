package com.paynexus.merchant.feature.amountentry

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun AmountEntryRoute(viewModel: AmountEntryViewModel = viewModel()) {
    AmountEntryScreen(
        state = viewModel.uiState,
        onAmountChanged = viewModel::onAmountChanged,
        onConfirm = viewModel::onConfirm,
    )
}
