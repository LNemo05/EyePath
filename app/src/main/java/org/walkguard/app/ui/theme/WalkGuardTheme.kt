package org.walkguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppleBg = Color(0xFFF2F2F7)
val AppleCard = Color(0xFFFFFFFF)
val AppleText = Color(0xFF1C1C1E)
val AppleSecondary = Color(0xFF8E8E93)
val AppleTertiary = Color(0xFFAEAEB2)
val AppleBlue = Color(0xFF007AFF)
val AppleGreen = Color(0xFF34C759)
val AppleOrange = Color(0xFFFF9500)
val AppleRed = Color(0xFFFF3B30)
val AppleFill = Color(0x1E787880) // rgba(120,120,128,0.12)
val AppleFillStrong = Color(0x33787880) // rgba(120,120,128,0.20)
val AppleSeparator = Color(0x1F3C3C43) // rgba(60,60,67,0.12)
val AppleBlueSoft = Color(0x1F007AFF) // ~12% blue
val AppleBlueBorder = Color(0x59007AFF) // ~35% blue
val AppleGreenSoft = Color(0x2634C759)
val AppleRedSoft = Color(0x1FFF3B30)
val AppleChevron = Color(0xFFC7C7CC)
val AppleTabBarBg = Color(0xDBFFFFFF) // ~86% white

private val WalkGuardColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    secondary = AppleSecondary,
    onSecondary = Color.White,
    tertiary = AppleGreen,
    onTertiary = Color.White,
    background = AppleBg,
    onBackground = AppleText,
    surface = AppleCard,
    onSurface = AppleText,
    surfaceVariant = AppleFill,
    onSurfaceVariant = AppleSecondary,
    error = AppleRed,
    onError = Color.White,
    outline = AppleSeparator
)

private val WalkGuardTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        color = AppleText
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        color = AppleText
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
        color = AppleText
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        color = AppleText
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        color = AppleText
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        color = AppleSecondary
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = AppleText
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = AppleText
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = AppleSecondary
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = AppleText
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AppleSecondary
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AppleSecondary
    )
)

@Composable
fun WalkGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WalkGuardColorScheme,
        typography = WalkGuardTypography,
        content = content
    )
}
