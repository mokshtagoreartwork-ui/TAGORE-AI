package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TagoreDarkColorScheme = darkColorScheme(
    primary = TagoreCyanPrimary,
    onPrimary = TagoreBlack,
    primaryContainer = TagoreObsidianCard,
    onPrimaryContainer = TagoreCyanBright,
    secondary = TagoreCyanDeep,
    onSecondary = TagoreWhite,
    secondaryContainer = TagoreObsidianSurface,
    onSecondaryContainer = TagoreCyanPrimary,
    tertiary = TagoreSuccessGreen,
    background = TagoreBlack,
    onBackground = TagoreWhite,
    surface = TagoreObsidianSurface,
    onSurface = TagoreWhite,
    surfaceVariant = TagoreObsidianCard,
    onSurfaceVariant = TagoreMutedTextDark,
    outline = TagoreObsidianBorder,
    error = TagoreErrorRed,
    onError = TagoreWhite
)

private val TagoreLightColorScheme = lightColorScheme(
    primary = TagoreCyanLightPrimary,
    onPrimary = TagoreWhite,
    primaryContainer = TagoreLightCard,
    onPrimaryContainer = TagoreCyanLightPrimary,
    secondary = TagoreCyanLightAccent,
    onSecondary = TagoreWhite,
    secondaryContainer = TagoreLightCard,
    onSecondaryContainer = TagoreInkDark,
    tertiary = TagoreSuccessGreen,
    background = TagoreLightBackground,
    onBackground = TagoreInkDark,
    surface = TagoreLightSurface,
    onSurface = TagoreInkDark,
    surfaceVariant = TagoreLightCard,
    onSurfaceVariant = TagoreMutedTextLight,
    outline = TagoreLightBorder,
    error = TagoreErrorRed,
    onError = TagoreWhite
)

@Composable
fun TagoreTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TagoreDarkColorScheme else TagoreLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
