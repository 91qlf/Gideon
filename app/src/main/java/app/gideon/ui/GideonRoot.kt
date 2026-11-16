package app.gideon.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gideon.CrashLog
import app.gideon.data.NewsItem
import app.gideon.ui.feed.FeedScreen
import app.gideon.ui.feed.FeedTopBar
import app.gideon.ui.feed.LocalYouTubePool
import app.gideon.ui.feed.YouTubePool
import app.gideon.ui.glass.IosTabBar
import app.gideon.ui.glass.LocalBackdrop
import app.gideon.ui.glass.LocalGlassDark
import app.gideon.ui.glass.LocalHighlightAngle
import app.gideon.ui.glass.TabItem
import app.gideon.ui.glass.rememberHighlightAngle
import app.gideon.ui.ios.BottomEdgeFade
import app.gideon.ui.ios.IosAlert
import app.gideon.ui.reader.ReaderSheet
import app.gideon.ui.screens.SavedScreen
import app.gideon.ui.screens.SearchScreen
import app.gideon.ui.screens.SettingsScreen
import app.gideon.ui.screens.StoryViewer
import app.gideon.ui.theme.DarkPalette
import app.gideon.ui.theme.systemPalette
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val tabs = listOf(
    TabItem("À la une", Icons.Rounded.Home),
    TabItem("Gardés", Icons.Rounded.Bookmark),
    TabItem("Réglages", Icons.Rounded.Settings),
)

private const val SEARCH = 3

