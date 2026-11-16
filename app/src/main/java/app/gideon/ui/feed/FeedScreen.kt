package app.gideon.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.data.FeedTab
import app.gideon.ui.GideonLogo
import app.gideon.ui.MainViewModel
import app.gideon.ui.glass.GlassIconButton
import app.gideon.ui.glass.GlassPill
import app.gideon.ui.glass.GlassSegmented
import app.gideon.ui.theme.GType
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
) {
    val items = vm.currentFeed
    PullToRefreshBox(
        isRefreshing = vm.isRefreshing,
        onRefresh = { vm.refresh(manual = true) },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {},
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
                    pageOffset = { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction },
                    vm = vm,
                    topSpace = topSpace,
                    bottomSpace = bottomSpace,
                    onCategory = { vm.openCategory(it) },
                )
            }
        }
    }

    // Look ahead: fetch missing pictures for the next stories.
    LaunchedEffect(pagerState.settledPage, items) {
        val start = pagerState.settledPage
        for (i in start..(start + 3)) items.getOrNull(i)?.let(vm::ensureImage)
    }
}

@Composable
private fun EmptyFeed(vm: MainViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            GideonLogo(Modifier.size(96.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    vm.isRefreshing -> "Chargement de l'actu…"
                    vm.loadError -> "Impossible de joindre les sources.\nVérifie ta connexion."
                    else -> "Rien ici pour l'instant."
                },
                style = GType.Body,
                textAlign = TextAlign.Center,
            )
            if (!vm.isRefreshing) {
                Spacer(Modifier.height(16.dp))
                GlassPill("Réessayer", onClick = { vm.refresh(manual = true) }, icon = Icons.Rounded.Refresh)
            }
        }
    }
}

/** Glass header floating over the feed: tabs (or the active category) and refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedTopBar(vm: MainViewModel, pullState: PullToRefreshState, topInset: Dp, onRefresh: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(850, easing = LinearEasing)), label = "angle")

    Column(
        Modifier.fillMaxWidth().padding(top = topInset + 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp)) {
            val cat = vm.categoryFilter
            if (cat != null) {
                GlassPill(cat.label, onClick = { vm.openCategory(null) }, icon = Icons.Rounded.Close, modifier = Modifier.align(Alignment.Center))
            } else {
                GlassSegmented(
                    options = FeedTab.entries.map { it.label },
                    selected = vm.feedTab.ordinal,
                    onSelect = { vm.selectTab(FeedTab.entries[it]) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            GlassIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Actualiser",
                onClick = onRefresh,
                size = 42.dp,
                modifier = Modifier.align(Alignment.CenterEnd),
                iconModifier = Modifier.graphicsLayer {
                    rotationZ = if (vm.isRefreshing) angle else pullState.distanceFraction.coerceIn(0f, 1.5f) * 240f
                },
            )
        }

        AnimatedVisibility(
            visible = vm.pendingCount > 0,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            GlassPill(
                "${vm.pendingCount} nouvelles infos",
                onClick = onRefresh,
                icon = Icons.Rounded.ArrowUpward,
                surface = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f),
                height = 38.dp,
            )
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
            GlassPill(message ?: "", onClick = { vm.clearMessage() }, height = 38.dp)
        }
    }
}
