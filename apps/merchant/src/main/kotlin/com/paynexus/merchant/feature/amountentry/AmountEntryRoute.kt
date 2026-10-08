package com.paynexus.merchant.feature.amountentry

import androidx.compose.runtime.Composable
import com.paynexus.merchant.ipc.PaymentSubmissionAdmission
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentId

@Composable
internal fun AmountEntryRoute(
    viewModel: AmountEntryViewModel,
    submitPayment: (PaymentId, IdempotencyKey, PaymentAmount) -> PaymentSubmissionAdmission,
) {
    AmountEntryScreen(
        state = viewModel.uiState,
        onAmountChanged = viewModel::onAmountChanged,
        onConfirm = viewModel::onConfirm,
        onChangeAmount = viewModel::onChangeAmount,
        onStartPayment = { viewModel.onStartPayment(submitPayment) },
        onNewPayment = viewModel::onNewPayment,
    )
}
