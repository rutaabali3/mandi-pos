package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = MandiEmerald,
    onPrimary = CrispWhite,
    primaryContainer = MandiEmeraldContainer,
    onPrimaryContainer = OnMandiEmeraldContainer,
    secondary = SaffronAmber,
    onSecondary = CrispWhite,
    secondaryContainer = SaffronAmberContainer,
    onSecondaryContainer = OnSaffronAmberContainer,
    tertiary = SlateSteel,
    onTertiary = CrispWhite,
    tertiaryContainer = SlateSteelContainer,
    onTertiaryContainer = OnSlateSteelContainer,
    background = AlabasterPaper,
    onBackground = Color(0xFF111914),
    surface = CrispWhite,
    onSurface = Color(0xFF111914),
    surfaceVariant = SoftFieldSurface,
    onSurfaceVariant = Color(0xFF425046),
    outline = SubtleOutline,
    error = CrimsonError,
    onError = CrispWhite,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = MandiEmeraldLight,
    onPrimary = Color(0xFF032A16),
    primaryContainer = MandiEmeraldDark,
    onPrimaryContainer = MandiEmeraldContainer,
    secondary = SaffronAmberLight,
    onSecondary = Color(0xFF3A1802),
    secondaryContainer = Color(0xFF5C2D09),
    onSecondaryContainer = SaffronAmberContainer,
    tertiary = Color(0xFF94A3B8),
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF1E293B),
    onTertiaryContainer = Color(0xFFE2E8F0),
    background = DarkCanvas,
    onBackground = Color(0xFFEBF2EC),
    surface = DarkSurface,
    onSurface = Color(0xFFEBF2EC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB5C7BA),
    outline = DarkOutline,
    error = CrimsonErrorLight,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = CrimsonErrorContainer
)

private val MandiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun MandiPosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MandiTypography,
        shapes = MandiShapes,
        content = content
    )
}
