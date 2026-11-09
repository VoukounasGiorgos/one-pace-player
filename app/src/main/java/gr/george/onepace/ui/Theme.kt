package gr.george.onepace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFE4572E)
val Background = Color(0xFF0F0F12)
val CardBg = Color(0xFF232328)
val Muted = Color(0xFF9A9AA3)

@Composable
fun OnePaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Background,
            onBackground = Color.White,
            surface = Color(0xFF1A1A1F),
            onSurface = Color.White,
            surfaceVariant = CardBg,
        ),
        content = content,
    )
}
