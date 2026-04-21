package com.koard.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = KoardGreen500,
    secondary = KoardGreen600,
    tertiary = KoardGreen700,
    background = KoardGray900,
    surface = KoardGray700,
    onPrimary = KoardWhite,
    onSecondary = KoardWhite,
    onTertiary = KoardWhite,
    onBackground = KoardWhite,
    onSurface = KoardWhite,
    error = KoardRed500,
    surfaceVariant = KoardGray700,
    onSurfaceVariant = KoardGray200
)

private val LightColorScheme = lightColorScheme(
    primary = KoardGreen800,
    secondary = KoardGreen600,
    tertiary = KoardGreen700,
    background = KoardWhite,
    surface = KoardGray50,
    onPrimary = KoardWhite,
    onSecondary = KoardWhite,
    onTertiary = KoardWhite,
    onBackground = KoardGray900,
    onSurface = KoardGray900,
    error = KoardRed500,
    surfaceVariant = KoardGray200,
    onSurfaceVariant = KoardGray700
)

@Composable
fun KoardAndroidSDKTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
