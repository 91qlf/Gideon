package app.gideon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.data.NewsItem
import app.gideon.ui.MainViewModel
import app.gideon.ui.feed.NewsPage
import app.gideon.ui.glass.GlassIconButton
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.glass.LocalGlassDark
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Full-screen vertical player opened from search or saved items (videos, story lists). */
@Composable
fun StoryViewer(
    items: List<NewsItem>,
    startIndex: Int,
    vm: MainViewModel,
    active: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    onOpenReader: (NewsItem) -> Unit,
    onClose: () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val state = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    LaunchedEffect(state.settledPage) {
        for (i in state.settledPage..(state.settledPage + 2)) items.getOrNull(i)?.let(vm::ensureArticle)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = state,
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it)?.id ?: it },
        ) { page ->
            val item = items.getOrNull(page) ?: return@VerticalPager
            NewsPage(
                item = item,
                active = active && state.settledPage == page,
                preload = active && state.settledPage + 1 == page,
                pageOffset = { (state.currentPage - page) + state.currentPageOffsetFraction },
                vm = vm,
                topSpace = topInset + 56.dp,
                bottomSpace = bottomInset + 12.dp,
                onOpenReader = onOpenReader,
                onCategory = null,
                onPublisher = null,
            )
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassDark provides true) {
            GlassIconButton(
                Icons.Rounded.Close,
                "Fermer",
                onClick = onClose,
                size = 40.dp,
                iconSize = 20.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(top = topInset + 8.dp, start = 16.dp),
            )
        }
    }
}
