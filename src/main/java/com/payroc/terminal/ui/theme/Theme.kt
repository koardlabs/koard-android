package com.payroc.terminal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PayrocBlue,
    onPrimary = PayrocWhite,
    secondary = PayrocNavy,
    onSecondary = PayrocWhite,
    tertiary = PayrocBlue,
    onTertiary = PayrocWhite,
    background = PayrocWhite,
    onBackground = PayrocDarkText,
    surface = PayrocWhite,
    onSurface = PayrocDarkText,
    surfaceVariant = PayrocLightestGray,
    onSurfaceVariant = PayrocMediumGray,
    outline = PayrocLightGray,
    outlineVariant = PayrocLightGray,
    error = PayrocRed,
    onError = PayrocWhite,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4D8FE0),
    onPrimary = PayrocWhite,
    secondary = Color(0xFF3A6FBB),
    onSecondary = PayrocWhite,
    tertiary = Color(0xFF4D8FE0),
    onTertiary = PayrocWhite,
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF1A0000),
)

@Composable
fun PayrocTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
