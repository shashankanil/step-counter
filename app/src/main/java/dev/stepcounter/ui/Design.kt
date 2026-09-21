package dev.stepcounter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Design {
    const val Background = 0xFF000000
    const val Surface = 0xFF1C1C1C
    const val White = 0xFFF3F1F1
    const val Grey = 0xFF8D8D8D
    const val Red = 0xFFD7192D
}
@Composable fun StepTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(Design.White), onPrimary = Color(Design.Background),
        background = Color(Design.Background), surface = Color(Design.Surface),
        onSurface = Color(Design.White), secondary = Color(Design.Grey), error = Color(Design.Red),
        onBackground = Color(Design.White), onSurfaceVariant = Color(0xFFB4B4B4),
        primaryContainer = Color(0xFF303030), onPrimaryContainer = Color(Design.White),
        secondaryContainer = Color(0xFF303030), onSecondaryContainer = Color(Design.White),
        surfaceVariant = Color(0xFF242424), surfaceTint = Color.Transparent,
        surfaceContainer = Color(Design.Surface), surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF303030), surfaceContainerLow = Color(0xFF141414),
        surfaceContainerLowest = Color.Black, inverseSurface = Color(0xFF303030),
        inverseOnSurface = Color(Design.White), inversePrimary = Color(Design.White), outline = Color(0xFF777777), outlineVariant = Color(0xFF383838)
    ), content = content)
}
