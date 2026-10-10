package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF005322),
    onPrimaryContainer = Color(0xFF66FFA3),
    secondary = CyberCyan,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF004E5B),
    onSecondaryContainer = Color(0xFF70F3FF),
    tertiary = AmberGold,
    onTertiary = Color(0xFF412D00),
    background = BilliardDark,
    onBackground = Color(0xFFE1E3DF),
    surface = BilliardSurface,
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = BilliardSurfaceVariant,
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF42564C)
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F3D0),
    onPrimaryContainer = Color(0xFF002111),
    secondary = CyberCyan40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF001F26),
    tertiary = Color(0xFF855300),
    background = Color(0xFFF2FBF5),
    surface = Color.White,
    onBackground = Color(0xFF161D1A),
    onSurface = Color(0xFF161D1A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent billiard theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // Billiard styling looks best in rich dark
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
