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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gideon.data.NewsItem
import app.gideon.ui.MainViewModel
import app.gideon.ui.feed.NewsPage
import app.gideon.ui.glass.GlassIconButton
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.theme.GColors
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Full-screen vertical reader opened from Explore, search or saved items. */
@Composable
fun StoryViewer(
    items: List<NewsItem>,
    startIndex: Int,
    vm: MainViewModel,
    topInset: Dp,
    bottomInset: Dp,
    onClose: () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val state = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    LaunchedEffect(state.settledPage) {
        for (i in state.settledPage..(state.settledPage + 2)) items.getOrNull(i)?.let(vm::ensureImage)
    }
    Box(Modifier.fillMaxSize().background(GColors.Bg)) {
        VerticalPager(
            state = state,
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it)?.id ?: it },
        ) { page ->
            val item = items.getOrNull(page) ?: return@VerticalPager
            NewsPage(
                item = item,
                active = state.settledPage == page,
                pageOffset = { (state.currentPage - page) + state.currentPageOffsetFraction },
                vm = vm,
                topSpace = topInset + 58.dp,
                bottomSpace = bottomInset + 6.dp,
                onCategory = null,
            )
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            GlassIconButton(
                Icons.Rounded.Close,
                "Fermer",
                onClick = onClose,
                size = 44.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(top = topInset + 8.dp, start = 14.dp),
            )
        }
    }
}
