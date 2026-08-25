package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    themeId: String = "EMERALD",
    isBgTintEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val themeDef = GitThemes.getThemeById(themeId)
    val appBackground = if (isBgTintEnabled) themeDef.bgTint else GitBg

    val dynamicColorScheme = lightColorScheme(
        primary = themeDef.primary,
        onPrimary = Color.White,
        primaryContainer = themeDef.soft,
        onPrimaryContainer = themeDef.deep,

        secondary = themeDef.secondaryAccent ?: themeDef.deep,
        onSecondary = Color.White,
        secondaryContainer = themeDef.soft,
        onSecondaryContainer = themeDef.deep,

        tertiary = themeDef.secondaryAccent ?: Color(0xFF3A6FB0),
        onTertiary = Color.White,
        tertiaryContainer = themeDef.soft,
        onTertiaryContainer = themeDef.deep,

        background = appBackground,
        onBackground = Md3LightTextPrimary,

        surface = GitSurface,
        onSurface = Md3LightTextPrimary,
        surfaceVariant = GitSurface2,
        onSurfaceVariant = Md3LightTextSecondary,

        outline = Md3LightOutline,
        outlineVariant = Md3LightOutlineVariant,

        error = Md3LightError,
        onError = Color.White,
        errorContainer = Md3LightErrorContainer,
        onErrorContainer = Md3LightError
    )

    CompositionLocalProvider(
        LocalGitTheme provides themeDef,
        LocalThemeBgTintEnabled provides isBgTintEnabled
    ) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = Typography,
            content = content
        )
    }
}

