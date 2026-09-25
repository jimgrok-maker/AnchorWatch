package com.jimgrok.anchorwatch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Hull = Color(0xFF0B1C24)
private val Teak = Color(0xFFE8C36A)
private val Foam = Color(0xFF7EC8C8)
private val Alarm = Color(0xFFFF5A4A)
private val Panel = Color(0xFF122833)
private val Ink = Color(0xFFE7F1F4)

private val Scheme = darkColorScheme(
    primary = Teak,
    onPrimary = Hull,
    secondary = Foam,
    onSecondary = Hull,
    background = Hull,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    error = Alarm,
    onError = Color.White
)

@Composable
fun AnchorWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        content = content
    )
}
