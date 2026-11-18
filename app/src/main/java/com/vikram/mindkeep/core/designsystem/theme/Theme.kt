package com.vikram.mindkeep.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = MindKeepLightPrimary,
    onPrimary = MindKeepLightOnPrimary,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD7E7F5),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF102D43),
    secondary = androidx.compose.ui.graphics.Color(0xFF526477),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDCE5EF),
    tertiary = androidx.compose.ui.graphics.Color(0xFF655B7D),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFE9DFF7),
    background = MindKeepLightBackground,
    surface = MindKeepLightSurface
)

private val DarkColors = darkColorScheme(
    primary = MindKeepDarkPrimary,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF29445B),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD7E7F5),
    secondary = androidx.compose.ui.graphics.Color(0xFFBEC9D7),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF394858),
    tertiary = androidx.compose.ui.graphics.Color(0xFFD1C3E8),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF4B435E),
    background = MindKeepDarkBackground,
    surface = MindKeepDarkSurface
)

private val MindKeepShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun MindKeepTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MindKeepTypography,
        shapes = MindKeepShapes,
        content = content
    )
}
