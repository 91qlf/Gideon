package app.gideon.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gideon.data.Category
import app.gideon.data.NewsItem
import app.gideon.ui.Burst
import app.gideon.ui.HeartBursts
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassCapsuleButton
import app.gideon.ui.glass.GlassIconButton
import app.gideon.ui.glass.GlassVariant
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.glass.LocalGlassDark
import app.gideon.ui.glass.liquidGlass
import app.gideon.ui.ios.PublisherLogo
import app.gideon.ui.openItem
import app.gideon.ui.shareItem
import app.gideon.ui.sourceColor
import app.gideon.ui.style
import app.gideon.ui.theme.Ios
import app.gideon.ui.theme.OverlayShadow
import app.gideon.ui.timeAgo
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlin.random.Random

/**
 * One full-screen story. Media is recorded into a backdrop layer so the glass controls
 * refract the picture behind them. Tap (or swipe left) on an article opens the reader.
 */
@Composable
fun NewsPage(
    item: NewsItem,
    active: Boolean,
    preload: Boolean,
    pageOffset: () -> Float,
    vm: MainViewModel,
    topSpace: Dp,
    bottomSpace: Dp,
    onOpenReader: (NewsItem) -> Unit,
    onCategory: ((Category) -> Unit)?,
    onPublisher: ((NewsItem) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val pageBackdrop = rememberLayerBackdrop()
    val controller = remember(item.id) { VideoController() }
    var bursts by remember { mutableStateOf(listOf<Burst>()) }
    val liked = item.id in vm.liked
    val saved = vm.saved.any { it.id == item.id }
    val appear by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 240f), label = "appear")

    Box(modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            if (item.isVideo) VideoBackground(item)
            else ArticleHero(item, vm.heroFor(item), active, pageOffset)
        }

        if (item.isVideo) {
            if (item.isYouTube) YouTubeLayer(item, active, preload, controller, vm.soundOn)
            else NativeVideoLayer(item, active, preload, controller, vm.soundOn)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                )
            )
        }

        // Gestures: tap = read / pause, double tap = like, swipe left = read
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(item.id) {
                    detectTapGestures(
                        onDoubleTap = { at ->
                            bursts = bursts + Burst(System.nanoTime(), at, Random.nextInt(-18, 18).toFloat())
                            if (item.id !in vm.liked) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            vm.toggleLike(item, forceOn = true)
                        },
                        onTap = { if (item.isVideo) controller.toggle() else onOpenReader(item) },
                    )
                }
                .pointerInput(item.id) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (!item.isVideo && total < -70.dp.toPx()) onOpenReader(item) },
                    ) { change, dx ->
                        total += dx
                        change.consume()
                    }
                }
        )

        CompositionLocalProvider(LocalBackdrop provides pageBackdrop, LocalGlassDark provides true) {
            if (item.isVideo) {
                AnimatedVisibility(
                    visible = controller.userPaused && !controller.ended,
                    enter = scaleIn(initialScale = 1.5f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.6f) + fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        Modifier.size(72.dp).liquidGlass(Capsule(), GlassVariant.Clear),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PlayArrow, "Lecture", tint = Color.White, modifier = Modifier.size(38.dp)) }
                }
                AnimatedVisibility(
                    visible = controller.ended,
                    enter = scaleIn(initialScale = 0.7f) + fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    GlassCapsuleButton("Revoir", onClick = { controller.replay() }, icon = Icons.Rounded.Replay, height = 44.dp)
                }
                if (controller.failed) {
                    GlassCapsuleButton(
                        "Regarder sur YouTube",
                        onClick = { context.openItem(item) },
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            ActionRail(
                item = item,
                vm = vm,
                liked = liked,
                saved = saved,
                onLike = {
                    haptics.performHapticFeedback(if (liked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    vm.toggleLike(item)
                },
                onSave = {
                    haptics.performHapticFeedback(if (saved) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    vm.toggleSave(item)
                },
                onShare = { context.shareItem(item) },
                onPublisher = onPublisher,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = bottomSpace + 6.dp),
            )

            InfoBlock(
                item = item,
                lead = if (item.isVideo) null else vm.leadFor(item),
                minutes = vm.minutesFor(item),
                onRead = { onOpenReader(item) },
                onCategory = onCategory,
                onPublisher = onPublisher,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 72.dp, bottom = bottomSpace + 8.dp)
                    .graphicsLayer {
                        alpha = 0.4f + 0.6f * appear
                        translationY = (1f - appear) * 20.dp.toPx()
                    },
            )
        }

        if (item.isVideo && active) {
            ScrubBar(
                controller,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (bottomSpace - 18.dp).coerceAtLeast(0.dp), start = 18.dp, end = 18.dp),
            )
        }

        HeartBursts(bursts, onDone = { id -> bursts = bursts.filterNot { it.id == id } }, size = 96.dp)
    }
}

