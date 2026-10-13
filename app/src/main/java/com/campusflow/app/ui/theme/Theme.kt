package com.campusflow.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusflow.app.data.Appearance
import com.campusflow.app.data.UserPreferences
import com.campusflow.app.data.Categories

private val LightColors = lightColorScheme(
    primary = Color(0xFF6842CC), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE2FF), onPrimaryContainer = Color(0xFF2C175C),
    secondary = Color(0xFF355DBC), secondaryContainer = Color(0xFFE4ECFF),
    onSecondaryContainer = Color(0xFF163064),
    tertiary = Color(0xFFAD385D), tertiaryContainer = Color(0xFFFFE2EC),
    onTertiaryContainer = Color(0xFF61152F),
    background = Color(0xFFF4F5FC), onBackground = Color(0xFF182036),
    surface = Color(0xFFF4F5FC), onSurface = Color(0xFF182036),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEBEEF8),
    surfaceContainer = Color(0xFFE4E8F4), surfaceContainerHigh = Color(0xFFDCE2F0),
    onSurfaceVariant = Color(0xFF59637B), outline = Color(0xFF74809A), outlineVariant = Color(0xFFD3D9EA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFAD95FF), onPrimary = Color(0xFF24104D),
    primaryContainer = Color(0xFF2B2250), onPrimaryContainer = Color(0xFFEDE5FF),
    secondary = Color(0xFF8BB4FF), secondaryContainer = Color(0xFF172A4C),
    onSecondaryContainer = Color(0xFFD8E6FF),
    tertiary = Color(0xFFFF8DAA), tertiaryContainer = Color(0xFF412034),
    onTertiaryContainer = Color(0xFFFFDDE7),
    background = Color(0xFF080D19), onBackground = Color(0xFFF0F2FF),
    surface = Color(0xFF080D19), onSurface = Color(0xFFF0F2FF),
    surfaceContainerLowest = Color(0xFF101727), surfaceContainerLow = Color(0xFF151D30),
    surfaceContainer = Color(0xFF1B2439), surfaceContainerHigh = Color(0xFF232D45),
    onSurfaceVariant = Color(0xFFAAB5CF), outline = Color(0xFF7C8AA8), outlineVariant = Color(0xFF2B3650),
    error = Color(0xFFFF98A5), errorContainer = Color(0xFF4A1C2A), onErrorContainer = Color(0xFFFFDDE3),
)

@Composable
fun CampusTheme(preferences: UserPreferences, content: @Composable () -> Unit) {
    val dark = when (preferences.appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.DARK -> true
        Appearance.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = if (preferences.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity = context as? android.app.Activity
        activity?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = colors.surface.luminance() > 0.5f
                isAppearanceLightNavigationBars = colors.surface.luminance() > 0.5f
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        ), content = content,
    )
}

/** Shared semantic accents; category identity remains clear in either appearance. */
data class KairosCategoryColors(val accent: Color, val container: Color, val content: Color)

@Composable
fun categoryColors(category: String): KairosCategoryColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = when (category) {
        Categories.UNIVERSITY -> if (dark) Color(0xFFAD95FF) else Color(0xFF6842CC)
        Categories.STUDY -> if (dark) Color(0xFF63DDB0) else Color(0xFF087C58)
        Categories.WORKOUT -> if (dark) Color(0xFFFF8DAA) else Color(0xFFAD385D)
        else -> if (dark) Color(0xFFFFBE75) else Color(0xFF96520E)
    }
    return KairosCategoryColors(accent, accent.copy(alpha = if (dark) 0.13f else 0.09f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest), MaterialTheme.colorScheme.onSurface)
}

@Composable
fun subjectAccent(id: Long): Color = when (Math.floorMod(id, 5L).toInt()) {
    0 -> MaterialTheme.colorScheme.secondary
    1 -> categoryColors(Categories.UNIVERSITY).accent
    2 -> categoryColors(Categories.STUDY).accent
    3 -> categoryColors(Categories.PERSONAL).accent
    else -> categoryColors(Categories.WORKOUT).accent
}

@Composable
fun preparationColor(state: String): Color = when (state) {
    "DOMINADO", "GREEN" -> categoryColors(Categories.STUDY).accent
    "PRACTICANDO", "YELLOW" -> categoryColors(Categories.PERSONAL).accent
    "RED" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
