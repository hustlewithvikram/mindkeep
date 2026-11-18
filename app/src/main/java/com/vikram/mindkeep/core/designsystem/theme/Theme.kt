package com.vikram.mindkeep.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF365D79), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EAF8), onPrimaryContainer = Color(0xFF102F45),
    secondary = Color(0xFF526477), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5EF), onSecondaryContainer = Color(0xFF202F3D),
    tertiary = Color(0xFF67558C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECE2FA), onTertiaryContainer = Color(0xFF291B45),
    background = Color(0xFFF7F8FC), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F8FC), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E6ED), onSurfaceVariant = Color(0xFF414A54),
    outline = Color(0xFF737D88), outlineVariant = Color(0xFFC4CBD4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8CBE8), onPrimary = Color(0xFF17344A),
    primaryContainer = Color(0xFF294A63), onPrimaryContainer = Color(0xFFD8EBFA),
    secondary = Color(0xFFBCCBDA), onSecondary = Color(0xFF263746),
    secondaryContainer = Color(0xFF354B5D), onSecondaryContainer = Color(0xFFD9E6F2),
    tertiary = Color(0xFFD0BCEB), onTertiary = Color(0xFF392B50),
    tertiaryContainer = Color(0xFF514166), onTertiaryContainer = Color(0xFFEBDDFA),
    background = Color(0xFF0E1318), onBackground = Color(0xFFE2E7ED),
    surface = Color(0xFF0E1318), onSurface = Color(0xFFE2E7ED),
    surfaceVariant = Color(0xFF252D35), onSurfaceVariant = Color(0xFFBEC8D2),
    surfaceContainerLowest = Color(0xFF090D11),
    surfaceContainerLow = Color(0xFF151C23),
    surfaceContainer = Color(0xFF1A222A),
    surfaceContainerHigh = Color(0xFF242D36),
    surfaceContainerHighest = Color(0xFF303A44),
    outline = Color(0xFF8995A1), outlineVariant = Color(0xFF414C57)
)

private val MindKeepShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun MindKeepTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MindKeepTypography,
        shapes = MindKeepShapes,
        content = content
    )
}
