package app.gideon.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gideon.ui.theme.GColors
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** The backdrop (content behind) that glass surfaces in this subtree refract. */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Liquid glass surface: the content behind is blurred, lensed at the edges
 * (refraction + light dispersion), boosted in vibrancy and rimmed by a specular highlight.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = Capsule(),
    surface: Color = Color.White.copy(alpha = 0.08f),
    blurRadius: Dp = 3.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 32.dp,
    chromatic: Boolean = true,
    castShadow: Boolean = true,
    scale: () -> Float = { 1f },
): Modifier {
    val backdrop = LocalBackdrop.current
        ?: return this
            .graphicsLayer { val s = scale(); scaleX = s; scaleY = s }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f))
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            lens(refractionHeight.toPx(), refractionAmount.toPx(), depthEffect = true, chromaticAberration = chromatic)
        },
        highlight = { Highlight.Default },
        shadow = if (castShadow) ({ Shadow(radius = 22.dp, color = Color.Black.copy(alpha = 0.22f)) }) else null,
        layerBlock = { val s = scale(); scaleX = s; scaleY = s },
        onDrawSurface = { drawRect(surface) },
    )
}

/** iOS-style "swell" on touch: glass grows under the finger then springs back. */
@Composable
fun rememberPressScale(source: MutableInteractionSource, pressed: Float = 1.14f): State<Float> {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch {
                    anim.animateTo(pressed, spring(dampingRatio = 0.55f, stiffness = 700f))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    if (anim.value < pressed * 0.98f) anim.animateTo(pressed, spring(stiffness = 1500f))
                    anim.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 380f))
                }
            }
        }
    }
    return anim.asState()
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = Color.White,
    surface: Color = Color.White.copy(alpha = 0.08f),
    iconModifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source)
    Box(
        modifier
            .size(size)
            .liquidGlass(shape = Capsule(), surface = surface, refractionHeight = size / 4, refractionAmount = size / 2, scale = { scale })
            .clickable(source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = iconModifier.size(size * 0.48f))
    }
}

@Composable
fun GlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    surface: Color = Color.White.copy(alpha = 0.10f),
    height: Dp = 42.dp,
) {
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, pressed = 1.08f)
    Row(
        modifier
            .height(height)
            .liquidGlass(shape = Capsule(), surface = surface, refractionHeight = 10.dp, refractionAmount = 20.dp, scale = { scale })
            .clickable(source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Segmented control with a sliding, stretching glass lens. */
@Composable
fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 84.dp,
) {
    val pos = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) { pos.animateTo(selected.toFloat(), spring(dampingRatio = 0.68f, stiffness = 420f)) }
    val density = LocalDensity.current
    Box(
        modifier
            .height(42.dp)
            .width(itemWidth * options.size + 8.dp)
            .liquidGlass(shape = Capsule(), refractionHeight = 10.dp, refractionAmount = 20.dp)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .offset { IntOffset(with(density) { (itemWidth * pos.value).roundToPx() }, 0) }
                .width(itemWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    pos.value // subscribe to every animation frame
                    val stretch = (abs(pos.velocity) * 0.08f).coerceAtMost(0.32f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.25f
                }
                .clip(Capsule())
                .background(Color.White.copy(alpha = 0.20f))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                val alpha by animateFloatAsState(if (active) 1f else 0.62f, label = "seg")
                Box(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = Color.White.copy(alpha = alpha),
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

data class TabSpec(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Floating liquid-glass tab bar. The selection lens springs between tabs, stretches with
 * its speed, swells while touched, and can be dragged across the bar like on iOS 26.
 */
@Composable
fun GlassTabBar(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pos = remember { Animatable(selected.toFloat()) }
    val swell = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (!dragging) pos.animateTo(selected.toFloat(), spring(dampingRatio = 0.66f, stiffness = 360f))
    }
    val barSource = remember { MutableInteractionSource() }
    val barScale by rememberPressScale(barSource, pressed = 1.03f)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .liquidGlass(shape = Capsule(), surface = Color(0x1AFFFFFF), refractionHeight = 18.dp, refractionAmount = 36.dp, scale = { barScale })
            .padding(5.dp)
    ) {
        val itemWidth = maxWidth / tabs.size
        val itemPx = with(LocalDensity.current) { itemWidth.toPx() }
        Box(
            Modifier
                .offset { IntOffset((itemPx * pos.value).roundToInt(), 0) }
                .width(itemWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    pos.value // subscribe to every animation frame
                    val stretch = (abs(pos.velocity) * 0.09f).coerceAtMost(0.38f)
                    scaleX = (1f + stretch) * swell.value
                    scaleY = (1f - stretch * 0.22f) * swell.value
                }
                .clip(Capsule())
                .background(Color.White.copy(alpha = if (dragging) 0.12f else 0.18f))
        )
        Row(
            Modifier
                .fillMaxSize()
                .pointerInput(tabs.size, itemPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            scope.launch { swell.animateTo(1.18f, spring(dampingRatio = 0.5f, stiffness = 600f)) }
                        },
                        onDragEnd = {
                            dragging = false
                            val target = pos.value.roundToInt().coerceIn(0, tabs.lastIndex)
                            scope.launch { swell.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
                            scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = 0.62f, stiffness = 380f)) }
                            onSelect(target)
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { swell.animateTo(1f) }
                            scope.launch { pos.animateTo(selected.toFloat()) }
                        },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { pos.snapTo((pos.value + dx / itemPx).coerceIn(-0.15f, tabs.lastIndex + 0.15f)) }
                    }
                }
        ) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                val tint by animateFloatAsState(if (active) 1f else 0f, label = "tab")
                Column(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(barSource, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (active) tab.selectedIcon else tab.icon,
                        tab.label,
                        tint = lerp(Color.White.copy(alpha = 0.72f), Color.White, tint),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = lerp(Color.White.copy(alpha = 0.72f), Color.White, tint),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** iOS 26 style switch: the thumb turns into a stretched glass drop while pressed. */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "switch",
    )
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    val density = LocalDensity.current
    Box(
        modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(Capsule())
            .background(lerp(Color.White.copy(alpha = 0.18f), GColors.Green, progress))
            .clickable(source, indication = null) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset(with(density) { (24.dp * progress).roundToPx() }, 0) }
                .size(width = 28.dp, height = 28.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.45f * press
                    scaleY = 1f + 0.18f * press
                    alpha = 1f - 0.25f * press
                }
                .shadow(4.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** Rounded glass container for grouped content (settings, lists). */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, corner: Dp = 26.dp, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedRectangle(corner),
                surface = Color.White.copy(alpha = 0.07f),
                blurRadius = 14.dp,
                refractionHeight = 14.dp,
                refractionAmount = 24.dp,
                chromatic = false,
            )
    ) { content() }
}
