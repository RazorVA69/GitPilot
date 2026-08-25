package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// GitPilot Light Modern Palette
val GitBg = Color(0xFFFFFFFF)
val GitSurface = Color(0xFFFFFFFF)
val GitSurface2 = Color(0xFFF8FAFC)
val GitSurface3 = Color(0xFFEEF2F6)
val GitBorder = Color(0x140F0F19) // rgba(15,15,25,.08) hairline
val GitBorderStrong = Color(0x240F0F19) // rgba(15,15,25,.14) border

val GitText1 = Color(0xFF14141B) // Primary text
val GitText2 = Color(0xFF63636F) // Secondary text
val GitText3 = Color(0xFF98979F) // Tertiary / Muted text
val GitButtonPrimary = Color(0xFF14141B) // Solid near-black primary button fill
val GitTopBarButtonBg = Color(0xFFEEF1F5) // Soft circular action button fill

// Dynamic Accent & Background according to the selected Theme
val GitAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = GitTheme.current.primary

val GitAccentDeep: Color
    @Composable
    @ReadOnlyComposable
    get() = GitTheme.current.deep

val GitAccentSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = GitTheme.current.soft

val GitAccentSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = GitTheme.current.secondaryAccent ?: GitTheme.current.primary

val GitAppBg: Color
    @Composable
    @ReadOnlyComposable
    get() = if (GitTheme.isBgTintEnabled) GitTheme.current.bgTint else GitBg

val GitYellow = Color(0xFFD4A017)

val GitSyntaxBool = Color(0xFFB5701F)
val GitSyntaxProp = Color(0xFF3A6FB0)
val GitSyntaxFn: Color
    @Composable
    @ReadOnlyComposable
    get() = GitTheme.current.primary
val GitSyntaxKw = Color(0xFF63636F)
val GitSyntaxComment = Color(0xFF98979F)

// Backwards-compatible MD3 tokens
val Md3LightBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = GitAppBg

val Md3LightSurface = GitSurface
val Md3LightSurfaceVariant = GitSurface2
val Md3LightSurfaceContainerHigh = GitSurface3
val Md3LightOutline = GitBorderStrong
val Md3LightOutlineVariant = GitBorder

val Md3LightPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = GitAccent

val Md3LightPrimaryContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = GitAccentSoft

val Md3LightOnPrimaryContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = GitAccentDeep

val Md3LightSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = GitAccentDeep

val Md3LightSecondaryContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = GitAccentSoft

val Md3LightOnSecondaryContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = GitAccentDeep

val Md3LightTertiary = Color(0xFF3A6FB0)
val Md3LightTertiaryContainer = Color(0xFFE6F0FA)
val Md3LightOnTertiaryContainer = Color(0xFF1E4B82)

val Md3LightTextPrimary = GitText1
val Md3LightTextSecondary = GitText2
val Md3LightTextTertiary = GitText3

val Md3LightError = Color(0xFFDC2626)
val Md3LightErrorContainer = Color(0xFFFEE2E2)
val Md3LightWarning = Color(0xFFD97706)
val Md3LightWarningContainer = Color(0xFFFEF3C7)

val Md3LightCodeBg = GitSurface2
val Md3LightCodeGutter = GitSurface3
val Md3LightCodeBorder = GitBorder

// Semantic Accents
val GitHubBlue = Color(0xFF0969DA)
val GitHubGreen = Color(0xFF0F9D74)
val GitHubPurple = Color(0xFF7C3AED)
val GitHubOrange = Color(0xFFD97706)
val GitHubYellow = Color(0xFFD4A017)
val GitHubRed = Color(0xFFDC2626)
val GitHubTeal = Color(0xFF0F9D74)


