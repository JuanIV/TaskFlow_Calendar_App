package com.example.taskflow.ui.theme

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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun onColor(background: Color): Color = if (background.luminance() > 0.5f) Color.Black else Color.White

private fun schemeFor(accent: Color, dark: Boolean): ColorScheme {
    return if (dark) {
        val primary = lerp(accent, Color.White, 0.35f)
        darkColorScheme(
            primary = primary,
            onPrimary = onColor(primary),
            primaryContainer = lerp(accent, Color.Black, 0.45f),
            onPrimaryContainer = Color.White,
            secondary = lerp(accent, Color.White, 0.55f),
            tertiary = lerp(accent, Color(0xFFFFB74D), 0.5f)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onColor(accent),
            primaryContainer = lerp(accent, Color.White, 0.8f),
            onPrimaryContainer = Color.Black,
            secondary = lerp(accent, Color.Black, 0.25f),
            tertiary = lerp(accent, Color(0xFFFB8C00), 0.5f)
        )
    }
}

@Composable
fun TaskFlowTheme(
    themeMode: String = "SYSTEM",
    dynamicColor: Boolean = true,
    accentArgb: Int = 0xFF3949AB.toInt(),
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        schemeFor(Color(accentArgb), dark)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
