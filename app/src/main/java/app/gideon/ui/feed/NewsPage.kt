package app.gideon.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gideon.data.Category
import app.gideon.data.NewsItem
import app.gideon.ui.Burst
import app.gideon.ui.HeartBursts
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassIconButton
import app.gideon.ui.glass.GlassPill
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.glass.liquidGlass
import app.gideon.ui.initials
import app.gideon.ui.openItem
import app.gideon.ui.shareItem
import app.gideon.ui.sourceColor
import app.gideon.ui.theme.GColors
import app.gideon.ui.theme.GType
import app.gideon.ui.timeAgo
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.random.Random

/**
 * One full-screen story. The media is recorded into a backdrop layer so the glass
 * controls on top refract the picture/video behind them.
 */
@Composable
fun NewsPage(
    item: NewsItem,
    active: Boolean,
    pageOffset: () -> Float,
    vm: MainViewModel,
    topSpace: Dp,
    bottomSpace: Dp,
    onCategory: ((Category) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pageBackdrop = rememberLayerBackdrop()
    val controller = remember(item.id) { VideoController() }
    var bursts by remember { mutableStateOf(listOf<Burst>()) }
    val liked = item.id in vm.liked
    val saved = vm.saved.any { it.id == item.id }
    val appear by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 220f), label = "appear")

    Box(modifier.fillMaxSize().clipToBounds().background(GColors.Bg)) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            if (item.isVideo) VideoBackground(item)
            else ArticleMedia(item, vm.imageFor(item), active, pageOffset, topSpace)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.16f to Color.Transparent,
                        0.48f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.88f),
                    )
                )
            )
        }

        if (item.isVideo) {
            YouTubeLayer(item, active, controller, vm.soundOn)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))
                )
            )
        }

        // Gestures: tap = play/pause, double tap = like with a heart burst
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(item.id) {
                    detectTapGestures(
                        onDoubleTap = { at ->
                            bursts = bursts + Burst(System.nanoTime(), at, Random.nextInt(-18, 18).toFloat())
                            vm.toggleLike(item, forceOn = true)
                        },
                        onTap = { if (item.isVideo) controller.toggle() },
                    )
                }
        )

        CompositionLocalProvider(LocalBackdrop provides pageBackdrop) {
            if (item.isVideo) {
                AnimatedVisibility(
                    visible = controller.started && !controller.isPlaying,
                    enter = scaleIn(initialScale = 1.6f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.6f) + fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        Modifier.size(84.dp).liquidGlass(shape = Capsule(), refractionHeight = 20.dp, refractionAmount = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PlayArrow, "Lecture", tint = Color.White, modifier = Modifier.size(46.dp)) }
                }
                if (controller.failed) {
                    GlassPill(
                        "Regarder sur YouTube",
                        onClick = { context.openItem(item) },
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            ActionRail(
                item = item,
                liked = liked,
                saved = saved,
                onLike = { vm.toggleLike(item) },
                onSave = { vm.toggleSave(item) },
                onShare = { context.shareItem(item) },
                soundOn = vm.soundOn,
                onSound = { vm.setSound(!vm.soundOn) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = bottomSpace + 22.dp),
            )

            InfoPanel(
                item = item,
                onOpen = { vm.noteOpened(item); context.openItem(item) },
                onCategory = onCategory,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 84.dp, bottom = bottomSpace + 16.dp)
                    .graphicsLayer {
                        alpha = 0.35f + 0.65f * appear
                        translationY = (1f - appear) * 28.dp.toPx()
                    },
            )
        }

        if (item.isVideo && active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomSpace + 4.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(Capsule())
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(controller.progress).background(Color.White))
            }
        }

        HeartBursts(bursts, onDone = { id -> bursts = bursts.filterNot { it.id == id } })
    }
}

