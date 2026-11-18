package com.vikram.mindkeep.core.designsystem.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
private val LightColors = lightColorScheme(
    primary = MindKeepLightPrimary, onPrimary = MindKeepLightOnPrimary,
    background = MindKeepLightBackground, surface = MindKeepLightSurface
)
private val DarkColors = darkColorScheme(
    primary = MindKeepDarkPrimary, background = MindKeepDarkBackground, surface = MindKeepDarkSurface
)
@Composable
fun MindKeepTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MindKeepTypography, content = content)
}
