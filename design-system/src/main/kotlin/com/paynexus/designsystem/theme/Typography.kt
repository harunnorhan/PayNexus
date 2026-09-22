package com.paynexus.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

private val DefaultTypography = Typography()

internal val PayNexusTypography =
    Typography(
        titleLarge = DefaultTypography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
        bodyLarge = DefaultTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        labelLarge = DefaultTypography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
    )
