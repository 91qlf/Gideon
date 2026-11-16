package app.gideon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.gideon.R

/** Inter: the closest open-source match to Apple's SF Pro. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** iOS semantic colours (UIKit system colours, light & dark variants). */
@Immutable
data class Palette(
    val dark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val cell: Color,
    val sheet: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val accent: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
    val yellow: Color,
    val pink: Color,
    val purple: Color,
    val indigo: Color,
    val teal: Color,
    val blue: Color,
    val gray: Color,
)

val DarkPalette = Palette(
    dark = true,
    background = Color.Black,
    groupedBackground = Color.Black,
    cell = Color(0xFF1C1C1E),
    sheet = Color(0xFF1C1C1E),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    fill = Color(0x52787880),
    accent = Color(0xFFFF375F),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    pink = Color(0xFFFF375F),
    purple = Color(0xFFBF5AF2),
    indigo = Color(0xFF5E5CE6),
    teal = Color(0xFF40CBE0),
    blue = Color(0xFF0A84FF),
    gray = Color(0xFF8E8E93),
)

val LightPalette = Palette(
    dark = false,
    background = Color.White,
    groupedBackground = Color(0xFFF2F2F7),
    cell = Color.White,
    sheet = Color.White,
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x29787880),
    accent = Color(0xFFFF2D55),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    pink = Color(0xFFFF2D55),
    purple = Color(0xFFAF52DE),
    indigo = Color(0xFF5856D6),
    teal = Color(0xFF30B0C7),
    blue = Color(0xFF007AFF),
    gray = Color(0xFF8E8E93),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun systemPalette(): Palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette

/** iOS Dynamic Type scale ("Large" size), set in Inter with SF-like tracking. */
object Ios {
    private fun s(size: Int, line: Int, weight: FontWeight, tracking: Float) = TextStyle(
        fontFamily = Inter,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
    )

    val largeTitle = s(34, 41, FontWeight.Bold, -0.9f)
    val title1 = s(28, 34, FontWeight.Bold, -0.7f)
    val title2 = s(22, 28, FontWeight.Bold, -0.5f)
    val title3 = s(20, 25, FontWeight.SemiBold, -0.4f)
    val headline = s(17, 22, FontWeight.SemiBold, -0.35f)
    val body = s(17, 22, FontWeight.Normal, -0.35f)
    val callout = s(16, 21, FontWeight.Normal, -0.3f)
    val subheadline = s(15, 20, FontWeight.Normal, -0.25f)
    val footnote = s(13, 18, FontWeight.Normal, -0.1f)
    val caption1 = s(12, 16, FontWeight.Normal, 0f)
    val caption2 = s(11, 13, FontWeight.Normal, 0.05f)
}

/** Soft drop shadow for text laid over photos and videos. */
val OverlayShadow = Shadow(Color.Black.copy(alpha = 0.40f), Offset(0f, 1.5f), 8f)

@Composable
fun GideonTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = DarkPalette.accent) else lightColorScheme(primary = LightPalette.accent)) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = Inter, letterSpacing = (-0.2).sp),
            LocalContentColor provides Color.White,
            content = content,
        )
    }
}
