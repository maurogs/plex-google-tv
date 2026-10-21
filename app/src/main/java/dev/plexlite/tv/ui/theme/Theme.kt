package dev.plexlite.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Accent = Color(0xFFE5A00D)
val Background = Color(0xFF101114)
val SurfaceColor = Color(0xFF1C1E23)

@Composable
fun PlexLiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Background,
            surface = SurfaceColor,
            onSurface = Color.White,
            onBackground = Color.White,
            surfaceVariant = Color(0xFF2A2D33),
            onSurfaceVariant = Color(0xFFB8BCC6),
        ),
        content = content,
    )
}
