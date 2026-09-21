package dev.stepcounter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Design {
    const val Background = 0xFF101010
    const val Surface = 0xFF1C1C1C
    const val White = 0xFFF3F1F1
    const val Grey = 0xFF8D8D8D
    const val Red = 0xFFD7192D
}
@Composable fun StepTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(Design.White), onPrimary = Color(Design.Background),
        background = Color(Design.Background), surface = Color(Design.Surface),
        onSurface = Color(Design.White), secondary = Color(Design.Grey), error = Color(Design.Red)
    ), content = content)
}
