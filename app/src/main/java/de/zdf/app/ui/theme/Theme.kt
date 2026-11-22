package de.zdf.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Positive = Color(0xFF16A34A)
val Negative = Color(0xFFDC2626)
val PositiveDark = Color(0xFF4ADE80)
val NegativeDark = Color(0xFFF87171)

private val Light = lightColorScheme(
    primary = Color(0xFF0E5E4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE4),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF34D399),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFF6F7FB),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFEEF1F5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6EE7C5),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0E5E4E),
    onPrimaryContainer = Color(0xFFCDEFE4),
    secondary = Color(0xFF34D399),
    background = Color(0xFF0F1417),
    surface = Color(0xFF0F1417),
    surfaceContainer = Color(0xFF1A2125),
    surfaceContainerLow = Color(0xFF1A2125),
    surfaceContainerHigh = Color(0xFF242C31),
)

@Composable
fun ZdfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

@Composable
fun amountColor(cents: Long): Color {
    val dark = isSystemInDarkTheme()
    return when {
        cents > 0 -> if (dark) PositiveDark else Positive
        cents < 0 -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
