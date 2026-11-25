package com.jimgrok.anchorwatch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Obsidian + Warm Amber night-watch palette matching PetLlama & CMG.
 * Deep OLED canvas, subtle bordered surfaces, warm amber primary accents,
 * emerald live/ok telemetry, sky GPS track, and coral drag/GPS-lost alerts.
 */
object AnchorColors {
    val Canvas = Color(0xFF0E1015)
    val Surface = Color(0xFF141821)
    val SurfaceCard = Color(0xFF181D28)
    val SurfaceElevated = Color(0xFF1F2634)

    val BorderSubtle = Color(0xFF252D3D)
    val BorderStrong = Color(0xFF333F56)

    val Amber = Color(0xFFF59E0B)
    val AmberSoft = Color(0xFF2C2211)
    val AmberOn = Color(0xFF1A1205)

    val Emerald = Color(0xFF34D399)
    val EmeraldSoft = Color(0xFF102822)
    val EmeraldOn = Color(0xFF042219)

    val Sky = Color(0xFF38BDF8)
    val SkySoft = Color(0xFF102433)

    val Coral = Color(0xFFF87171)
    val CoralSoft = Color(0xFF2D161A)
    val CoralOn = Color(0xFF2B0A0D)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

private val Scheme = darkColorScheme(
    primary = AnchorColors.Amber,
    onPrimary = AnchorColors.AmberOn,
    primaryContainer = AnchorColors.AmberSoft,
    onPrimaryContainer = AnchorColors.TextPrimary,
    secondary = AnchorColors.Emerald,
    onSecondary = AnchorColors.EmeraldOn,
    secondaryContainer = AnchorColors.SurfaceElevated,
    onSecondaryContainer = AnchorColors.TextPrimary,
    tertiary = AnchorColors.Sky,
    onTertiary = Color(0xFF061B26),
    background = AnchorColors.Canvas,
    onBackground = AnchorColors.TextPrimary,
    surface = AnchorColors.Surface,
    onSurface = AnchorColors.TextPrimary,
    surfaceVariant = AnchorColors.SurfaceCard,
    onSurfaceVariant = AnchorColors.TextSecondary,
    outline = AnchorColors.BorderStrong,
    outlineVariant = AnchorColors.BorderSubtle,
    error = AnchorColors.Coral,
    onError = AnchorColors.CoralOn,
    errorContainer = AnchorColors.CoralSoft,
    onErrorContainer = Color(0xFFFECACA)
)

private val AnchorTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun AnchorWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AnchorTypography,
        content = content
    )
}
