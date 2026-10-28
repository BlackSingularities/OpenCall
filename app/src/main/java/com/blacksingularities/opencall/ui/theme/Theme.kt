package com.blacksingularities.opencall.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    // Czarno-biały interfejs jak w OpenDocument: akcent = biel, czerwień dla rozłączania i usuwania,
    // zieleń tylko dla odbierania połączeń.
    val Amber = Color(0xFFFFFFFF)
    val Coral = Color(0xFFE0E0E0)
    val Record = Color(0xFFFF3B47)
    val Answer = Color(0xFF2FD073)
    val Moon = Color(0xFFBDBDBD)
    val Ink = Color(0xFF000000)
    val Panel = Color(0xFF000000)
    val PanelHigh = Color(0xFF1C1C1C)
    val Glass = Color(0x29FFFFFF)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)

    /** Tła awatarów bez zdjęcia – stonowane, żeby nie łamać czarno-białego interfejsu. */
    val AvatarTints = listOf(
        Color(0xFF2A2A2A), Color(0xFF33302A), Color(0xFF2A3033), Color(0xFF302A33),
        Color(0xFF2A332D), Color(0xFF33292B), Color(0xFF2D2D36), Color(0xFF36332A),
    )
}

private val scheme = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Palette.Ink,
    secondary = Palette.Coral,
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Color.White,
    surface = Palette.Panel,
    onSurface = Color.White,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Palette.PanelHigh,
    outline = Color(0x33FFFFFF),
    error = Palette.Record,
)

val MonoDigits = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp)

@Composable
fun OpenCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
