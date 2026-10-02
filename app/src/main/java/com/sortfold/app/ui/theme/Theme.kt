package com.sortfold.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sortfold.app.data.prefs.ThemeMode

// Brand: one restrained deep-teal accent, neutral surfaces everywhere else.
private val Teal = Color(0xFF0F6B5F)
private val TealDim = Color(0xFF66D9C4)
private val TealContainerLight = Color(0xFF9EF2E0)
private val TealOnContainerLight = Color(0xFF00201B)
private val TealContainerDark = Color(0xFF005046)
private val TealOnContainerDark = Color(0xFF83F5DE)
private val WarnAmber = Color(0xFF7A5900)
private val WarnAmberDark = Color(0xFFE9C24A)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = Color(0xFF4A635D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E0),
    onSecondaryContainer = Color(0xFF06201A),
    tertiary = WarnAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08D),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
)

private val DarkColors = darkColorScheme(
    primary = TealDim,
    onPrimary = Color(0xFF00382F),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = Color(0xFFB0CCC4),
    onSecondary = Color(0xFF1C352F),
    secondaryContainer = Color(0xFF334B45),
    onSecondaryContainer = Color(0xFFCCE8E0),
    tertiary = WarnAmberDark,
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5D4300),
    onTertiaryContainer = Color(0xFFFFE08D),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF88938F),
)

val LocalReducedMotion = staticCompositionLocalOf { false }

/** The one spacing scale. No other dp constant may be used for layout gaps. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Maximum readable content width on expanded layouts. */
    val maxContentWidth = 720.dp
}

private val SortfoldTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

private val SortfoldShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun SortfoldTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colors,
            typography = SortfoldTypography,
            shapes = SortfoldShapes,
            content = content,
        )
    }
}