/** TikTok-style progress line that becomes a scrubber when dragged. */
@Composable
private fun ScrubBar(controller: VideoController, modifier: Modifier = Modifier) {
    var scrubbing by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    val thickness by animateDpAsState(if (scrubbing) 7.dp else 2.5.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(controller) {
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        scrubbing = true
                        fraction = (at.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        controller.seekTo(fraction)
                        scrubbing = false
                    },
                    onDragCancel = { scrubbing = false },
                ) { change, _ ->
                    change.consume()
                    fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(controller) {
                detectTapGestures { at -> controller.seekTo((at.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val f = if (scrubbing) fraction else controller.progress
        if (scrubbing && controller.duration > 0f) {
            Text(
                "${clock(f * controller.duration)} / ${clock(controller.duration)}",
                style = Ios.headline.copy(shadow = OverlayShadow),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-34).dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .clip(Capsule())
                .background(Color.White.copy(alpha = if (scrubbing) 0.3f else 0.2f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color.White))
        }
    }
}

private fun clock(seconds: Float): String {
    val t = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(t / 60, t % 60)
}

@Composable
private fun ArticleHero(item: NewsItem, imageUrl: String?, active: Boolean, pageOffset: () -> Float) {
    val zoom by animateFloatAsState(
        if (active) 1.14f else 1.06f,
        if (active) tween(14000, easing = LinearEasing) else snap(),
        label = "kenburns",
    )
    val ctx = LocalPlatformContext.current
    val request = remember(imageUrl) { imageUrl?.let { ImageRequest.Builder(ctx).data(it).crossfade(350).build() } }
    val accent = sourceColor(item.sourceName)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = maxHeight * 0.64f
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clipToBounds()
                    .graphicsLayer {
                        translationY = pageOffset() * size.height * 0.18f
                        scaleX = zoom
                        scaleY = zoom
                    },
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f), Color.Black))),
                contentAlignment = Alignment.Center,
            ) {
                PublisherLogo(item.sourceName, item.domain, 96.dp)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight + 1.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.18f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black,
                    )
                )
        )
    }
}

@Composable
private fun VideoBackground(item: NewsItem) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.4f
                    scaleY = 1.4f
                }
                .blur(70.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        VideoFrame(item.defaultAspect()) { frame ->
            AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = frame)
        }
    }
}

@Composable
private fun ActionRail(
    item: NewsItem,
    vm: MainViewModel,
    liked: Boolean,
    saved: Boolean,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPublisher: ((NewsItem) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PublisherLogo(
            item.sourceName,
            item.domain,
            42.dp,
            shape = CircleShape,
            modifier = Modifier
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                .then(
                    if (onPublisher != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onPublisher(item) }
                    else Modifier
                ),
        )
        RailButton(
            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = "J'aime",
            tint = if (liked) Color(0xFFFF375F) else Color.White,
            toggled = liked,
            onClick = onLike,
        )
        RailButton(
            icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            label = "Garder",
            tint = if (saved) Color(0xFFFFD60A) else Color.White,
            toggled = saved,
            onClick = onSave,
        )
        RailButton(icon = Icons.Rounded.IosShare, label = "Partager", tint = Color.White, toggled = false, onClick = onShare)
        if (item.isVideo) {
            RailButton(
                icon = if (vm.soundOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                label = if (vm.soundOn) "Son" else "Muet",
                tint = Color.White,
                toggled = vm.soundOn,
                onClick = { vm.setSound(!vm.soundOn) },
            )
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, tint: Color, toggled: Boolean, onClick: () -> Unit) {
    val bounce = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(toggled) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (toggled) {
            bounce.snapTo(0.55f)
            bounce.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(
            icon = icon,
            contentDescription = label,
            onClick = onClick,
            size = 44.dp,
            iconSize = 22.dp,
            variant = GlassVariant.Clear,
            tint = tint,
            iconModifier = Modifier.graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            },
        )
        Spacer(Modifier.height(3.dp))
        Text(label, style = Ios.caption2.copy(shadow = OverlayShadow), color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoBlock(
    item: NewsItem,
    lead: String?,
    minutes: Int?,
    onRead: () -> Unit,
    onCategory: ((Category) -> Unit)?,
    onPublisher: ((NewsItem) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (onPublisher != null) {
                Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onPublisher(item) }
            } else Modifier,
        ) {
            PublisherLogo(item.sourceName, item.domain, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                item.sourceName,
                style = Ios.subheadline.copy(shadow = OverlayShadow),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                "  ·  " + timeAgo(item.publishedAt),
                style = Ios.subheadline.copy(shadow = OverlayShadow),
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
            )
        }
        val cat = item.categoryEnum
        if (cat != Category.UNE) {
            Spacer(Modifier.height(8.dp))
            Text(
                cat.label.uppercase(),
                color = cat.style().color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
                modifier = if (onCategory != null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onCategory(cat) }
                } else Modifier,
            )
            Spacer(Modifier.height(3.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Text(
            item.title,
            style = Ios.title3.copy(fontWeight = FontWeight.Bold, shadow = OverlayShadow),
            color = Color.White,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        val body = lead ?: item.summary
        if (body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = Ios.subheadline.copy(shadow = OverlayShadow, lineHeight = 21.sp),
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.isVideo) {
            Spacer(Modifier.height(12.dp))
            GlassCapsuleButton(
                if (minutes != null) "Lire l'article · $minutes min" else "Lire l'article",
                onClick = onRead,
                icon = Icons.Rounded.AutoStories,
                height = 34.dp,
                variant = GlassVariant.Clear,
            )
        }
    }
}
