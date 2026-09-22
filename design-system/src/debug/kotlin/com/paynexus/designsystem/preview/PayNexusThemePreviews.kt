package com.paynexus.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.paynexus.designsystem.component.PayNexusButton
import com.paynexus.designsystem.theme.PayNexusSpacing
import com.paynexus.designsystem.theme.PayNexusTheme

@Preview(name = "Light theme", widthDp = 320)
@Composable
private fun PayNexusThemeLightPreview() {
    PayNexusTheme(darkTheme = false) { ThemeSample() }
}

@Preview(name = "Dark theme", widthDp = 600)
@Composable
private fun PayNexusThemeDarkPreview() {
    PayNexusTheme(darkTheme = true) { ThemeSample() }
}

@Composable
private fun ThemeSample() {
    Surface {
        Column(
            modifier = Modifier.padding(PayNexusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.sm),
        ) {
            Text(text = "Design system example", style = MaterialTheme.typography.titleLarge)
            Text(text = "Synthetic content with platform typography.", style = MaterialTheme.typography.bodyLarge)
            PayNexusButton(text = "Continue", onClick = {})
            PayNexusButton(text = "Unavailable", onClick = {}, enabled = false)
        }
    }
}
