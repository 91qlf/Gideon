package app.gideon.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object GColors {
    val Bg = Color(0xFF07070A)
    val Cyan = Color(0xFF2FE6FF)
    val Coral = Color(0xFFFF2D6F)
    val Heart = Color(0xFFFF3B5C)
    val Green = Color(0xFF34C759)
    val Gold = Color(0xFFFFC94D)
    val Dim = Color.White.copy(alpha = 0.68f)
    val Faint = Color.White.copy(alpha = 0.42f)
}

private val textShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 10f)

object GType {
    val LargeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, color = Color.White)
    val Title = TextStyle(fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em, color = Color.White, shadow = textShadow)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Color.White.copy(alpha = 0.88f), shadow = textShadow)
    val Label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val Caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GColors.Dim)
    val Section = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em, color = Color.White)
}

@Composable
fun GideonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GColors.Cyan,
            secondary = GColors.Coral,
            background = GColors.Bg,
            surface = GColors.Bg,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}
