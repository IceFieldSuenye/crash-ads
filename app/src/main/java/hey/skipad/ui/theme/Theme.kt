package hey.skipad.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFDCEBFF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001B3D),
    secondary = MintGreen,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD3F7EA),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF00301F),
    tertiary = SunsetOrange,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFE8EDF5),
)

private val DarkColors = darkColorScheme(
    primary = OceanBlueLight,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF001A33),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF0A2A4D),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD6E8FF),
    secondary = MintGreenLight,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00281A),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF083D2E),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFC9F7E7),
    tertiary = SunsetOrange,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF1E2A3C),
)

@Composable
fun HeySkipAdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
