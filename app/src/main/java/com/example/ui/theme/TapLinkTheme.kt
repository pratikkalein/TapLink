package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** User-selectable theme preference exposed in Settings. */
enum class ThemeMode { LIGHT, AUTO, DARK }

/**
 * The bespoke "Wallet Stack" (TapLink M3 Redesign) palette. Material's stock color
 * roles don't map cleanly onto this design, so every semantic color the mockup uses
 * is spelled out here with explicit light and dark values.
 */
@Immutable
data class TapLinkColors(
    val isDark: Boolean,
    val background: Color,
    val display: Color,
    val bodyMuted: Color,
    val iconBtnBg: Color,
    val iconBtnTint: Color,
    // Home filter chips
    val chipSelBg: Color,
    val chipSelText: Color,
    val chipBorder: Color,
    val chipText: Color,
    // Surface (top) card
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val liveBg: Color,
    val live: Color,
    // Accent card
    val accentCard: Color,
    val onAccentCard: Color,
    val onAccentCardMuted: Color,
    // "New tag" / add card + primary action button dot
    val tagAddCard: Color,
    val onTagAddCard: Color,
    val addBtnBg: Color,
    val addBtnIcon: Color,
    // Bottom sheet + form fields
    val scrim: Color,
    val sheet: Color,
    val handle: Color,
    val fieldActiveBorder: Color,
    val fieldBorder: Color,
    val fieldLabel: Color,
    val fieldValue: Color,
    val fieldPlaceholder: Color,
    // Buttons / controls
    val primaryBtn: Color,
    val onPrimaryBtn: Color,
    val danger: Color,
    val segBg: Color,
    val segSelBg: Color,
    val segSelText: Color,
    val segText: Color,
    val toggleOn: Color,
    val toggleThumb: Color,
    val footer: Color,
    val cancel: Color,
    // Broadcasting / QR
    val pulse: Color,
    val barSurface: Color,
    val onBarSurface: Color,
)

private val LightTapLink = TapLinkColors(
    isDark = false,
    background = Color(0xFFDBE1FF),
    display = Color(0xFF001945),
    bodyMuted = Color(0xFF324476),
    iconBtnBg = Color(0x14001945),
    iconBtnTint = Color(0xFF001945),
    chipSelBg = Color(0xFF001945),
    chipSelText = Color(0xFFDBE1FF),
    chipBorder = Color(0x59001945),
    chipText = Color(0xFF001945),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceMuted = Color(0xFF8F909A),
    liveBg = Color(0xFFCDEEC4),
    live = Color(0xFF1D6B33),
    accentCard = Color(0xFF4A5C92),
    onAccentCard = Color(0xFFFFFFFF),
    onAccentCardMuted = Color(0xFFC6D0F2),
    tagAddCard = Color(0xFF1B1B1F),
    onTagAddCard = Color(0xFFFAF9FD),
    addBtnBg = Color(0xFFB4C4FF),
    addBtnIcon = Color(0xFF17295E),
    scrim = Color(0x73001945),
    sheet = Color(0xFFFAF9FD),
    handle = Color(0xFFC5C6D0),
    fieldActiveBorder = Color(0xFF4A5C92),
    fieldBorder = Color(0xFFC5C6D0),
    fieldLabel = Color(0xFF66676F),
    fieldValue = Color(0xFF1B1B1F),
    fieldPlaceholder = Color(0xFF8F909A),
    primaryBtn = Color(0xFF1B1B1F),
    onPrimaryBtn = Color(0xFFFAF9FD),
    danger = Color(0xFF93000A),
    segBg = Color(0xFFE1E2EC),
    segSelBg = Color(0xFF4A5C92),
    segSelText = Color(0xFFFFFFFF),
    segText = Color(0xFF66676F),
    toggleOn = Color(0xFF4A5C92),
    toggleThumb = Color(0xFFFFFFFF),
    footer = Color(0xFF324476),
    cancel = Color(0xFF4A5C92),
    pulse = Color(0xFF4A5C92),
    barSurface = Color(0xFFFAF9FD),
    onBarSurface = Color(0xFF4A5C92),
)

