package com.paynexus.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DefaultTypography = Typography()

internal val PayNexusTypography =
    Typography(
        headlineSmall = DefaultTypography.headlineSmall.copy(
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
        ),
        titleLarge = DefaultTypography.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
        ),
        titleMedium = DefaultTypography.titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = DefaultTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = DefaultTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = DefaultTypography.labelLarge.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
