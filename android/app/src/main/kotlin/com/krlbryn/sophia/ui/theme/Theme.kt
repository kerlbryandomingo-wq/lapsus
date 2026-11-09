package com.krlbryn.sophia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krlbryn.sophia.data.settings.AccentColor
import com.krlbryn.sophia.data.settings.ThemeMode

/** Brand colours sampled from the AMA logo, shared with Lapsus for Windows' tokens.css. */
object Brand {
    val Maroon = Color(0xFF7A1B1B)
    val MaroonDeep = Color(0xFF5E1414)
    val MaroonBright = Color(0xFF8A2424)
    val Navy = Color(0xFF2A367D)
    val Gold = Color(0xFFE7A323)
    val OnGold = Color(0xFF3D2900)
}

/** Colours outside the Material scheme that Sophia components need. */
@Immutable
data class SophiaExtraColors(
    val success: Color,
    val warning: Color,
    val warningContainer: Color,
    val ai: Color,
    val aiContainer: Color,
)

val LocalSophiaColors = staticCompositionLocalOf {
    SophiaExtraColors(Color(0xFF1D6F47), Color(0xFF8F5200), Color(0xFFFDF0DC), Color(0xFF2A367D), Color(0xFFEEF0FA))
}

private fun lightScheme(accent: AccentColor): ColorScheme {
    val base = lightColorScheme(
        primary = Brand.Maroon,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF6E9E7),
        onPrimaryContainer = Color(0xFF561111),
        secondary = Brand.Navy,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9EBF6),
        onSecondaryContainer = Color(0xFF1C2459),
        tertiary = Color(0xFF8F5B00),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFBF0D9),
        onTertiaryContainer = Brand.OnGold,
        background = Color(0xFFF7F5F2),
        onBackground = Color(0xFF1F1B19),
        surface = Color(0xFFF7F5F2),
        onSurface = Color(0xFF1F1B19),
        surfaceVariant = Color(0xFFEBE7E2),
        onSurfaceVariant = Color(0xFF645D57),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFBFAF8),
        surfaceContainer = Color(0xFFF2EFEB),
        surfaceContainerHigh = Color(0xFFEDE9E4),
        surfaceContainerHighest = Color(0xFFE7E2DC),
        outline = Color(0xFFCFC7BE),
        outlineVariant = Color(0xFFE5DFD8),
        error = Color(0xFFB0261D),
        onError = Color.White,
        errorContainer = Color(0xFFFBE7E5),
        onErrorContainer = Color(0xFF5C0E09),
    )
    return when (accent) {
        AccentColor.MAROON -> base
        AccentColor.NAVY -> base.copy(
            primary = Brand.Navy,
            primaryContainer = Color(0xFFE9EBF6),
            onPrimaryContainer = Color(0xFF1C2459),
        )
        AccentColor.GOLD -> base.copy(
            primary = Color(0xFF8F5B00),
            primaryContainer = Color(0xFFFBF0D9),
            onPrimaryContainer = Brand.OnGold,
        )
    }
}

private fun darkScheme(accent: AccentColor): ColorScheme {
    val base = darkColorScheme(
        primary = Color(0xFFF0AAA3),
        onPrimary = Color(0xFF561111),
        primaryContainer = Color(0xFF8C2626),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFB2BCF3),
        onSecondary = Color(0xFF1C2459),
        secondaryContainer = Color(0xFF3A4794),
        onSecondaryContainer = Color.White,
        tertiary = Brand.Gold,
        onTertiary = Brand.OnGold,
        tertiaryContainer = Color(0xFF3B2F17),
        onTertiaryContainer = Color(0xFFF7DDA6),
        background = Color(0xFF141214),
        onBackground = Color(0xFFF3EEEA),
        surface = Color(0xFF141214),
        onSurface = Color(0xFFF3EEEA),
        surfaceVariant = Color(0xFF2C272C),
        onSurfaceVariant = Color(0xFFBCB2AB),
        surfaceContainerLowest = Color(0xFF0F0D0F),
        surfaceContainerLow = Color(0xFF1D1A1D),
        surfaceContainer = Color(0xFF221E22),
        surfaceContainerHigh = Color(0xFF2A252A),
        surfaceContainerHighest = Color(0xFF332D33),
        outline = Color(0xFF4A424A),
        outlineVariant = Color(0xFF363036),
        error = Color(0xFFFF9B92),
        onError = Color(0xFF5C0E09),
        errorContainer = Color(0xFF3D1F1D),
        onErrorContainer = Color(0xFFFFDAD5),
    )
    return when (accent) {
        AccentColor.MAROON -> base
        AccentColor.NAVY -> base.copy(primary = Color(0xFFB2BCF3), onPrimary = Color(0xFF1C2459), primaryContainer = Color(0xFF3A4794))
        AccentColor.GOLD -> base.copy(primary = Brand.Gold, onPrimary = Brand.OnGold, primaryContainer = Color(0xFF6B4A0E))
    }
}

private val SophiaTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val Eyebrow = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)

private val SophiaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun SophiaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.MAROON,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extra = if (dark) {
        SophiaExtraColors(Color(0xFF7FD1A6), Color(0xFFF2BD6B), Color(0xFF3A2C16), Color(0xFFB2BCF3), Color(0xFF22263D))
    } else {
        SophiaExtraColors(Color(0xFF1D6F47), Color(0xFF8F5200), Color(0xFFFDF0DC), Color(0xFF2A367D), Color(0xFFEEF0FA))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSophiaColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(accent) else lightScheme(accent),
            typography = SophiaTypography,
            shapes = SophiaShapes,
            content = content,
        )
    }
}

/** True when the active Sophia theme is dark; used for status bar icon colours. */
@Composable
fun isSophiaDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
