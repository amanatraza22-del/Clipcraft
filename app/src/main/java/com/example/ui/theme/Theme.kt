package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCoral,
    onPrimary = Color.White,
    primaryContainer = ElectricCoralDark,
    onPrimaryContainer = ElectricCoralSoft,
    secondary = CyberCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = CyberCyanDark,
    onSecondaryContainer = CyberCyanSoft,
    tertiary = AmberSpotlight,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    error = Color(0xFFFF6B6B),
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricCoral,
    onPrimary = Color.White,
    primaryContainer = ElectricCoralSoft,
    onPrimaryContainer = ElectricCoralDark,
    secondary = Color(0xFF008B9E),
    onSecondary = Color.White,
    secondaryContainer = CyberCyanSoft,
    onSecondaryContainer = CyberCyanDark,
    tertiary = AmberSpotlight,
    onTertiary = TextPrimaryLight,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceHigh,
    surfaceContainerHigh = Color(0xFFE2E6F2),
    outline = LightOutline
)

val ClipCraftShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClipCraftTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClipCraftTypography,
        shapes = ClipCraftShapes,
        content = content
    )
}
