package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GitHubBlue,
    onPrimary = Color.Black,
    primaryContainer = GitHubDarkSurfaceVariant,
    onPrimaryContainer = GitHubBlueLight,
    secondary = GitHubGreenBright,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1E3A28),
    onSecondaryContainer = GitHubGreenMint,
    tertiary = GitHubPurple,
    onTertiary = Color.Black,
    background = GitHubDarkBg,
    onBackground = GitHubDarkTextPrimary,
    surface = GitHubDarkSurface,
    onSurface = GitHubDarkTextPrimary,
    surfaceVariant = GitHubDarkSurfaceVariant,
    onSurfaceVariant = GitHubDarkTextSecondary,
    outline = GitHubDarkBorder,
    outlineVariant = Color(0xFF21262D),
    error = GitHubRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = GitHubLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF0969DA),
    secondary = GitHubGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAFBE1),
    onSecondaryContainer = Color(0xFF1A7F37),
    tertiary = Color(0xFF8250DF),
    onTertiary = Color.White,
    background = GitHubLightBg,
    onBackground = GitHubLightTextPrimary,
    surface = GitHubLightSurface,
    onSurface = GitHubLightTextPrimary,
    surfaceVariant = GitHubLightSurfaceVariant,
    onSurfaceVariant = GitHubLightTextSecondary,
    outline = GitHubLightBorder,
    outlineVariant = Color(0xFFE1E4E8),
    error = Color(0xFFCF222E),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep signature GitHub brand identity
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

