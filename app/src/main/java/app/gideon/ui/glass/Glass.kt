package app.gideon.ui.glass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Content behind the glass surfaces of this subtree. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Whether the glass of this subtree sits over dark content (picks the dark material). */
val LocalGlassDark = staticCompositionLocalOf { true }

/** Direction of the specular highlight, driven by the phone's tilt like on iOS. */
val LocalHighlightAngle = staticCompositionLocalOf<FloatState> { mutableFloatStateOf(45f) }

enum class GlassVariant {
    /** Bars, controls, menus: frosted with strong lensing. */
    Regular,

    /** Over photos and video: barely frosted, the media stays visible. */
    Clear,
}

/** Follows gravity so the rim light slides around the glass as the phone tilts. */
@Composable
fun rememberHighlightAngle(): FloatState {
    val context = LocalContext.current
    val angle = remember { mutableFloatStateOf(45f) }
    LifecycleResumeEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            private var smooth = 45f
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val target = 45f - (x / 9.81f).coerceIn(-1f, 1f) * 55f
                smooth += (target - smooth) * 0.12f
                if (abs(smooth - angle.floatValue) > 0.35f) angle.floatValue = smooth
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sm != null && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sm?.unregisterListener(listener) }
    }
    return angle
}

/** Touch state of a glass control: swell, elastic pull toward the finger and inner glow. */
@Stable
class GlassInteraction internal constructor(private val scope: CoroutineScope, private val pressedScale: Float) {
    private val press = Animatable(0f)
    private val pullX = Animatable(0f)
    private val pullY = Animatable(0f)
    private var bounds = IntSize.Zero

    var touch by mutableStateOf(Offset.Unspecified)
        private set

    val progress: Float get() = press.value

    internal fun down(at: Offset, size: IntSize) {
        bounds = size
        touch = at
        scope.launch { press.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 520f)) }
    }

    internal fun move(at: Offset) {
        touch = at
        if (bounds == IntSize.Zero) return
        val dx = ((at.x - bounds.width / 2f) / bounds.width).coerceIn(-1f, 1f)
        val dy = ((at.y - bounds.height / 2f) / bounds.height).coerceIn(-1f, 1f)
        scope.launch { pullX.snapTo(dx) }
        scope.launch { pullY.snapTo(dy) }
    }

    internal fun up() {
        scope.launch {
            if (press.value < 0.85f) press.animateTo(1f, spring(stiffness = 1600f))
            press.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 300f))
        }
        scope.launch { pullX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
        scope.launch { pullY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    }

    internal fun GraphicsLayerScope.applyTransform() {
        val p = press.value
        val s = 1f + (pressedScale - 1f) * p
        val px = pullX.value
        val py = pullY.value
        scaleX = s * (1f + abs(px) * 0.07f * p) * (1f - abs(py) * 0.03f * p)
        scaleY = s * (1f + abs(py) * 0.07f * p) * (1f - abs(px) * 0.03f * p)
        translationX = px * size.width * 0.07f * p
        translationY = py * size.height * 0.07f * p
    }
}

@Composable
fun rememberGlassInteraction(pressedScale: Float = 1.16f): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(pressedScale) { GlassInteraction(scope, pressedScale) }
}

/** Tap handling that drives a [GlassInteraction] (tracks the finger without stealing scrolls). */
@Composable
fun Modifier.glassTouch(interaction: GlassInteraction, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    if (!enabled) return this
    val click by rememberUpdatedState(onClick)
    return this
        .pointerInput(interaction) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    interaction.move(change.position)
                }
            }
        }
        .pointerInput(interaction) {
            detectTapGestures(
                onPress = { at ->
                    interaction.down(at, size)
                    tryAwaitRelease()
                    interaction.up()
                },
                onTap = { click() },
            )
        }
}