private val DarkTapLink = TapLinkColors(
    isDark = true,
    background = Color(0xFF121316),
    display = Color(0xFFB4C4FF),
    bodyMuted = Color(0xFF8F909A),
    iconBtnBg = Color(0xFF1E1F23),
    iconBtnTint = Color(0xFFC5C6D0),
    chipSelBg = Color(0xFFB4C4FF),
    chipSelText = Color(0xFF17295E),
    chipBorder = Color(0xFF45464F),
    chipText = Color(0xFFC5C6D0),
    surface = Color(0xFF292A2E),
    onSurface = Color(0xFFE4E2E6),
    onSurfaceMuted = Color(0xFF8F909A),
    liveBg = Color(0xFF12372B),
    live = Color(0xFF6DD58C),
    accentCard = Color(0xFF324476),
    onAccentCard = Color(0xFFDBE1FF),
    onAccentCardMuted = Color(0xFFB4C4FF),
    tagAddCard = Color(0xFFB4C4FF),
    onTagAddCard = Color(0xFF17295E),
    addBtnBg = Color(0xFF17295E),
    addBtnIcon = Color(0xFFB4C4FF),
    scrim = Color(0x99000000),
    sheet = Color(0xFF1E1F23),
    handle = Color(0xFF45464F),
    fieldActiveBorder = Color(0xFFB4C4FF),
    fieldBorder = Color(0xFF45464F),
    fieldLabel = Color(0xFFC5C6D0),
    fieldValue = Color(0xFFE4E2E6),
    fieldPlaceholder = Color(0xFF8F909A),
    primaryBtn = Color(0xFFB4C4FF),
    onPrimaryBtn = Color(0xFF17295E),
    danger = Color(0xFFFFB4AB),
    segBg = Color(0xFF1E1F23),
    segSelBg = Color(0xFFB4C4FF),
    segSelText = Color(0xFF17295E),
    segText = Color(0xFFC5C6D0),
    toggleOn = Color(0xFFB4C4FF),
    toggleThumb = Color(0xFF17295E),
    footer = Color(0xFF8F909A),
    cancel = Color(0xFFB4C4FF),
    pulse = Color(0xFF324476),
    barSurface = Color(0xFF1E1F23),
    onBarSurface = Color(0xFFB4C4FF),
)

val LocalTapLinkColors = staticCompositionLocalOf { LightTapLink }

/** Convenience accessor: `TapLinkTheme.colors` inside any composable under [TapLinkTheme]. */
object TapLinkTheme {
    val colors: TapLinkColors
        @Composable get() = LocalTapLinkColors.current
}

/**
 * Maps a Material You dynamic [ColorScheme] onto the bespoke [TapLinkColors] roles so the
 * "Wallet Stack" design takes on the user's wallpaper palette while keeping its structure.
 * Success (LIVE) green stays fixed — it isn't part of the wallpaper palette.
 */
private fun dynamicTapLinkColors(c: ColorScheme, dark: Boolean): TapLinkColors {
    val liveBg = if (dark) Color(0xFF12372B) else Color(0xFFCDEEC4)
    val live = if (dark) Color(0xFF6DD58C) else Color(0xFF1D6B33)
    return TapLinkColors(
        isDark = dark,
        background = c.background,
        display = c.primary,
        bodyMuted = c.onSurfaceVariant,
        iconBtnBg = c.surfaceContainerHighest,
        iconBtnTint = c.onSurfaceVariant,
        chipSelBg = c.primary,
        chipSelText = c.onPrimary,
        chipBorder = c.outline,
        chipText = c.onSurfaceVariant,
        // Elevated container (not `surface`) so the neutral card stays distinct from
        // the background — in dynamic dark, `surface` and `background` are near-identical.
        surface = c.surfaceContainerHigh,
        onSurface = c.onSurface,
        onSurfaceMuted = c.onSurfaceVariant,
        liveBg = liveBg,
        live = live,
        accentCard = c.primary,
        onAccentCard = c.onPrimary,
        onAccentCardMuted = c.onPrimary.copy(alpha = 0.75f),
        tagAddCard = c.inverseSurface,
        onTagAddCard = c.inverseOnSurface,
        addBtnBg = c.primaryContainer,
        addBtnIcon = c.onPrimaryContainer,
        scrim = c.scrim.copy(alpha = 0.45f),
        sheet = c.surfaceContainerLow,
        handle = c.outlineVariant,
        fieldActiveBorder = c.primary,
        fieldBorder = c.outline,
        fieldLabel = c.onSurfaceVariant,
        fieldValue = c.onSurface,
        fieldPlaceholder = c.onSurfaceVariant.copy(alpha = 0.6f),
        primaryBtn = c.primary,
        onPrimaryBtn = c.onPrimary,
        danger = c.error,
        segBg = c.surfaceContainerHighest,
        segSelBg = c.primary,
        segSelText = c.onPrimary,
        segText = c.onSurfaceVariant,
        toggleOn = c.primary,
        toggleThumb = c.onPrimary,
        footer = c.onSurfaceVariant,
        cancel = c.primary,
        pulse = c.primary,
        barSurface = c.surfaceContainerHigh,
        onBarSurface = c.primary,
    )
}

private fun schemeFrom(c: TapLinkColors) =
    if (c.isDark) {
        darkColorScheme(
            primary = c.primaryBtn,
            onPrimary = c.onPrimaryBtn,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.sheet,
            onSurface = c.onSurface,
            error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.primaryBtn,
            onPrimary = c.onPrimaryBtn,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.sheet,
            onSurface = c.onSurface,
            error = c.danger,
        )
    }

@Composable
fun TapLinkTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = if (useDynamic) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        schemeFrom(if (dark) DarkTapLink else LightTapLink)
    }

    // The whole UI paints from the bespoke TapLinkColors palette, so to actually
    // honor wallpaper colors we rebuild that palette from the dynamic scheme.
    val tapColors = if (useDynamic) {
        dynamicTapLinkColors(colorScheme, dark)
    } else {
        if (dark) DarkTapLink else LightTapLink
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalTapLinkColors provides tapColors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