private data class ViewerRequest(val items: List<NewsItem>, val index: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GideonRoot() {
    val vm: MainViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val palette = systemPalette()
    val rootBackdrop = rememberLayerBackdrop()
    val highlight = rememberHighlightAngle()

    // Warm YouTube engines, reused from video to video (much faster than a fresh embed each time).
    val lifecycleOwner = LocalLifecycleOwner.current
    val youTubePool = remember { YouTubePool(context, lifecycleOwner.lifecycle) }
    LaunchedEffect(Unit) {
        delay(1200)
        youTubePool.warmUp(2)
    }
    DisposableEffect(Unit) { onDispose { youTubePool.release() } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var lastViewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var reader by remember { mutableStateOf<NewsItem?>(null) }
    val sheet = remember { Animatable(0f) }
    val sheetDrag = remember { Animatable(0f) }

    var crashReport by remember { mutableStateOf(CrashLog.read(context)) }
    val pagerState = rememberPagerState { vm.currentFeed.size }
    val pullState = rememberPullToRefreshState()

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabBarSpace = navInset + 8.dp + 58.dp
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val sheetProgress: () -> Float = {
        sheet.value * (1f - (sheetDrag.value / screenHeightPx).coerceIn(0f, 1f))
    }

    val screen = if (searchActive) SEARCH else tab
    val onFeed = screen == 0

    // iOS 26: scrolling down a list folds the tab bar, scrolling up brings it back.
    var barMinimized by remember { mutableStateOf(false) }
    LaunchedEffect(screen) { barMinimized = false }
    val listScreen by rememberUpdatedState(!onFeed)
    val minimizeOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (listScreen && source == NestedScrollSource.UserInput) {
                    if (consumed.y < -8f) barMinimized = true else if (consumed.y > 8f) barMinimized = false
                }
                return Offset.Zero
            }
        }
    }

    fun openReader(item: NewsItem) {
        focus.clearFocus()
        vm.noteOpened(item)
        reader = item
        scope.launch {
            sheetDrag.snapTo(0f)
            sheet.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 300f))
        }
    }

    fun closeReader() {
        scope.launch {
            sheet.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            reader = null
            sheetDrag.snapTo(0f)
        }
    }

    fun openItems(items: List<NewsItem>, index: Int) {
        val item = items.getOrNull(index) ?: return
        if (!item.isVideo) {
            openReader(item)
        } else {
            val req = ViewerRequest(items, index)
            lastViewer = req
            viewer = req
        }
    }

    LaunchedEffect(vm.feedVersion) {
        if (pagerState.pageCount > 0 && pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    // Status / navigation bar icon colours follow what is on screen, like on iOS.
    val view = LocalView.current
    val lightContent = onFeed || reader != null || viewer != null || palette.dark
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !lightContent
        controller.isAppearanceLightNavigationBars = !lightContent
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

    BackHandler(enabled = reader != null) { closeReader() }
    BackHandler(enabled = reader == null && viewer != null) { viewer = null }
    BackHandler(enabled = reader == null && viewer == null && (searchActive || tab != 0 || vm.categoryFilter != null || vm.publisherFilter != null)) {
        when {
            searchActive -> {
                focus.clearFocus()
                searchActive = false
            }
            tab != 0 -> tab = 0
            else -> vm.openCategory(null)
        }
    }

    CompositionLocalProvider(
        LocalHighlightAngle provides highlight,
        LocalOverscrollFactory provides null,
        LocalYouTubePool provides youTubePool,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Presenting layer: pushed back with rounded corners while a sheet is up (iOS page sheet).
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = sheetProgress()
                        if (p > 0f) {
                            val s = 1f - 0.07f * p
                            scaleX = s
                            scaleY = s
                            translationY = 8.dp.toPx() * p
                            clip = true
                            shape = RoundedCornerShape(36.dp * p)
                        }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (onFeed) Color.Black else palette.background)
                        .nestedScroll(minimizeOnScroll)
                        .layerBackdrop(rootBackdrop)
                ) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn(tween(240)) + scaleIn(initialScale = 0.98f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)))
                                .togetherWith(fadeOut(tween(150)))
                        },
                        label = "screens",
                    ) { s ->
                        when (s) {
                            0 -> FeedScreen(
                                vm = vm,
                                pagerState = pagerState,
                                pullState = pullState,
                                feedVisible = viewer == null && reader == null && !searchActive,
                                topSpace = topInset + 52.dp,
                                bottomSpace = tabBarSpace + 14.dp,
                                onOpenReader = ::openReader,
                            )
                            1 -> SavedScreen(vm, palette, topInset, tabBarSpace, onOpen = ::openItems)
                            2 -> SettingsScreen(
                                vm = vm,
                                palette = palette,
                                topInset = topInset,
                                bottomSpace = tabBarSpace,
                                onDailyBriefChange = { on ->
                                    vm.updateDailyBrief(on)
                                    if (on) ensureNotificationPermission()
                                },
                            )
                            else -> SearchScreen(
                                vm = vm,
                                query = query,
                                palette = palette,
                                topInset = topInset,
                                bottomSpace = tabBarSpace,
                                onOpen = ::openItems,
                                onCategory = { cat ->
                                    focus.clearFocus()
                                    vm.openCategory(cat)
                                    searchActive = false
                                    tab = 0
                                },
                                onPublisher = { pub ->
                                    focus.clearFocus()
                                    vm.openPublisher(pub)
                                    searchActive = false
                                    tab = 0
                                },
                            )
                        }
                    }
                }

                CompositionLocalProvider(
                    LocalBackdrop provides rootBackdrop,
                    LocalGlassDark provides (onFeed || palette.dark),
                ) {
                    AnimatedVisibility(
                        visible = onFeed,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        FeedTopBar(vm, pullState, topInset, onRefresh = {
                            if (vm.pendingCount > 0) vm.applyPending() else vm.refresh(manual = true)
                        })
                    }
                    if (!onFeed) {
                        BottomEdgeFade(palette, tabBarSpace + 28.dp, Modifier.align(Alignment.BottomCenter))
                    }
                    IosTabBar(
                        tabs = tabs,
                        selected = tab,
                        searchActive = searchActive,
                        minimized = barMinimized && !searchActive,
                        onSelect = { i ->
                            focus.clearFocus()
                            barMinimized = false
                            if (!searchActive && i == tab && i == 0) {
                                if (vm.categoryFilter != null || vm.publisherFilter != null) vm.openCategory(null)
                                else scope.launch { pagerState.animateScrollToPage(0) }
                            }
                            searchActive = false
                            tab = i
                        },
                        onSearch = { searchActive = true },
                        query = query,
                        onQueryChange = { query = it },
                        accent = if (onFeed || palette.dark) DarkPalette.accent else palette.accent,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }

                // Dim the presenting layer under a sheet
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = sheetProgress() }
                        .background(Color.Black.copy(alpha = 0.35f))
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
                        active = reader == null,
                        topInset = topInset,
                        bottomInset = navInset,
                        onOpenReader = ::openReader,
                        onClose = { viewer = null },
                    )
                }
            }

            reader?.let { item ->
                ReaderSheet(
                    item = item,
                    vm = vm,
                    palette = palette,
                    progress = { sheet.value },
                    drag = sheetDrag,
                    topInset = topInset,
                    bottomInset = navInset,
                    onDismiss = ::closeReader,
                )
            }

            AnimatedVisibility(
                visible = !vm.hasLoaded || (vm.allItems.isEmpty() && vm.isRefreshing),
                enter = fadeIn(),
                exit = fadeOut(tween(450)),
            ) {
                SplashView(Modifier.background(Color.Black))
            }

            crashReport?.let { report ->
                IosAlert(
                    title = "Gideon s'est fermé",
                    message = "L'app a planté la dernière fois. Partage le rapport pour qu'on corrige le problème.",
                    palette = palette,
                    confirmLabel = "Partager",
                    onConfirm = {
                        CrashLog.clear(context)
                        crashReport = null
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Rapport de plantage Gideon")
                            putExtra(android.content.Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Partager le rapport"))
                    },
                    dismissLabel = "Ignorer",
                    onDismiss = {
                        CrashLog.clear(context)
                        crashReport = null
                    },
                )
            }
        }
    }
}
