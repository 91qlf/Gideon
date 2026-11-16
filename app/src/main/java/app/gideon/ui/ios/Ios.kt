package app.gideon.ui.ios

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gideon.ui.initials
import app.gideon.ui.sourceColor
import app.gideon.ui.theme.Ios
import app.gideon.ui.theme.Palette
import coil3.compose.AsyncImage
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.abs

/** UIScrollView-style rubber band at both ends of a list (replaces Android's stretch). */
@Composable
fun Modifier.iosBounce(): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val o = offset.value
                if (o == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                if ((o > 0f && available.y < 0f) || (o < 0f && available.y > 0f)) {
                    val next = if (abs(available.y) >= abs(o)) 0f else o + available.y
                    scope.launch { offset.snapTo(next) }
                    return Offset(0f, next - o)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                val o = offset.value
                val resistance = 0.5f / (1f + abs(o) / 260f)
                scope.launch { offset.snapTo(o + available.y * resistance) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value == 0f) return Velocity.Zero
                offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 260f))
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (abs(available.y) < 60f) return Velocity.Zero
                offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 200f), initialVelocity = available.y * 0.3f)
                return available
            }
        }
    }
    return this.nestedScroll(connection).graphicsLayer { translationY = offset.value }
}

/** Large title as the first row of a list. */
@Composable
fun LargeTitle(title: String, palette: Palette, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp)) {
        Text(title, style = Ios.largeTitle, color = palette.label)
        if (subtitle != null) Text(subtitle, style = Ios.subheadline, color = palette.secondaryLabel)
    }
}

/**
 * Inline navigation title that fades in once the large title has scrolled away,
 * over a soft "scroll edge" fade like iOS 26.
 */
@Composable
fun InlineTitleBar(title: String, listState: LazyListState, palette: Palette, topInset: Dp, modifier: Modifier = Modifier) {
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 90 }
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (collapsed) 1f else 0f, label = "inline")
    Box(
        modifier
            .fillMaxWidth()
            .height(topInset + 52.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.verticalGradient(
                    0f to palette.background,
                    0.72f to palette.background.copy(alpha = 0.97f),
                    1f to palette.background.copy(alpha = 0f),
                )
            ),
    ) {
        Text(
            title,
            style = Ios.headline,
            color = palette.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

@Composable
fun SectionHeader(text: String, palette: Palette, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = Ios.footnote,
        color = palette.secondaryLabel,
        modifier = modifier.padding(start = 36.dp, end = 36.dp, top = 26.dp, bottom = 7.dp),
    )
}

@Composable
fun SectionFooter(text: String, palette: Palette, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Ios.footnote,
        color = palette.secondaryLabel,
        modifier = modifier.padding(start = 36.dp, end = 36.dp, top = 7.dp),
    )
}

/** iOS 26 inset-grouped section: rounded continuous corners, hairline separators between rows. */
@Composable
fun InsetSection(palette: Palette, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedRectangle(26.dp))
            .background(palette.cell)
    ) { content() }
}

@Composable
fun RowSeparator(palette: Palette, inset: Dp = 60.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(palette.separator))
}

/** Settings-style row: coloured squircle icon, title, optional subtitle and trailing content. */
@Composable
fun IosRow(
    title: String,
    palette: Palette,
    icon: ImageVector? = null,
    iconColor: Color = palette.gray,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            icon != null -> {
                Box(
                    Modifier.size(30.dp).clip(RoundedRectangle(8.dp)).background(iconColor),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Ios.body, color = palette.label, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = Ios.footnote, color = palette.secondaryLabel, maxLines = 2)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = Ios.body, color = palette.secondaryLabel, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = palette.tertiaryLabel, modifier = Modifier.size(22.dp))
        }
    }
}

/** Newspaper logo (site icon) on a white tile, falling back to initials on the outlet colour. */
@Composable
fun PublisherLogo(name: String, domain: String, size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedRectangle(size * 0.26f)) {
    var loaded by remember(domain) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (loaded) Color.White else sourceColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(initials(name), color = Color.White, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (domain.isNotBlank()) {
            AsyncImage(
                model = "https://www.google.com/s2/favicons?domain=$domain&sz=128",
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.padding(size * 0.12f).size(size),
            )
        }
    }
}

/** UIActivityIndicatorView: eight rounded spokes fading around the circle. */
@Composable
fun IosActivityIndicator(modifier: Modifier = Modifier, color: Color = Color.White, diameter: Dp = 22.dp) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spinner")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "step",
    )
    androidx.compose.foundation.Canvas(modifier.size(diameter)) {
        val spokes = 8
        val head = step.toInt() % spokes
        val stroke = this.size.minDimension * 0.095f
        for (i in 0 until spokes) {
            val age = (head - i + spokes) % spokes
            val alpha = 1f - age / spokes.toFloat() * 0.85f
            rotate(i * 360f / spokes, center) {
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(center.x, this.size.height * 0.10f),
                    end = Offset(center.x, this.size.height * 0.32f),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    }
}

/** iOS 26 alert: rounded card, centred text, capsule buttons. */
@Composable
fun IosAlert(
    title: String,
    message: String,
    palette: Palette,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .clip(RoundedRectangle(34.dp))
                .background(if (palette.dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = Ios.headline, color = palette.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(message, style = Ios.footnote, color = palette.label.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                AlertButton(dismissLabel, palette.fill, palette.label, Modifier.weight(1f), onDismiss)
                AlertButton(confirmLabel, palette.blue, Color.White, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

@Composable
private fun AlertButton(text: String, fill: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(com.kyant.shapes.Capsule())
            .background(fill)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ios.headline, color = content, maxLines = 1)
    }
}

/** iOS 26 "scroll edge effect" under the floating tab bar. */
@Composable
fun BottomEdgeFade(palette: Palette, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to palette.background.copy(alpha = 0f),
                    0.55f to palette.background.copy(alpha = 0.55f),
                    1f to palette.background.copy(alpha = 0.92f),
                )
            )
    )
}
