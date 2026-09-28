package com.paynexus.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.paynexus.designsystem.theme.PayNexusTheme
import com.paynexus.merchant.feature.amountentry.AmountEntryViewModel
import com.paynexus.merchant.ipc.PaymentServiceClient
import com.paynexus.merchant.ui.MerchantApp

class MainActivity : ComponentActivity() {
    private lateinit var paymentServiceClient: PaymentServiceClient
    private val amountEntryViewModel: AmountEntryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paymentServiceClient = PaymentServiceClient(applicationContext, amountEntryViewModel::onSubmissionStateChanged)
        enableEdgeToEdge()
        setContent {
            PayNexusTheme {
                MerchantApp(amountEntryViewModel, paymentServiceClient::submitPayment)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        paymentServiceClient.bind()
    }

    override fun onStop() {
        paymentServiceClient.unbind()
        super.onStop()
    }

    override fun onDestroy() {
        paymentServiceClient.close()
        super.onDestroy()
    }
}
