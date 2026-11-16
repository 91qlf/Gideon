package app.gideon.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.data.Category
import app.gideon.data.FeedTab
import app.gideon.data.NewsItem
import app.gideon.ui.GideonLogo
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassCapsuleButton
import app.gideon.ui.glass.GlassIconButton
import app.gideon.ui.glass.GlassSegmented
import app.gideon.ui.glass.glassTouch
import app.gideon.ui.glass.liquidGlass
import app.gideon.ui.glass.rememberGlassInteraction
import app.gideon.ui.ios.IosActivityIndicator
import com.kyant.shapes.Capsule
import androidx.compose.material3.Icon
import app.gideon.ui.theme.Ios
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm: MainViewModel,
    pagerState: PagerState,
    pullState: PullToRefreshState,
    feedVisible: Boolean,
    topSpace: Dp,
    bottomSpace: Dp,
    onOpenReader: (NewsItem) -> Unit,
) {
    val items = vm.currentFeed
    PullToRefreshBox(
        isRefreshing = vm.isRefreshing,
        onRefresh = { vm.refresh(manual = true) },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            val f = pullState.distanceFraction.coerceIn(0f, 1f)
            if (f > 0.02f || vm.isRefreshing) {
                IosActivityIndicator(
                    diameter = 26.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topSpace + 12.dp)
                        .graphicsLayer {
                            val v = if (vm.isRefreshing) 1f else f
                            alpha = v
                            scaleX = 0.6f + 0.4f * v
                            scaleY = 0.6f + 0.4f * v
                        },
                )
            }
        },
    ) {
        if (items.isEmpty()) {
            EmptyFeed(vm)
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { index -> items.getOrNull(index)?.id ?: index },
            ) { page ->
                val item = items.getOrNull(page) ?: return@VerticalPager
                NewsPage(
                    item = item,
                    active = feedVisible && pagerState.settledPage == page,
                    preload = feedVisible && pagerState.settledPage + 1 == page,
                    pageOffset = { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction },
                    vm = vm,
                    topSpace = topSpace,
                    bottomSpace = bottomSpace,
                    onOpenReader = onOpenReader,
                    onCategory = { cat: Category -> vm.openCategory(cat) },
                    onPublisher = { it: NewsItem -> vm.openPublisher(vm.publisherOf(it)) },
                )
            }
        }
    }

    // Read ahead: fetch the full text of the current and next stories.
    LaunchedEffect(pagerState.settledPage, items) {
        val start = pagerState.settledPage
        for (i in start..(start + 2)) items.getOrNull(i)?.let(vm::ensureArticle)
    }
}

@Composable
private fun EmptyFeed(vm: MainViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            if (vm.isRefreshing) IosActivityIndicator(diameter = 30.dp) else GideonLogo(Modifier.size(88.dp))
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    vm.isRefreshing -> "Chargement de l'actu…"
                    vm.loadError -> "Impossible de joindre les journaux.\nVérifie ta connexion."
                    else -> "Rien ici pour l'instant."
                },
                style = Ios.subheadline,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            if (!vm.isRefreshing) {
                Spacer(Modifier.height(16.dp))
                GlassCapsuleButton("Réessayer", onClick = { vm.refresh(manual = true) }, icon = Icons.Rounded.Refresh)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshButton(vm: MainViewModel, pullState: PullToRefreshState, onRefresh: () -> Unit, modifier: Modifier) {
    val interaction = rememberGlassInteraction()
    Box(
        modifier
            .size(38.dp)
            .liquidGlass(Capsule(), interaction = interaction)
            .glassTouch(interaction, enabled = !vm.isRefreshing, onClick = onRefresh),
        contentAlignment = Alignment.Center,
    ) {
        if (vm.isRefreshing) {
            IosActivityIndicator(diameter = 20.dp)
        } else {
            Icon(
                Icons.Rounded.Refresh,
                "Actualiser",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = pullState.distanceFraction.coerceIn(0f, 1.5f) * 240f },
            )
        }
    }
}

/** Floating header of the feed: segmented control (or active filter) and refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedTopBar(vm: MainViewModel, pullState: PullToRefreshState, topInset: Dp, onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = topInset + 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp)) {
            val filter = vm.publisherFilter?.name ?: vm.categoryFilter?.label
            if (filter != null) {
                GlassCapsuleButton(
                    filter,
                    onClick = { vm.openCategory(null) },
                    icon = Icons.Rounded.Close,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                GlassSegmented(
                    options = FeedTab.entries.map { it.label },
                    selected = vm.feedTab.ordinal,
                    onSelect = { vm.selectTab(FeedTab.entries[it]) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            RefreshButton(vm, pullState, onRefresh, Modifier.align(Alignment.CenterEnd))
        }

        AnimatedVisibility(
            visible = vm.pendingCount > 0,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            GlassCapsuleButton("${vm.pendingCount} nouvelles infos", onClick = onRefresh, icon = Icons.Rounded.ArrowUpward, height = 34.dp)
        }

        val message = vm.message
        LaunchedEffect(message) {
            if (message != null) {
                delay(2600)
                vm.clearMessage()
            }
        }
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            GlassCapsuleButton(message ?: "", onClick = { vm.clearMessage() }, height = 34.dp)
        }
    }
}
