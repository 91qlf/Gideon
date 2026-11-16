package app.gideon.ui

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gideon.GideonApp
import app.gideon.data.Article
import app.gideon.data.Category
import app.gideon.data.FeedCache
import app.gideon.data.FeedMixer
import app.gideon.data.FeedTab
import app.gideon.data.NewsItem
import app.gideon.data.Publisher
import app.gideon.work.DailyBriefWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface ArticleState {
    data object Loading : ArticleState
    data class Ready(val article: Article) : ArticleState
    data object Failed : ArticleState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val gideon = app as GideonApp
    private val repo = gideon.repository
    private val store = gideon.store
    val catalog get() = repo.catalog

    /** Everything fetched; [allItems] is what the user wants to see (Shorts filtered out by default). */
    private var rawItems: List<NewsItem> = emptyList()
    var allItems by mutableStateOf<List<NewsItem>>(emptyList()); private set
    private var forYou by mutableStateOf<List<NewsItem>>(emptyList())
    var hasLoaded by mutableStateOf(false); private set
    var isRefreshing by mutableStateOf(false); private set
    var lastUpdated by mutableLongStateOf(0L); private set
    var loadError by mutableStateOf(false); private set

    /** Bumped whenever the visible feed is rebuilt, so the pager jumps back to the top. */
    var feedVersion by mutableIntStateOf(0); private set

    /** Result of a background refresh waiting for the user to tap "nouvelles infos". */
    private var pending: FeedCache? = null
    var pendingCount by mutableIntStateOf(0); private set
    var message by mutableStateOf<String?>(null); private set

    var liked by mutableStateOf(store.liked); private set
    var saved by mutableStateOf(store.saved); private set
    var disabledGroups by mutableStateOf(store.disabledGroups); private set
    var soundOn by mutableStateOf(store.soundOn); private set
    var dailyBrief by mutableStateOf(store.dailyBrief); private set
    var textScale by mutableFloatStateOf(store.textScale); private set
    var includeShorts by mutableStateOf(store.includeShorts); private set

    var feedTab by mutableStateOf(FeedTab.FOR_YOU); private set
    var categoryFilter by mutableStateOf<Category?>(null); private set
    var publisherFilter by mutableStateOf<Publisher?>(null); private set

    val articles = mutableStateMapOf<String, ArticleState>()
    private val articleGate = Semaphore(3)

    val currentFeed: List<NewsItem> by derivedStateOf {
        val cat = categoryFilter
        val pub = publisherFilter
        when {
            pub != null -> allItems.filter { it.publisherId == pub.id }
            cat == Category.UNE -> allItems.filter { it.category == cat.name || it.isVideo }
            cat != null -> allItems.filter { it.category == cat.name }
            feedTab == FeedTab.VIDEOS -> allItems.filter { it.isVideo }
            feedTab == FeedTab.ARTICLES -> allItems.filter { !it.isVideo }
            else -> forYou
        }
    }

    val countsByCategory: Map<Category, Int> by derivedStateOf {
        Category.entries.associateWith { c -> allItems.count { it.category == c.name } }
    }

    init {
        viewModelScope.launch {
            val cache = repo.loadCache()
            if (cache.items.isNotEmpty()) {
                setItems(cache.items)
                lastUpdated = cache.updatedAt
            }
            hasLoaded = true
            if (System.currentTimeMillis() - cache.updatedAt > 20 * 60_000L) refresh(manual = false)
        }
    }

    private fun setItems(items: List<NewsItem>) {
        rawItems = items
        val visible = if (includeShorts) items else items.filterNot { it.isShort }
        allItems = visible
        forYou = FeedMixer.forYou(visible, store.affinity)
        feedVersion++
    }

    fun updateIncludeShorts(on: Boolean) {
        includeShorts = on
        store.includeShorts = on
        setItems(rawItems)
    }

    fun refresh(manual: Boolean = true) {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            val before = rawItems.mapTo(HashSet()) { it.id }
            val result = repo.refresh(disabledGroups)
            if (result != null) {
                val fresh = result.items.count { it.id !in before }
                lastUpdated = result.updatedAt
                loadError = false
                when {
                    before.isEmpty() || manual -> {
                        applyResult(result)
                        if (manual && before.isNotEmpty()) {
                            message = if (fresh > 0) "$fresh nouvelles infos" else "Tu es à jour"
                        }
                    }
                    fresh > 0 -> {
                        pending = result
                        pendingCount = fresh
                    }
                }
            } else {
                loadError = allItems.isEmpty()
                if (manual) message = "Connexion impossible"
            }
            isRefreshing = false
        }
    }

    private fun applyResult(result: FeedCache) {
        pending = null
        pendingCount = 0
        setItems(result.items)
    }

    fun applyPending() {
        pending?.let(::applyResult)
    }

    fun clearMessage() {
        message = null
    }

    fun selectTab(tab: FeedTab) {
        categoryFilter = null
        publisherFilter = null
        if (feedTab != tab) {
            feedTab = tab
            feedVersion++
        }
    }

    fun openCategory(category: Category?) {
        publisherFilter = null
        categoryFilter = category
        feedVersion++
    }

    fun openPublisher(publisher: Publisher?) {
        categoryFilter = null
        publisherFilter = publisher
        feedVersion++
    }

    fun publisherOf(item: NewsItem): Publisher? = catalog.publisher(item.publisherId)

    fun isSaved(item: NewsItem) = saved.any { it.id == item.id }

    fun toggleLike(item: NewsItem, forceOn: Boolean = false) {
        val on = forceOn || item.id !in liked
        if (on == (item.id in liked)) return
        liked = if (on) liked + item.id else liked - item.id
        store.liked = liked
        if (on) bumpAffinity(item, 1f)
    }

    fun toggleSave(item: NewsItem) {
        saved = if (isSaved(item)) saved.filterNot { it.id == item.id } else {
            bumpAffinity(item, 1.5f)
            listOf(item.copy(imageUrl = heroFor(item))) + saved
        }
        store.saved = saved
    }

    fun noteOpened(item: NewsItem) = bumpAffinity(item, 0.5f)

    private fun bumpAffinity(item: NewsItem, by: Float) {
        val map = store.affinity.toMutableMap()
        map[item.category] = (map[item.category] ?: 0f) + by
        store.affinity = map
    }

    fun setSound(on: Boolean) {
        soundOn = on
        store.soundOn = on
    }

    fun updateDailyBrief(on: Boolean) {
        dailyBrief = on
        store.dailyBrief = on
        if (on) DailyBriefWorker.schedule(gideon) else DailyBriefWorker.cancel(gideon)
    }

    fun cycleTextScale() {
        val steps = listOf(0.9f, 1f, 1.12f, 1.25f)
        val next = steps[(steps.indexOfFirst { it >= textScale - 0.01f }.coerceAtLeast(0) + 1) % steps.size]
        textScale = next
        store.textScale = next
    }

    fun isGroupEnabled(publisher: String, kind: String) = "$kind:$publisher" !in disabledGroups

    fun setGroupEnabled(publisher: String, kind: String, enabled: Boolean) {
        val key = "$kind:$publisher"
        disabledGroups = if (enabled) disabledGroups - key else disabledGroups + key
        store.disabledGroups = disabledGroups
        if (!enabled) setItems(rawItems.filter { it.groupKey != key }) else refresh(manual = false)
    }

    var shouldAskNotifications: Boolean
        get() = !store.askedNotifications && dailyBrief
        set(@Suppress("UNUSED_PARAMETER") value) {
            store.askedNotifications = true
        }

    // ------------------------------------------------------------ full articles

    /** Downloads and extracts the article behind [item] (once). */
    fun ensureArticle(item: NewsItem) {
        if (item.isVideo || articles.containsKey(item.id)) return
        articles[item.id] = ArticleState.Loading
        viewModelScope.launch {
            val article = articleGate.withPermit { repo.article(item.link) }
            articles[item.id] = if (article != null) ArticleState.Ready(article) else ArticleState.Failed
        }
    }

    fun retryArticle(item: NewsItem) {
        articles.remove(item.id)
        ensureArticle(item)
    }

    private fun readyArticle(item: NewsItem): Article? = (articles[item.id] as? ArticleState.Ready)?.article

    fun heroFor(item: NewsItem): String? = item.imageUrl ?: readyArticle(item)?.heroImage

    fun leadFor(item: NewsItem): String? = readyArticle(item)?.lead(300)

    fun minutesFor(item: NewsItem): Int? = readyArticle(item)?.takeIf { !it.truncated }?.readingMinutes
}
