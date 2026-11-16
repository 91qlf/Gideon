package app.gideon.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.gideon.data.Category
import app.gideon.data.NewsItem
import app.gideon.ui.theme.Ios
import kotlin.math.roundToInt

fun timeAgo(ts: Long, now: Long = System.currentTimeMillis()): String {
    if (ts <= 0L) return "jamais"
    val min = (now - ts).coerceAtLeast(0) / 60_000
    val h = min / 60
    val d = h / 24
    return when {
        min < 1 -> "à l'instant"
        min < 60 -> "il y a $min min"
        h < 24 -> "il y a $h h"
        d == 1L -> "hier"
        else -> "il y a $d j"
    }
}

private val palette = listOf(
    Color(0xFF2FE6FF), Color(0xFFFF2D6F), Color(0xFF7C5CFF), Color(0xFFFFB020),
    Color(0xFF34C759), Color(0xFF4D7CFF), Color(0xFFFF6B3D), Color(0xFFE85CFF),
)

fun sourceColor(name: String): Color = palette[(name.hashCode() and 0x7fffffff) % palette.size]

fun initials(name: String): String =
    name.split(' ', '\'').filter { it.isNotBlank() && it[0].isLetterOrDigit() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "G" }

data class CategoryStyle(val icon: ImageVector, val color: Color)

fun Category.style(): CategoryStyle = when (this) {
    Category.UNE -> CategoryStyle(Icons.Rounded.Newspaper, Color(0xFFFF375F))
    Category.MONDE -> CategoryStyle(Icons.Rounded.Public, Color(0xFF0A84FF))
    Category.FRANCE -> CategoryStyle(Icons.Rounded.Flag, Color(0xFF5E5CE6))
    Category.ECO -> CategoryStyle(Icons.Rounded.Euro, Color(0xFF30D158))
    Category.TECH -> CategoryStyle(Icons.Rounded.Memory, Color(0xFFBF5AF2))
    Category.SCIENCES -> CategoryStyle(Icons.Rounded.Science, Color(0xFF40CBE0))
    Category.SPORT -> CategoryStyle(Icons.Rounded.SportsSoccer, Color(0xFFFF9F0A))
    Category.CULTURE -> CategoryStyle(Icons.Rounded.TheaterComedy, Color(0xFFFF2D55))
}

fun Context.openItem(item: NewsItem) {
    val uri = Uri.parse(item.link)
    if (item.isVideo) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
        }
        return
    }
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF07070A.toInt()).build()
            )
            .build()
            .launchUrl(this, uri)
    } catch (_: Exception) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

fun Context.shareItem(item: NewsItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.link}\n\nvu sur Gideon")
    }
    startActivity(Intent.createChooser(send, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The Gideon "G": a single stroke with a cyan / coral chromatic split. */
@Composable
fun GideonLogo(modifier: Modifier = Modifier, split: Float = 1f, strokeScale: Float = 1f) {
    Canvas(modifier) {
        val unit = size.minDimension / 108f
        val shift = 1.8f * unit * split
        drawG(Offset(-shift, -shift * 0.78f), Color(0xFF2FE6FF), unit, strokeScale)
        drawG(Offset(shift, shift * 0.78f), Color(0xFFFF2D6F), unit, strokeScale)
        drawG(Offset.Zero, Color.White, unit, strokeScale)
    }
}

private fun DrawScope.drawG(offset: Offset, color: Color, unit: Float, strokeScale: Float) {
    val r = 19f * unit
    val c = center + offset
    val stroke = Stroke(width = 8f * unit * strokeScale, cap = StrokeCap.Round)
    translate(offset.x, offset.y) {
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = -315f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = stroke,
        )
    }
    drawLine(color, Offset(c.x + r, c.y), Offset(c.x + 2f * unit, c.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

@Composable
fun SplashView(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "splash")
    val split by t.animateFloat(
        0.4f, 2.2f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "split",
    )
    Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            GideonLogo(Modifier.size(150.dp), split = split)
            Text("Gideon", style = Ios.largeTitle, color = Color.White)
            Text("L'actu, en un scroll", style = Ios.subheadline, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

data class Burst(val id: Long, val at: Offset, val tilt: Float)

/** TikTok-like heart that pops where the user double-tapped. */
@Composable
fun HeartBursts(bursts: List<Burst>, onDone: (Long) -> Unit, size: Dp = 110.dp) {
    bursts.forEach { b ->
        key(b.id) {
            val p = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                p.animateTo(1f, tween(950, easing = LinearEasing))
                onDone(b.id)
            }
            Icon(
                Icons.Rounded.Favorite,
                null,
                tint = Color(0xFFFF375F),
                modifier = Modifier
                    .offset { IntOffset((b.at.x - size.toPx() / 2).roundToInt(), (b.at.y - size.toPx() / 2).roundToInt()) }
                    .size(size)
                    .graphicsLayer {
                        val v = p.value
                        val s = when {
                            v < 0.18f -> v / 0.18f * 1.25f
                            v < 0.32f -> 1.25f - (v - 0.18f) / 0.14f * 0.25f
                            else -> 1f + (v - 0.32f) * 0.6f
                        }
                        scaleX = s
                        scaleY = s
                        rotationZ = b.tilt
                        translationY = -(v - 0.32f).coerceAtLeast(0f) * 180.dp.toPx()
                        alpha = if (v < 0.55f) 1f else 1f - (v - 0.55f) / 0.45f
                    },
            )
        }
    }
}
