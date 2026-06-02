package com.example.audiobooks.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens ported from the Claude Design prototype (styles.css).
 * Warm-paper light theme + matching dark theme. oklch values were converted to
 * sRGB approximations. The accent is the brand orange (#c4703a / dark #d98a55).
 */
internal data class AppColors(
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val hairline: Color,
    val hairline2: Color,
    val accent: Color,
    val accentInk: Color,
    val accentWash: Color,
    val isDark: Boolean
)

internal val LightColors = AppColors(
    bg = Color(0xFFF6F3ED),
    bg2 = Color(0xFFEEEAE1),
    surface = Color(0xFFFEFCF8),
    surface2 = Color(0xFFF3EFE8),
    ink = Color(0xFF2E2A23),
    ink2 = Color(0xFF5F584E),
    ink3 = Color(0xFF8C857A),
    hairline = Color(0xFFE6E1D8),
    hairline2 = Color(0xFFD5CEC2),
    accent = Color(0xFFC4703A),
    accentInk = Color(0xFFFFFFFF),
    accentWash = Color(0xFFF4E6DB),
    isDark = false
)

internal val DarkColors = AppColors(
    bg = Color(0xFF211E19),
    bg2 = Color(0xFF28241F),
    surface = Color(0xFF2C2823),
    surface2 = Color(0xFF353029),
    ink = Color(0xFFECE6DB),
    ink2 = Color(0xFFBBB3A6),
    ink3 = Color(0xFF8B8276),
    hairline = Color(0xFF3D3832),
    hairline2 = Color(0xFF4B443C),
    accent = Color(0xFFD98A55),
    accentInk = Color(0xFF1B1813),
    accentWash = Color(0xFF463A2D),
    isDark = true
)

internal val LocalAppColors = staticCompositionLocalOf { LightColors }

internal data class AppFonts(val base: FontFamily, val serif: FontFamily)

internal val LocalAppFonts = staticCompositionLocalOf { AppFonts(FontFamily.SansSerif, FontFamily.Serif) }

/** Fixed layout tokens (prototype "soft" radius + "regular" density). */
internal object Dim {
    val pad = 20.dp
    val gap = 14.dp
    val rS = 10.dp
    val rM = 16.dp
    val rL = 24.dp
}

/** Text styles mirroring styles.css typography. */
internal object AppText {
    val eyebrow: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalAppFonts.current.base,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
            color = LocalAppColors.current.ink3
        )
    val h1: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalAppFonts.current.serif,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp,
            color = LocalAppColors.current.ink
        )
    val h2: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalAppFonts.current.serif,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 25.sp,
            color = LocalAppColors.current.ink
        )
    val title: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalAppFonts.current.base,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp,
            color = LocalAppColors.current.ink
        )
    val body: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalAppFonts.current.base,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 22.sp,
            color = LocalAppColors.current.ink2
        )
    val meta: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalAppFonts.current.base,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = LocalAppColors.current.ink3
        )
}

@Composable
internal fun AudioBooksTheme(
    dark: Boolean,
    serifBase: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (dark) DarkColors else LightColors
    val fonts = AppFonts(
        base = if (serifBase) FontFamily.Serif else FontFamily.SansSerif,
        serif = FontFamily.Serif
    )
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentInk,
            secondary = colors.accent,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.ink2,
            outline = colors.hairline2,
            secondaryContainer = colors.accentWash
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentInk,
            secondary = colors.accent,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.ink2,
            outline = colors.hairline2,
            secondaryContainer = colors.accentWash
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppFonts provides fonts
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
