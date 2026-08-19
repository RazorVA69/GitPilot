package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BeautifulLightColorScheme = lightColorScheme(
    primary = Md3LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Md3LightPrimaryContainer,
    onPrimaryContainer = Md3LightOnPrimaryContainer,

    secondary = Md3LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Md3LightSecondaryContainer,
    onSecondaryContainer = Md3LightOnSecondaryContainer,

    tertiary = Md3LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Md3LightTertiaryContainer,
    onTertiaryContainer = Md3LightOnTertiaryContainer,

    background = Md3LightBackground,
    onBackground = Md3LightTextPrimary,

    surface = Md3LightSurface,
    onSurface = Md3LightTextPrimary,
    surfaceVariant = Md3LightSurfaceVariant,
    onSurfaceVariant = Md3LightTextSecondary,

    outline = Md3LightOutline,
    outlineVariant = Md3LightOutlineVariant,

    error = Md3LightError,
    onError = Color.White,
    errorContainer = Md3LightErrorContainer,
    onErrorContainer = Md3LightError
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Default to gorgeous Light MD3
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BeautifulLightColorScheme,
        typography = Typography,
        content = content
    )
}
