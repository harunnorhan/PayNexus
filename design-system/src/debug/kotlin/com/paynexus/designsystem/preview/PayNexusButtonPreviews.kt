package com.paynexus.designsystem.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.paynexus.designsystem.component.PayNexusButton
import com.paynexus.designsystem.theme.PayNexusTheme

@Preview(name = "Light enabled", showBackground = true)
@Composable
private fun PayNexusButtonLightPreview() {
    PayNexusTheme(darkTheme = false) {
        Surface { PayNexusButton(text = "Continue", onClick = {}) }
    }
}

@Preview(name = "Dark enabled", showBackground = true)
@Composable
private fun PayNexusButtonDarkPreview() {
    PayNexusTheme(darkTheme = true) {
        Surface { PayNexusButton(text = "Continue", onClick = {}) }
    }
}

@Preview(name = "Disabled", showBackground = true)
@Composable
private fun PayNexusButtonDisabledPreview() {
    PayNexusTheme(darkTheme = false) {
        Surface { PayNexusButton(text = "Continue", onClick = {}, enabled = false) }
    }
}

@Preview(name = "Long text in narrow space", widthDp = 160, showBackground = true)
@Composable
private fun PayNexusButtonLongTextPreview() {
    PayNexusTheme(darkTheme = false) {
        Surface { PayNexusButton(text = "Continue to the next example", onClick = {}) }
    }
}

@Preview(name = "Large font", widthDp = 240, fontScale = 2f, showBackground = true)
@Composable
private fun PayNexusButtonLargeFontPreview() {
    PayNexusTheme(darkTheme = true) {
        Surface { PayNexusButton(text = "Continue to the next example", onClick = {}) }
    }
}
