package com.taiful.tfvoicetyper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A colour theme the user can pick in Settings. */
data class Palette(
    val id: String,
    val nameBn: String,
    val primary: Color,
    val primaryOnDark: Color,
    val accent: Color,
)

val Palettes = listOf(
    Palette("emerald", "সবুজ", Color(0xFF0E9F78), Color(0xFF4FD6A8), Color(0xFF0B6E8A)),
    Palette("ocean", "নীল", Color(0xFF1E63E9), Color(0xFF8AB4FF), Color(0xFF0891B2)),
    Palette("royal", "বেগুনি", Color(0xFF6B4EFF), Color(0xFFB9A8FF), Color(0xFFC026D3)),
    Palette("sunset", "কমলা", Color(0xFFE8590C), Color(0xFFFFA06A), Color(0xFFDC2626)),
    Palette("rose", "গোলাপি", Color(0xFFD6336C), Color(0xFFFF8FB4), Color(0xFF9333EA)),
    Palette("maroon", "মেরুন", Color(0xFF9F1239), Color(0xFFF49AB0), Color(0xFFB45309)),
    Palette("teal", "ফিরোজা", Color(0xFF0F8B9C), Color(0xFF67D5E2), Color(0xFF2563EB)),
    Palette("slate", "ধূসর", Color(0xFF3B4A5E), Color(0xFFC3CFDD), Color(0xFF0EA5E9)),
)

fun paletteOf(id: String): Palette = Palettes.firstOrNull { it.id == id } ?: Palettes.first()

data class Brand(val palette: Palette, val dark: Boolean) {
    val main: Color get() = if (dark) palette.primaryOnDark else palette.primary
    val gradient: List<Color> get() = listOf(palette.primary, palette.accent)
}

val LocalBrand = staticCompositionLocalOf { Brand(Palettes.first(), false) }

private fun lightScheme(p: Palette): ColorScheme {
    val bg = lerp(Color.White, p.primary, 0.035f)
    return lightColorScheme(
        primary = p.primary,
        onPrimary = Color.White,
        primaryContainer = lerp(Color.White, p.primary, 0.14f),
        onPrimaryContainer = lerp(p.primary, Color.Black, 0.55f),
        secondary = p.accent,
        onSecondary = Color.White,
        secondaryContainer = lerp(Color.White, p.accent, 0.13f),
        onSecondaryContainer = lerp(p.accent, Color.Black, 0.55f),
        tertiary = p.accent,
        background = bg,
        onBackground = Color(0xFF15191C),
        surface = Color.White,
        onSurface = Color(0xFF15191C),
        surfaceVariant = lerp(Color.White, p.primary, 0.07f),
        onSurfaceVariant = Color(0xFF4A5258),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = lerp(Color.White, p.primary, 0.025f),
        surfaceContainer = lerp(Color.White, p.primary, 0.045f),
        surfaceContainerHigh = lerp(Color.White, p.primary, 0.065f),
        surfaceContainerHighest = lerp(Color.White, p.primary, 0.085f),
        outline = lerp(Color(0xFF9AA3A8), p.primary, 0.15f),
        outlineVariant = lerp(Color(0xFFDDE2E4), p.primary, 0.08f),
        error = Color(0xFFC62828),
    )
}

private fun darkScheme(p: Palette): ColorScheme {
    val base = Color(0xFF0E1214)
    return darkColorScheme(
        primary = p.primaryOnDark,
        onPrimary = lerp(p.primary, Color.Black, 0.7f),
        primaryContainer = lerp(p.primary, base, 0.55f),
        onPrimaryContainer = lerp(p.primaryOnDark, Color.White, 0.6f),
        secondary = lerp(p.accent, Color.White, 0.35f),
        onSecondary = Color.Black,
        secondaryContainer = lerp(p.accent, base, 0.6f),
        onSecondaryContainer = lerp(p.accent, Color.White, 0.75f),
        tertiary = lerp(p.accent, Color.White, 0.35f),
        background = lerp(base, p.primary, 0.03f),
        onBackground = Color(0xFFE6EAEC),
        surface = lerp(Color(0xFF161B1E), p.primary, 0.03f),
        onSurface = Color(0xFFE6EAEC),
        surfaceVariant = lerp(Color(0xFF20272B), p.primary, 0.06f),
        onSurfaceVariant = Color(0xFFB4BDC2),
        surfaceContainerLowest = lerp(base, p.primary, 0.01f),
        surfaceContainerLow = lerp(Color(0xFF14191C), p.primary, 0.03f),
        surfaceContainer = lerp(Color(0xFF181E21), p.primary, 0.04f),
        surfaceContainerHigh = lerp(Color(0xFF1D2427), p.primary, 0.05f),
        surfaceContainerHighest = lerp(Color(0xFF232B2F), p.primary, 0.06f),
        outline = Color(0xFF6B757A),
        outlineVariant = Color(0xFF353E42),
        error = Color(0xFFFF8A80),
    )
}

/** Tight but comfortable line spacing so more text fits on a phone screen (Bangla needs ~1.35×). */
private val CompactType = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.5.sp, letterSpacing = 0.1.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.5.sp, letterSpacing = 0.1.sp),
        bodySmall = t.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
        labelLarge = t.labelLarge.copy(fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
        labelSmall = t.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
    )
}

/** Style for the long transcript text (compact). */
val TranscriptStyle = TextStyle(fontSize = 15.5.sp, lineHeight = 21.sp)

@Composable
fun TFTheme(themeId: String, darkMode: Int, content: @Composable () -> Unit) {
    val dark = when (darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = paletteOf(themeId)
    CompositionLocalProvider(LocalBrand provides Brand(p, dark)) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(p) else lightScheme(p),
            typography = CompactType,
            content = content,
        )
    }
}
