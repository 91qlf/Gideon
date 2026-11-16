package app.gideon.ui.reader

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.gideon.data.Article
import app.gideon.data.ArticleBlock
import app.gideon.data.BlockType
import app.gideon.data.Category
import app.gideon.data.NewsItem
import app.gideon.ui.ArticleState
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassCapsuleButton
import app.gideon.ui.glass.GlassIconButton
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.glass.LocalGlassDark
import app.gideon.ui.ios.PublisherLogo
import app.gideon.ui.shareItem
import app.gideon.ui.style
import app.gideon.ui.theme.Ios
import app.gideon.ui.theme.Palette
import app.gideon.ui.timeAgo
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * iOS "page sheet" showing the article inside the app. [progress] is the presentation
 * (0 hidden → 1 shown); [drag] is the interactive pull-down offset in px.
 */
@Composable
fun ReaderSheet(
    item: NewsItem,
    vm: MainViewModel,
    palette: Palette,
    progress: () -> Float,
    drag: Animatable<Float, AnimationVector1D>,
    topInset: Dp,
    bottomInset: Dp,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissPx = with(density) { 140.dp.toPx() }
    LaunchedEffect(item.id) { vm.ensureArticle(item) }

    val connection = remember(drag) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val d = drag.value
                if (d > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -d)
                    scope.launch { drag.snapTo(d + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { drag.snapTo(drag.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.value <= 0f) return Velocity.Zero
                if (drag.value > dismissPx || available.y > 1600f) onDismiss()
                else drag.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
                return available
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topInset + 10.dp)
                .offset { IntOffset(0, ((1f - progress()) * heightPx + drag.value).roundToInt()) }
                .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))
                .background(palette.sheet)
        ) {
            ReaderContent(item, vm, palette, connection, bottomInset, onDismiss)
            // Grabber (drag to dismiss)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.5f)
                    .height(26.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } },
                        onDragStopped = { velocity ->
                            if (drag.value > dismissPx || velocity > 1600f) onDismiss()
                            else drag.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
                        },
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.padding(top = 6.dp).size(36.dp, 5.dp).clip(Capsule()).background(palette.tertiaryLabel))
            }
        }
    }
}

@Composable
private fun ReaderContent(
    item: NewsItem,
    vm: MainViewModel,
    palette: Palette,
    connection: NestedScrollConnection,
    bottomInset: Dp,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = vm.articles[item.id] ?: ArticleState.Loading
    val article = (state as? ArticleState.Ready)?.article
    var webMode by rememberSaveable(item.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val contentBackdrop = rememberLayerBackdrop()
    val scale = vm.textScale
    val hero = article?.heroImage ?: item.imageUrl
    val saved = vm.isSaved(item)

    Box(Modifier.fillMaxSize()) {
        if (webMode) {
            WebPage(item.link, palette.dark, Modifier.fillMaxSize().padding(top = 64.dp))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().layerBackdrop(contentBackdrop).nestedScroll(connection),
                contentPadding = PaddingValues(bottom = bottomInset + 48.dp),
            ) {
                item {
                    if (hero != null) {
                        AsyncImage(
                            model = hero,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).background(palette.fill),
                        )
                    } else {
                        Spacer(Modifier.height(72.dp))
                    }
                }
                item { ArticleHeader(item, article, palette, scale) }
                when (state) {
                    is ArticleState.Ready -> {
                        items(state.article.blocks) { block -> BlockView(block, palette, scale) }
                        if (state.article.truncated) {
                            item { PaywallCard(item, palette) { webMode = true } }
                        }
                    }
                    ArticleState.Loading -> item { Skeleton(palette) }
                    ArticleState.Failed -> item {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            if (item.summary.isNotBlank()) {
                                Text(item.summary, style = Ios.body.copy(fontSize = 18.sp * scale, lineHeight = 28.sp * scale), color = palette.label)
                                Spacer(Modifier.height(16.dp))
                            }
                            Text("Impossible d'extraire le texte de cette page.", style = Ios.footnote, color = palette.secondaryLabel)
                            Spacer(Modifier.height(12.dp))
                            CompositionLocalProvider(LocalGlassDark provides palette.dark) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GlassCapsuleButton("Réessayer", onClick = { vm.retryArticle(item) })
                                    GlassCapsuleButton("Ouvrir la page", onClick = { webMode = true }, icon = Icons.Rounded.Explore)
                                }
                            }
                        }
                    }
                }
                item { SourceFooter(item, palette) { webMode = true } }
            }
        }

        // Scroll-edge fade behind the floating buttons once the hero is gone
        val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 260 } }
        val edge by animateFloatAsState(if (scrolled || webMode) 1f else 0f, tween(220), label = "edge")
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .graphicsLayer { alpha = edge }
                .background(Brush.verticalGradient(0f to palette.sheet, 0.7f to palette.sheet.copy(alpha = 0.9f), 1f to palette.sheet.copy(alpha = 0f)))
        )

        CompositionLocalProvider(
            LocalBackdrop provides (if (webMode) null else contentBackdrop),
            LocalGlassDark provides (palette.dark || (hero != null && !scrolled && !webMode)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Rounded.Close, "Fermer", onClick = onDismiss, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.weight(1f))
                if (webMode) {
                    GlassCapsuleButton("Lecteur", onClick = { webMode = false }, icon = Icons.Rounded.TextFields, height = 40.dp)
                } else {
                    GlassIconButton(Icons.Rounded.TextFields, "Taille du texte", onClick = { vm.cycleTextScale() }, size = 40.dp, iconSize = 20.dp)
                }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(Icons.Rounded.IosShare, "Partager", onClick = { context.shareItem(item) }, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(10.dp))
                GlassIconButton(
                    if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    "Garder",
                    onClick = { vm.toggleSave(item) },
                    size = 40.dp,
                    iconSize = 20.dp,
                    tint = if (saved) palette.orange else Color.Unspecified,
                )
            }
        }
    }
}

