package app.gideon.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import app.gideon.ui.theme.Inter
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class TabItem(val label: String, val icon: ImageVector)

private val BarHeight = 58.dp

/**
 * iOS 26 tab bar: a floating glass capsule plus a separate search button. Touching the bar
 * lifts the selection into a magnifying glass drop you can slide; opening search morphs the
 * bar into a search field and folds the tabs into a single button.
 */
@Composable
fun IosTabBar(
    tabs: List<TabItem>,
    selected: Int,
    searchActive: Boolean,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    minimized: Boolean = false,
) {
    val dark = LocalGlassDark.current
    val labelColor = if (dark) Color.White else Color.Black
    val morph by animateFloatAsState(
        if (searchActive) 1f else 0f,
        spring(dampingRatio = 0.82f, stiffness = 360f),
        label = "search",
    )
    // The spring overshoots slightly: clamp so sizes/paddings never go negative.
    val sp = morph.coerceIn(0f, 1f)
    // iOS 26: the tab bar folds to the current tab while scrolling down a list.
    val fold by animateFloatAsState(
        if (searchActive || minimized) 1f else 0f,
        spring(dampingRatio = 0.82f, stiffness = 360f),
        label = "fold",
    )
    val fp = fold.coerceIn(0f, 1f)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchActive) {
        if (searchActive) {
            // Open the keyboard straight away, like tapping Search on iOS.
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(BarHeight)) {
        val gap = 10.dp
        val circle = BarHeight
        val wide = maxWidth - gap - circle
        val tabsWidth = lerp(wide, circle, fp)
        val searchWidth = lerp(circle, wide, sp)

        // Tabs capsule (folds into a circle showing the current tab while searching)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(tabsWidth)
                .fillMaxHeight()
                .liquidGlass(Capsule())
        ) {
            if (fp < 0.98f) {
                TabsRow(
                    tabs = tabs,
                    selected = selected,
                    onSelect = onSelect,
                    accent = accent,
                    labelColor = labelColor,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = (1f - fp * 1.8f).coerceIn(0f, 1f) },
                )
            }
            if (fp > 0.02f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((fp - 0.3f) / 0.7f).coerceIn(0f, 1f) }
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(selected) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tabs[selected.coerceIn(0, tabs.lastIndex)].icon, "Retour", tint = labelColor, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Search button → search field
        val interaction = rememberGlassInteraction()
        val focus = LocalFocusManager.current
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .width(searchWidth)
                .fillMaxHeight()
                .liquidGlass(Capsule(), interaction = if (searchActive) null else interaction)
                .glassTouch(interaction, enabled = !searchActive, onClick = onSearch)
                .padding(horizontal = lerp(0.dp, 16.dp, sp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (sp < 0.5f) Arrangement.Center else Arrangement.Start,
        ) {
            Icon(
                Icons.Rounded.Search,
                "Rechercher",
                tint = if (sp > 0.5f) labelColor.copy(alpha = 0.6f) else labelColor,
                modifier = Modifier.size(lerp(24.dp, 21.dp, sp)),
            )
            if (searchActive || sp > 0.05f) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).graphicsLayer { alpha = ((sp - 0.4f) / 0.6f).coerceIn(0f, 1f) }) {
                    if (query.isEmpty()) {
                        Text("Rechercher", color = labelColor.copy(alpha = 0.5f), fontSize = 17.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Inter, color = labelColor, fontSize = 17.sp, letterSpacing = (-0.3).sp),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Cancel,
                        "Effacer",
                        tint = labelColor.copy(alpha = 0.45f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { onQueryChange("") },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabsRow(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    labelColor: Color,
    modifier: Modifier,
) {
    val dark = LocalGlassDark.current
    val backdrop = LocalBackdrop.current
    val angle = LocalHighlightAngle.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tabsLayer = rememberLayerBackdrop()
    val pos = remember { Animatable(selected.toFloat()) }
    val drop = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var hovered by remember { mutableIntStateOf(selected) }
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(selected) {
        if (!dragging) pos.animateTo(selected.toFloat(), spring(dampingRatio = 0.7f, stiffness = 380f))
    }

    BoxWithConstraints(modifier.padding(4.dp)) {
        val itemW = maxWidth / tabs.size
        val itemPx = with(LocalDensity.current) { itemW.toPx() }

        // Resting selection pill
        Box(
            Modifier
                .offset { IntOffset((itemPx * pos.value).roundToInt(), 0) }
                .width(itemW)
                .fillMaxHeight()
                .graphicsLayer {
                    val stretch = (abs(pos.velocity) * 0.08f).coerceAtMost(0.32f) + 0f * pos.value
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.2f
                    alpha = 1f - drop.value
                }
                .clip(Capsule())
                .background(if (dark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.07f))
        )

        // Icons + labels (also recorded so the drop can magnify them)
        Row(Modifier.fillMaxSize().layerBackdrop(tabsLayer)) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                Column(
                    Modifier.width(itemW).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, null, tint = if (active) accent else labelColor, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.height(1.dp))
                    Text(
                        tab.label,
                        color = if (active) accent else labelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        // Liquid drop lifted while the finger is down
        if (backdrop != null) {
            val combined = rememberCombinedBackdrop(backdrop, tabsLayer)
            Box(
                Modifier
                    .offset { IntOffset((itemPx * pos.value).roundToInt(), 0) }
                    .width(itemW)
                    .fillMaxHeight()
                    .drawBackdrop(
                        backdrop = combined,
                        shape = { Capsule() },
                        effects = { lens(10.dp.toPx(), 20.dp.toPx(), depthEffect = true, chromaticAberration = true) },
                        highlight = {
                            Highlight(
                                width = 1.dp,
                                style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.7f), angle = angle.floatValue),
                            )
                        },
                        shadow = { Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.16f * drop.value)) },
                        layerBlock = {
                            val d = drop.value
                            scaleX = 1f + 0.34f * d
                            scaleY = 1f + 0.34f * d
                            alpha = d
                        },
                        onDrawBackdrop = { drawLayer ->
                            withTransform({ scale(1.14f, 1.14f, center) }) { drawLayer() }
                        },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = if (dark) 0.04f else 0.12f)) },
                    )
            )
        }

        // Gestures: tap a tab, or press and slide the drop across the bar
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(tabs.size, itemPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        val start = (down.position.x / itemPx).toInt().coerceIn(0, tabs.lastIndex)
                        hovered = start
                        scope.launch { drop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 520f)) }
                        scope.launch { pos.animateTo(start.toFloat(), spring(dampingRatio = 0.75f, stiffness = 520f)) }
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            if (abs(c.position.x - down.position.x) > viewConfiguration.touchSlop) moved = true
                            if (moved) {
                                c.consume()
                                val p = (c.position.x / itemPx - 0.5f).coerceIn(-0.1f, tabs.lastIndex + 0.1f)
                                scope.launch { pos.snapTo(p) }
                                val h = p.roundToInt().coerceIn(0, tabs.lastIndex)
                                if (h != hovered) {
                                    hovered = h
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            }
                        }
                        dragging = false
                        val target = if (moved) hovered else start
                        scope.launch { drop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 320f)) }
                        scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = 0.66f, stiffness = 380f)) }
                        if (target != current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        select(target)
                    }
                }
        )
    }
}
