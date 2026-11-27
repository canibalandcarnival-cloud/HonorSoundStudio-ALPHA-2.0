package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HonorSoundDarkColorScheme = darkColorScheme(
    primary = MagicCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = ObsidianElevated,
    onPrimaryContainer = MagicCyan,
    secondary = MagicSapphire,
    onSecondary = TextPrimary,
    secondaryContainer = LiquidGlassBase,
    onSecondaryContainer = TextPrimary,
    tertiary = MagicEmerald,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = LiquidGlassBase,
    onSurfaceVariant = TextSecondary,
    outline = LiquidGlassBorderLight,
    error = MagicRose,
    onError = ObsidianVoid
)

@Composable
fun HonorSoundStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HonorSoundDarkColorScheme,
        typography = Typography,
        content = content
    )
}