/**
 * iOS 26 Liquid Glass: the content behind is lensed at the edges (refraction + dispersion),
 * frosted and saturated, with a specular rim light that follows the device tilt.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = Capsule(),
    variant: GlassVariant = GlassVariant.Regular,
    tint: Color = Color.Unspecified,
    interaction: GlassInteraction? = null,
    shadow: Boolean = true,
): Modifier {
    val backdrop = LocalBackdrop.current
    val dark = LocalGlassDark.current
    val angle = LocalHighlightAngle.current
    val surface = when {
        tint.isSpecified -> tint
        variant == GlassVariant.Clear -> if (dark) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f)
        else -> if (dark) Color(0xFF2C2C2E).copy(alpha = 0.30f) else Color(0xFFFAFAFA).copy(alpha = 0.55f)
    }
    val layer: (GraphicsLayerScope.() -> Unit)? = interaction?.let { i -> { with(i) { applyTransform() } } }

    if (backdrop == null) {
        return this
            .then(if (layer != null) Modifier.graphicsLayer(layer) else Modifier)
            .clip(shape)
            .background(if (dark) Color(0xD92C2C2E) else Color(0xEBF2F2F7))
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (variant == GlassVariant.Regular) {
                blur(8.dp.toPx())
                lens(16.dp.toPx(), 32.dp.toPx(), depthEffect = true)
            } else {
                blur(1.5.dp.toPx())
                lens(12.dp.toPx(), 26.dp.toPx(), depthEffect = true, chromaticAberration = true)
            }
        },
        highlight = {
            Highlight(
                width = 0.8.dp,
                style = HighlightStyle.Default(
                    color = Color.White.copy(alpha = if (dark) 0.40f else 0.90f),
                    angle = angle.floatValue,
                ),
            )
        },
        shadow = if (shadow) {
            { Shadow(radius = 16.dp, offset = DpOffset(0.dp, 3.dp), color = Color.Black.copy(alpha = if (dark) 0.24f else 0.08f)) }
        } else null,
        layerBlock = layer,
        onDrawSurface = {
            drawRect(surface)
            if (interaction != null) {
                val p = interaction.progress
                val t = interaction.touch
                if (p > 0.01f && t.isSpecified) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = (if (dark) 0.18f else 0.35f) * p), Color.Transparent),
                            center = t,
                            radius = size.maxDimension * 0.75f,
                        )
                    )
                }
            }
        },
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = size * 0.48f,
    variant: GlassVariant = GlassVariant.Regular,
    tint: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    iconModifier: Modifier = Modifier,
) {
    val interaction = rememberGlassInteraction()
    val dark = LocalGlassDark.current
    Box(
        modifier
            .size(size)
            .liquidGlass(Capsule(), variant, surface, interaction)
            .glassTouch(interaction, onClick = onClick)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tint.isSpecified) tint else if (dark) Color.White else Color.Black,
            modifier = iconModifier.size(iconSize),
        )
    }
}

@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 36.dp,
    variant: GlassVariant = GlassVariant.Regular,
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val interaction = rememberGlassInteraction(1.08f)
    val dark = LocalGlassDark.current
    val color = if (contentColor.isSpecified) contentColor else if (dark) Color.White else Color.Black
    Row(
        modifier
            .height(height)
            .liquidGlass(Capsule(), variant, tint, interaction)
            .glassTouch(interaction, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** iOS 26 segmented control: glass track, sliding thumb that stretches with its speed. */
@Composable
fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 78.dp,
    height: Dp = 36.dp,
) {
    val pos = remember { Animatable(selected.toFloat()) }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(selected) { pos.animateTo(selected.toFloat(), spring(dampingRatio = 0.72f, stiffness = 420f)) }
    val dark = LocalGlassDark.current
    val density = LocalDensity.current
    val itemPx = with(density) { itemWidth.toPx() }
    val labelColor = if (dark) Color.White else Color.Black

    Box(
        modifier
            .height(height)
            .width(itemWidth * options.size + 6.dp)
            .liquidGlass(Capsule())
            .padding(3.dp)
            .pointerInput(options.size, itemPx) {
                detectTapGestures(
                    onPress = {
                        scope.launch { press.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f)) }
                        tryAwaitRelease()
                        scope.launch { press.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
                    },
                    onTap = { at ->
                        val i = (at.x / itemPx).toInt().coerceIn(0, options.lastIndex)
                        if (i != current) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            select(i)
                        }
                    },
                )
            }
    ) {
        Box(
            Modifier
                .offset { IntOffset((itemPx * pos.value).roundToInt(), 0) }
                .width(itemWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    val stretch = (abs(pos.velocity) * 0.06f).coerceAtMost(0.25f) + 0f * pos.value
                    val sw = 1f + 0.07f * press.value
                    scaleX = (1f + stretch) * sw
                    scaleY = (1f - stretch * 0.2f) * sw
                }
                .clip(Capsule())
                .background(if (dark) Color.White.copy(alpha = 0.17f) else Color.White)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                Box(Modifier.width(itemWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = labelColor.copy(alpha = if (active) 1f else 0.72f),
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** iOS 26 switch: wide track, capsule thumb that swells into a glass drop while pressed. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onColor: Color = Color(0xFF34C759),
    offColor: Color = Color(0x52787880),
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val progress by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.68f, stiffness = 420f), label = "switch")
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 650f), label = "press")
    val density = LocalDensity.current
    Box(
        modifier
            .size(width = 58.dp, height = 30.dp)
            .clip(Capsule())
            .background(lerp(offColor, onColor, progress))
            .clickable(source, indication = null) {
                haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onCheckedChange(!checked)
            }
            .semantics { role = Role.Switch }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset(with(density) { (20.dp * progress).roundToPx() }, 0) }
                .size(width = 34.dp, height = 26.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.30f * press
                    scaleY = 1f + 0.22f * press
                    alpha = 1f - 0.30f * press
                }
                .shadow(3.dp, Capsule())
                .background(Color.White, Capsule())
        )
    }
}
