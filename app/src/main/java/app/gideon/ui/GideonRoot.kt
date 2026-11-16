package app.gideon.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gideon.data.NewsItem
import app.gideon.ui.feed.FeedScreen
import app.gideon.ui.feed.FeedTopBar
import app.gideon.ui.glass.GlassTabBar
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.glass.TabSpec
import app.gideon.ui.screens.ExploreScreen
import app.gideon.ui.screens.SavedScreen
import app.gideon.ui.screens.SettingsScreen
import app.gideon.ui.screens.StoryViewer
import app.gideon.ui.theme.GColors
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val tabs = listOf(
    TabSpec("Fil", Icons.Outlined.Home, Icons.Rounded.Home),
    TabSpec("Explorer", Icons.Outlined.Explore, Icons.Rounded.Explore),
    TabSpec("Gardés", Icons.Rounded.BookmarkBorder, Icons.Rounded.Bookmark),
    TabSpec("Réglages", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

private data class ViewerRequest(val items: List<NewsItem>, val index: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GideonRoot() {
    val vm: MainViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootBackdrop = rememberLayerBackdrop()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var lastViewer by remember { mutableStateOf<ViewerRequest?>(null) }
    val pagerState = rememberPagerState { vm.currentFeed.size }
    val pullState = rememberPullToRefreshState()

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabBarSpace = navInset + 10.dp + 66.dp

    fun openViewer(items: List<NewsItem>, index: Int) {
        val req = ViewerRequest(items, index)
        lastViewer = req
        viewer = req
    }

    LaunchedEffect(vm.feedVersion) {
        if (pagerState.pageCount > 0 && pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(vm.hasLoaded) {
        if (vm.hasLoaded && vm.shouldAskNotifications) {
            delay(2500)
            vm.shouldAskNotifications = false
            ensureNotificationPermission()
        }
    }

    BackHandler(enabled = viewer != null) { viewer = null }
    BackHandler(enabled = viewer == null && (tab != 0 || vm.categoryFilter != null)) {
        if (tab != 0) tab = 0 else vm.openCategory(null)
    }

    Box(Modifier.fillMaxSize().background(GColors.Bg)) {
        Box(Modifier.fillMaxSize().layerBackdrop(rootBackdrop)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(initialScale = 0.97f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f)))
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> FeedScreen(
                        vm = vm,
                        pagerState = pagerState,
                        pullState = pullState,
                        feedVisible = viewer == null,
                        topSpace = topInset + 58.dp,
                        bottomSpace = tabBarSpace,
                    )
                    1 -> ExploreScreen(
                        vm = vm,
                        topInset = topInset,
                        bottomSpace = tabBarSpace,
                        onOpenCategory = { vm.openCategory(it); tab = 0 },
                        onOpenStories = ::openViewer,
                    )
                    2 -> SavedScreen(vm, topInset, tabBarSpace, onOpenStories = ::openViewer)
                    else -> SettingsScreen(
                        vm = vm,
                        topInset = topInset,
                        bottomSpace = tabBarSpace,
                        onDailyBriefChange = { on ->
                            vm.updateDailyBrief(on)
                            if (on) ensureNotificationPermission()
                        },
                    )
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides rootBackdrop) {
            AnimatedVisibility(
                visible = tab == 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                FeedTopBar(vm, pullState, topInset, onRefresh = {
                    if (vm.pendingCount > 0) vm.applyPending() else vm.refresh(manual = true)
                })
            }
            GlassTabBar(
                tabs = tabs,
                selected = tab,
                onSelect = { i ->
                    if (i == tab && i == 0) {
                        if (vm.categoryFilter != null) vm.openCategory(null)
                        else scope.launch { pagerState.animateScrollToPage(0) }
                    }
                    tab = i
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 22.dp)
                    .padding(bottom = navInset + 10.dp),
            )
        }

        AnimatedVisibility(
            visible = viewer != null,
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(260)),
        ) {
            lastViewer?.let { req ->
                StoryViewer(
                    items = req.items,
                    startIndex = req.index,
                    vm = vm,
                    topInset = topInset,
                    bottomInset = navInset,
                    onClose = { viewer = null },
                )
            }
        }

        AnimatedVisibility(
            visible = !vm.hasLoaded || (vm.allItems.isEmpty() && vm.isRefreshing),
            enter = fadeIn(),
            exit = fadeOut(tween(450)),
        ) {
            SplashView(Modifier.background(GColors.Bg))
        }
    }
}
