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
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusflow.app.data.Appearance
import com.campusflow.app.data.UserPreferences

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D6655), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEDE1), onPrimaryContainer = Color(0xFF213D30),
    secondary = Color(0xFF596880), secondaryContainer = Color(0xFFE3EAF5),
    tertiary = Color(0xFF876242), tertiaryContainer = Color(0xFFF7E5D4),
    background = Color(0xFFF6F8F4), onBackground = Color(0xFF202923),
    surface = Color(0xFFF6F8F4), onSurface = Color(0xFF202923),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF2EC),
    surfaceContainer = Color(0xFFE9EEE7), surfaceContainerHigh = Color(0xFFE2E8E0),
    onSurfaceVariant = Color(0xFF647067), outlineVariant = Color(0xFFD9E0D7),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4D0B6), onPrimary = Color(0xFF103826),
    primaryContainer = Color(0xFF2C4D3D), onPrimaryContainer = Color(0xFFD6EDDE),
    secondary = Color(0xFFB8C8E4), secondaryContainer = Color(0xFF2F4057),
    tertiary = Color(0xFFE7BE98), tertiaryContainer = Color(0xFF523D2C),
    background = Color(0xFF111713), onBackground = Color(0xFFE2E9E0),
    surface = Color(0xFF111713), onSurface = Color(0xFFE2E9E0),
    surfaceContainerLowest = Color(0xFF191F1B), surfaceContainerLow = Color(0xFF1E2620),
    surfaceContainer = Color(0xFF232C25), surfaceContainerHigh = Color(0xFF2D372F),
    onSurfaceVariant = Color(0xFFADB9AE), outlineVariant = Color(0xFF354238),
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
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
            headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        ), content = content,
    )
}
