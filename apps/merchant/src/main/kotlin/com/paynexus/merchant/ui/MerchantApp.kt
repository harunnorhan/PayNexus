package com.paynexus.merchant.ui

import androidx.compose.runtime.Composable
import com.paynexus.merchant.feature.amountentry.AmountEntryRoute
import com.paynexus.merchant.feature.amountentry.AmountEntryViewModel
import com.paynexus.merchant.ipc.PaymentSubmissionAdmission
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentId

@Composable
internal fun MerchantApp(
    viewModel: AmountEntryViewModel,
    submitPayment: (PaymentId, IdempotencyKey, PaymentAmount) -> PaymentSubmissionAdmission,
) {
    AmountEntryRoute(viewModel, submitPayment)
}
