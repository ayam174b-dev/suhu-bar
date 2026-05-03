package com.tempmonitor.app.ui.theme

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
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceColor,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8DD58E),
    onPrimary = Color(0xFF003912),
    primaryContainer = Color(0xFF1F5921),
    onPrimaryContainer = Color(0xFFB7E4B0),
    secondary = Color(0xFFB6CCAB),
    onSecondary = Color(0xFF223420),
    secondaryContainer = Color(0xFF394A36),
    onSecondaryContainer = Color(0xFFD7E7CB),
    tertiary = Color(0xFFB4CDA9),
    onTertiary = Color(0xFF1F3617),
    tertiaryContainer = Color(0xFF364D2E),
    onTertiaryContainer = Color(0xFFD0E8C4),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF42493D),
    onSurfaceVariant = Color(0xFFC2C9B9)
)

/**
 * The app intentionally opts OUT of dynamic color (Material You) on Android 12+
 * because the design language calls for a consistent "green alam" identity.
 */
@Composable
fun SuhuBarTheme(
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