@Composable
private fun ArticleMedia(item: NewsItem, imageUrl: String?, active: Boolean, pageOffset: () -> Float, topSpace: Dp) {
    val zoom by animateFloatAsState(
        if (active) 1.12f else 1.0f,
        if (active) tween(15000, easing = LinearEasing) else snap(),
        label = "kenburns",
    )
    val ctx = LocalPlatformContext.current
    val request = remember(imageUrl) { imageUrl?.let { ImageRequest.Builder(ctx).data(it).crossfade(450).build() } }
    val accent = sourceColor(item.sourceName)

    Box(Modifier.fillMaxSize()) {
        // Ambient blurred copy fills the screen…
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f; alpha = 0.85f }
                    .blur(60.dp),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), GColors.Bg, accent.copy(alpha = 0.25f)))
                )
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))

        // …and the sharp photo floats as a card, with parallax and a slow Ken Burns zoom.
        Box(
            Modifier
                .padding(top = topSpace + 8.dp, start = 14.dp, end = 14.dp)
                .fillMaxWidth()
                .aspectRatio(0.92f)
                .graphicsLayer { translationY = pageOffset() * size.height * 0.35f }
                .clip(RoundedRectangle(34.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedRectangle(34.dp))
                .background(accent.copy(alpha = 0.25f))
        ) {
            if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f), GColors.Bg))
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initials(item.sourceName),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
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
                .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }
                .blur(70.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        VideoFrame(item) { frame ->
            AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = frame)
        }
    }
}

@Composable
private fun ActionRail(
    item: NewsItem,
    liked: Boolean,
    saved: Boolean,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    soundOn: Boolean,
    onSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val color = sourceColor(item.sourceName)
        Box(
            Modifier
                .size(52.dp)
                .liquidGlass(shape = Capsule(), surface = color.copy(alpha = 0.30f), refractionHeight = 12.dp, refractionAmount = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(item.sourceName), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
        RailButton(
            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = "J'aime",
            tint = if (liked) GColors.Heart else Color.White,
            toggled = liked,
            onClick = onLike,
        )
        RailButton(
            icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            label = "Garder",
            tint = if (saved) GColors.Gold else Color.White,
            toggled = saved,
            onClick = onSave,
        )
        RailButton(icon = Icons.Rounded.Share, label = "Partager", tint = Color.White, toggled = false, onClick = onShare)
        if (item.isVideo) {
            RailButton(
                icon = if (soundOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                label = if (soundOn) "Son" else "Muet",
                tint = Color.White,
                toggled = soundOn,
                onClick = onSound,
            )
        }
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    toggled: Boolean,
    onClick: () -> Unit,
) {
    val bounce = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(toggled) {
        if (first) { first = false; return@LaunchedEffect }
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
            size = 50.dp,
            tint = tint,
            iconModifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, style = GType.Caption.copy(color = Color.White))
    }
}

@Composable
private fun InfoPanel(
    item: NewsItem,
    onOpen: () -> Unit,
    onCategory: ((Category) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .height(30.dp)
                    .liquidGlass(shape = Capsule(), refractionHeight = 8.dp, refractionAmount = 14.dp, castShadow = false)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(sourceColor(item.sourceName)))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (item.isVideo) "${item.sourceName} · Vidéo" else item.sourceName,
                    style = GType.Label,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(timeAgo(item.publishedAt), style = GType.Caption.copy(color = Color.White.copy(alpha = 0.78f)))
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, style = GType.Title, maxLines = 5, overflow = TextOverflow.Ellipsis)
        if (item.summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                item.summary,
                style = GType.Body,
                maxLines = if (expanded) 14 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassPill(
                if (item.isVideo) "Ouvrir sur YouTube" else "Lire l'article",
                onClick = onOpen,
                icon = if (item.isVideo) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.OpenInNew,
                surface = Color.White.copy(alpha = 0.16f),
            )
            val cat = item.categoryEnum
            if (onCategory != null && cat != Category.UNE) {
                GlassPill("#${cat.label}", onClick = { onCategory(cat) }, height = 42.dp)
            }
        }
    }
}
