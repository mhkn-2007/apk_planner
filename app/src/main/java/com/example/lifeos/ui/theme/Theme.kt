package com.example.lifeos.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

private val DarkGlassmorphismColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = GlassAccent,
    onPrimaryContainer = TextPrimary,
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = GlassSecondaryDark,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentAmber,
    onTertiary = Color.Black,
    background = GlassPrimaryDark,
    onBackground = TextPrimary,
    surface = GlassSecondaryDark,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurfaceMedium,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    onError = Color.White
)

private val LightGlassmorphismColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = LightTextPrimary,
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = Color.White.copy(alpha = 0.8f),
    onSecondaryContainer = LightTextPrimary,
    tertiary = AccentAmber,
    onTertiary = Color.Black,
    background = Color(0xFFF5F7FA),
    onBackground = LightTextPrimary,
    surface = Color.White.copy(alpha = 0.6f),
    onSurface = LightTextPrimary,
    surfaceVariant = Color.White.copy(alpha = 0.4f),
    onSurfaceVariant = LightTextSecondary,
    error = AccentRed,
    onError = Color.White
)

@Composable
fun LifeOSTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkGlassmorphismColorScheme else LightGlassmorphismColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (darkTheme) GradientStart else LightGradientStart).toArgb()
            window.navigationBarColor = (if (darkTheme) GradientStart else LightGradientStart).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Force RTL for Persian
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