@Composable
private fun ArticleHeader(item: NewsItem, article: Article?, palette: Palette, scale: Float) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PublisherLogo(item.sourceName, item.domain, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(item.sourceName, style = Ios.subheadline, fontWeight = FontWeight.SemiBold, color = palette.label)
            val cat = item.categoryEnum
            if (cat != Category.UNE) {
                Text("  ·  ", style = Ios.subheadline, color = palette.secondaryLabel)
                Text(cat.label, style = Ios.subheadline, fontWeight = FontWeight.SemiBold, color = cat.style().color)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(item.title, style = Ios.title1.copy(fontSize = 28.sp * scale, lineHeight = 34.sp * scale), color = palette.label)
        val standfirst = article?.standfirst
        if (!standfirst.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                standfirst,
                style = Ios.title3.copy(fontWeight = FontWeight.Normal, fontSize = 19.sp * scale, lineHeight = 26.sp * scale),
                color = palette.secondaryLabel,
            )
        }
        Spacer(Modifier.height(12.dp))
        val parts = buildList {
            article?.byline?.let { add(it) }
            add(formatDate(article?.publishedAt ?: item.publishedAt))
            if (article != null && !article.truncated) add("${article.readingMinutes} min de lecture")
        }
        Text(parts.joinToString(" · "), style = Ios.footnote, color = palette.secondaryLabel)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.separator))
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun BlockView(block: ArticleBlock, palette: Palette, scale: Float) {
    when (block.type) {
        BlockType.PARAGRAPH -> Text(
            block.text,
            style = Ios.body.copy(fontSize = 18.sp * scale, lineHeight = 28.sp * scale, letterSpacing = (-0.2).sp),
            color = palette.label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        BlockType.HEADING -> Text(
            block.text,
            style = Ios.title3.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp * scale, lineHeight = 27.sp * scale),
            color = palette.label,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
        )
        BlockType.QUOTE -> Row(
            Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(IntrinsicSize.Min)
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(Capsule()).background(palette.accent))
            Spacer(Modifier.width(14.dp))
            Text(
                block.text,
                style = Ios.title3.copy(fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 19.sp * scale, lineHeight = 27.sp * scale),
                color = palette.label,
            )
        }
        BlockType.BULLET -> Row(Modifier.padding(start = 24.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
            Text("•", style = Ios.body.copy(fontSize = 18.sp * scale, lineHeight = 28.sp * scale), color = palette.accent)
            Spacer(Modifier.width(10.dp))
            Text(block.text, style = Ios.body.copy(fontSize = 18.sp * scale, lineHeight = 28.sp * scale), color = palette.label)
        }
        BlockType.IMAGE -> Column(Modifier.padding(vertical = 12.dp)) {
            AsyncImage(
                model = block.url,
                contentDescription = block.text.ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedRectangle(18.dp))
                    .background(palette.fill),
            )
            if (block.text.isNotBlank()) {
                Text(
                    block.text,
                    style = Ios.footnote,
                    color = palette.secondaryLabel,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PaywallCard(item: NewsItem, palette: Palette, onOpen: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clip(RoundedRectangle(24.dp))
            .background(palette.fill)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = palette.secondaryLabel, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text("La suite est réservée aux abonnés de ${item.sourceName}", style = Ios.headline, color = palette.label)
        Spacer(Modifier.height(12.dp))
        CompositionLocalProvider(LocalGlassDark provides palette.dark) {
            GlassCapsuleButton("Continuer sur ${item.domain.ifBlank { "le site" }}", onClick = onOpen, icon = Icons.Rounded.Explore)
        }
    }
}

@Composable
private fun SourceFooter(item: NewsItem, palette: Palette, onOpen: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp), horizontalAlignment = Alignment.Start) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.separator))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PublisherLogo(item.sourceName, item.domain, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.sourceName, style = Ios.headline, color = palette.label)
                Text("Publié ${timeAgo(item.publishedAt)}", style = Ios.footnote, color = palette.secondaryLabel)
            }
            CompositionLocalProvider(LocalGlassDark provides palette.dark) {
                GlassCapsuleButton("Page d'origine", onClick = onOpen, icon = Icons.Rounded.Explore, height = 34.dp)
            }
        }
    }
}

@Composable
private fun Skeleton(palette: Palette) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).graphicsLayer { alpha = a }) {
        listOf(1f, 0.96f, 0.98f, 0.7f, 0f, 1f, 0.94f, 0.97f, 0.55f).forEach { w ->
            if (w == 0f) Spacer(Modifier.height(18.dp))
            else {
                Box(Modifier.fillMaxWidth(w).height(14.dp).clip(Capsule()).background(palette.fill))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPage(url: String, dark: Boolean, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                if (Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = dark
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("d MMMM yyyy 'à' HH:mm", Locale.FRENCH).format(Date(ts))
