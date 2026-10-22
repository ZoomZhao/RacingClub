package com.zoomzhao.racingclub

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Small Chinese labels keep their natural line height instead of inheriting 24sp.
private fun TextStyle.clubText()=copy(letterSpacing=0.sp,lineHeight=TextUnit.Unspecified,platformStyle=PlatformTextStyle(includeFontPadding=false))
internal val ClubTypography=Typography().let { base -> base.copy(
    displayLarge=base.displayLarge.clubText(),
    displayMedium=base.displayMedium.clubText(),
    displaySmall=base.displaySmall.clubText(),
    headlineLarge=base.headlineLarge.clubText(),
    headlineMedium=base.headlineMedium.clubText(),
    headlineSmall=base.headlineSmall.clubText(),
    titleLarge=base.titleLarge.clubText(),
    titleMedium=base.titleMedium.clubText(),
    titleSmall=base.titleSmall.clubText(),
    bodyLarge=base.bodyLarge.clubText(),
    bodyMedium=base.bodyMedium.clubText(),
    bodySmall=base.bodySmall.clubText(),
    labelLarge=base.labelLarge.clubText(),
    labelMedium=base.labelMedium.clubText(),
    labelSmall=base.labelSmall.clubText()
)}
