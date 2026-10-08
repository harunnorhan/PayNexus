package com.paynexus.merchant.feature.amountentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paynexus.designsystem.theme.PayNexusSpacing
import com.paynexus.merchant.R

internal fun appendAmountEntryKeypadInput(
    input: String,
    value: String,
): String = input + value

internal fun backspaceAmountEntryKeypadInput(input: String): String = input.dropLast(1)

@Composable
internal fun AmountEntryKeypad(
    input: String,
    onAmountChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backspaceDescription = stringResource(R.string.amount_keypad_backspace_description)
    val doubleZeroDescription = stringResource(R.string.amount_keypad_double_zero_description)
    val rows = listOf(
        listOf(KeypadKey("1"), KeypadKey("2"), KeypadKey("3")),
        listOf(KeypadKey("4"), KeypadKey("5"), KeypadKey("6")),
        listOf(KeypadKey("7"), KeypadKey("8"), KeypadKey("9")),
        listOf(
            KeypadKey("00", doubleZeroDescription),
            KeypadKey("0"),
            KeypadKey("⌫", backspaceDescription, isBackspace = true),
        ),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PayNexusSpacing.sm),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PayNexusSpacing.sm),
            ) {
                row.forEach { key ->
                    KeypadButton(
                        key = key,
                        onClick = {
                            val updated = if (key.isBackspace) {
                                backspaceAmountEntryKeypadInput(input)
                            } else {
                                appendAmountEntryKeypadInput(input, key.label)
                            }
                            onAmountChanged(updated)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    key: KeypadKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics {
                role = Role.Button
                key.description?.let { contentDescription = it }
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = key.label,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(align = androidx.compose.ui.Alignment.Center),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class KeypadKey(
    val label: String,
    val description: String? = null,
    val isBackspace: Boolean = false,
)
