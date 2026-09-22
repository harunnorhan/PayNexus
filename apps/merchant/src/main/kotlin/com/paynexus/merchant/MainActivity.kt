package com.paynexus.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paynexus.designsystem.theme.PayNexusTheme
import com.paynexus.merchant.ui.MerchantApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PayNexusTheme {
                MerchantApp()
            }
        }
    }
}
