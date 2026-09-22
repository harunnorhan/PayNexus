package com.paynexus.merchant.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.paynexus.designsystem.theme.PayNexusTheme

@Preview(name = "Light", widthDp = 360, heightDp = 640)
@Composable
private fun MerchantShellLightPreview() {
    PayNexusTheme {
        MerchantShell()
    }
}

@Preview(name = "Dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MerchantShellDarkPreview() {
    PayNexusTheme {
        MerchantShell()
    }
}

@Preview(name = "Narrow", widthDp = 280, heightDp = 480)
@Composable
private fun MerchantShellNarrowPreview() {
    PayNexusTheme {
        MerchantShell()
    }
}

@Preview(name = "Wide", widthDp = 600, heightDp = 800)
@Composable
private fun MerchantShellWidePreview() {
    PayNexusTheme {
        MerchantShell()
    }
}

@Preview(name = "LargeFont", widthDp = 280, heightDp = 480, fontScale = 2f)
@Composable
private fun MerchantShellLargeFontPreview() {
    PayNexusTheme {
        MerchantShell()
    }
}

@Preview(name = "Landscape", widthDp = 640, heightDp = 280)
@Composable
private fun MerchantShellLandscapePreview() {
    PayNexusTheme {
        MerchantShell()
    }
}
