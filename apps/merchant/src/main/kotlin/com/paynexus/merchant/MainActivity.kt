package com.paynexus.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paynexus.designsystem.theme.PayNexusTheme
import com.paynexus.merchant.ipc.PaymentServiceClient
import com.paynexus.merchant.ui.MerchantApp

class MainActivity : ComponentActivity() {
    private lateinit var paymentServiceClient: PaymentServiceClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paymentServiceClient = PaymentServiceClient(applicationContext)
        enableEdgeToEdge()
        setContent {
            PayNexusTheme {
                MerchantApp()
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
