package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppThemeDefinition(
    val id: String,
    val name: String,
    val subtitle: String,
    val primary: Color,
    val secondaryAccent: Color? = null,
    val deep: Color,
    val soft: Color,
    val bgTint: Color,
    val isMixed: Boolean = false
)

object GitThemes {
    // 6 Material Themes
    val EMERALD = AppThemeDefinition(
        id = "EMERALD",
        name = "Emerald Green",
        subtitle = "Material Emerald",
        primary = Color(0xFF0F9D74),
        secondaryAccent = null,
        deep = Color(0xFF0C7D5C),
        soft = Color(0xFFE7F6F1),
        bgTint = Color(0xFFF4FBF8),
        isMixed = false
    )

    val INDIGO = AppThemeDefinition(
        id = "INDIGO",
        name = "GitHub Indigo",
        subtitle = "Developer Blue",
        primary = Color(0xFF0969DA),
        secondaryAccent = null,
        deep = Color(0xFF064B9E),
        soft = Color(0xFFEAF2FE),
        bgTint = Color(0xFFF4F8FE),
        isMixed = false
    )

    val PURPLE = AppThemeDefinition(
        id = "PURPLE",
        name = "Royal Violet",
        subtitle = "Deep Material Purple",
        primary = Color(0xFF7C3AED),
        secondaryAccent = null,
        deep = Color(0xFF6325C7),
        soft = Color(0xFFF3ECFE),
        bgTint = Color(0xFFF8F5FE),
        isMixed = false
    )

    val AMBER = AppThemeDefinition(
        id = "AMBER",
        name = "Amber Gold",
        subtitle = "Warm Solar Gold",
        primary = Color(0xFFD97706),
        secondaryAccent = null,
        deep = Color(0xFFB45309),
        soft = Color(0xFFFEF3C7),
        bgTint = Color(0xFFFFFBEB),
        isMixed = false
    )

    val CRIMSON = AppThemeDefinition(
        id = "CRIMSON",
        name = "Ruby Crimson",
        subtitle = "Vivid Scarlet Red",
        primary = Color(0xFFDC2626),
        secondaryAccent = null,
        deep = Color(0xFFB91C1C),
        soft = Color(0xFFFEE2E2),
        bgTint = Color(0xFFFEF2F2),
        isMixed = false
    )

    val CYAN = AppThemeDefinition(
        id = "CYAN",
        name = "Teal Cyan",
        subtitle = "Pacific Cyan",
        primary = Color(0xFF0284C7),
        secondaryAccent = null,
        deep = Color(0xFF0369A1),
        soft = Color(0xFFE0F2FE),
        bgTint = Color(0xFFF0F9FF),
        isMixed = false
    )

    // 6 Mixed Accent Themes
    val CYBER_MINT = AppThemeDefinition(
        id = "CYBER_MINT",
        name = "Cyber Mint",
        subtitle = "Lime & Forest Mint",
        primary = Color(0xFF059669),
        secondaryAccent = Color(0xFF10B981),
        deep = Color(0xFF047857),
        soft = Color(0xFFD1FAE5),
        bgTint = Color(0xFFECFDF5),
        isMixed = true
    )

    val ELECTRIC_SUNSET = AppThemeDefinition(
        id = "ELECTRIC_SUNSET",
        name = "Electric Sunset",
        subtitle = "Coral & Sunset Orange",
        primary = Color(0xFFEA580C),
        secondaryAccent = Color(0xFFF43F5E),
        deep = Color(0xFFC2410C),
        soft = Color(0xFFFFEDD5),
        bgTint = Color(0xFFFFF7ED),
        isMixed = true
    )

    val COSMIC_BERRY = AppThemeDefinition(
        id = "COSMIC_BERRY",
        name = "Cosmic Berry",
        subtitle = "Rose & Purple Berry",
        primary = Color(0xFFE11D48),
        secondaryAccent = Color(0xFF9333EA),
        deep = Color(0xFFBE123C),
        soft = Color(0xFFFFE4E6),
        bgTint = Color(0xFFFFF1F2),
        isMixed = true
    )

    val HYPER_WAVE = AppThemeDefinition(
        id = "HYPER_WAVE",
        name = "Hyper Wave",
        subtitle = "Ultramarine & Sky Cyan",
        primary = Color(0xFF2563EB),
        secondaryAccent = Color(0xFF06B6D4),
        deep = Color(0xFF1D4ED8),
        soft = Color(0xFFDBEAFE),
        bgTint = Color(0xFFEFF6FF),
        isMixed = true
    )

    val AURORA_NEON = AppThemeDefinition(
        id = "AURORA_NEON",
        name = "Aurora Glow",
        subtitle = "Violet & Neon Fuchsia",
        primary = Color(0xFF8B5CF6),
        secondaryAccent = Color(0xFFEC4899),
        deep = Color(0xFF7C3AED),
        soft = Color(0xFFEDE9FE),
        bgTint = Color(0xFFF5F3FF),
        isMixed = true
    )

    val SLATE_TITANIUM = AppThemeDefinition(
        id = "SLATE_TITANIUM",
        name = "Slate Obsidian",
        subtitle = "Titanium & Steel Blue",
        primary = Color(0xFF475569),
        secondaryAccent = Color(0xFF64748B),
        deep = Color(0xFF334155),
        soft = Color(0xFFE2E8F0),
        bgTint = Color(0xFFF8FAFC),
        isMixed = true
    )

    val materialThemes = listOf(EMERALD, INDIGO, PURPLE, AMBER, CRIMSON, CYAN)
    val mixedAccentThemes = listOf(CYBER_MINT, ELECTRIC_SUNSET, COSMIC_BERRY, HYPER_WAVE, AURORA_NEON, SLATE_TITANIUM)
    val allThemes = materialThemes + mixedAccentThemes

    fun getThemeById(id: String?): AppThemeDefinition {
        return allThemes.find { it.id.equals(id, ignoreCase = true) } ?: EMERALD
    }
}

val LocalGitTheme = staticCompositionLocalOf { GitThemes.EMERALD }
val LocalThemeBgTintEnabled = staticCompositionLocalOf { false }

object GitTheme {
    val current: AppThemeDefinition
        @Composable
        @ReadOnlyComposable
        get() = LocalGitTheme.current

    val isBgTintEnabled: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeBgTintEnabled.current
}
