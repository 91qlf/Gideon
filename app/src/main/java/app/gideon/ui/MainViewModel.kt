package app.gideon.ui

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gideon.GideonApp
import app.gideon.data.Category
import app.gideon.data.FeedCache
import app.gideon.data.FeedMixer
import app.gideon.data.FeedTab
import app.gideon.data.NewsItem
import app.gideon.data.SourceKind
import app.gideon.work.DailyBriefWorker
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val gideon = app as GideonApp
    private val repo = gideon.repository
    private val store = gideon.store

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

    var feedTab by mutableStateOf(FeedTab.FOR_YOU); private set
    var categoryFilter by mutableStateOf<Category?>(null); private set

    val imageOverrides = mutableStateMapOf<String, String>()
    private val imageAttempts = HashSet<String>()

    val currentFeed: List<NewsItem> by derivedStateOf {
        val cat = categoryFilter
        when {
            cat != null && cat == Category.UNE -> allItems.filter { it.category == cat.name || it.isVideo }
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
        allItems = items
        forYou = FeedMixer.forYou(items, store.affinity)
        feedVersion++
    }

    fun refresh(manual: Boolean = true) {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            val before = allItems.mapTo(HashSet()) { it.id }
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
                    fresh > 0 -> { pending = result; pendingCount = fresh }
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

    fun applyPending() { pending?.let(::applyResult) }

    fun clearMessage() { message = null }

    fun selectTab(tab: FeedTab) {
        categoryFilter = null
        if (feedTab != tab) {
            feedTab = tab
            feedVersion++
        }
    }

    fun openCategory(category: Category?) {
        categoryFilter = category
        feedVersion++
    }

    fun isLiked(item: NewsItem) = item.id in liked
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
            listOf(item.copy(imageUrl = item.imageUrl ?: imageOverrides[item.id])) + saved
        }
        store.saved = saved
    }

    fun noteOpened(item: NewsItem) = bumpAffinity(item, 0.5f)

    private fun bumpAffinity(item: NewsItem, by: Float) {
        val map = store.affinity.toMutableMap()
        map[item.category] = (map[item.category] ?: 0f) + by
        store.affinity = map
    }

    fun setSound(on: Boolean) { soundOn = on; store.soundOn = on }

    fun updateDailyBrief(on: Boolean) {
        dailyBrief = on
        store.dailyBrief = on
        if (on) DailyBriefWorker.schedule(gideon) else DailyBriefWorker.cancel(gideon)
    }

    val catalog get() = repo.catalog

    fun isGroupEnabled(publisher: String, kind: String) = "$kind:$publisher" !in disabledGroups

    fun setGroupEnabled(publisher: String, kind: String, enabled: Boolean) {
        val key = "$kind:$publisher"
        disabledGroups = if (enabled) disabledGroups - key else disabledGroups + key
        store.disabledGroups = disabledGroups
        // hide content of disabled outlets immediately; a refresh brings new ones back
        if (!enabled) setItems(allItems.filter { it.groupKey != key }) else refresh(manual = false)
    }

    var shouldAskNotifications: Boolean
        get() = !store.askedNotifications && dailyBrief
        set(@Suppress("UNUSED_PARAMETER") value) { store.askedNotifications = true }

    fun imageFor(item: NewsItem): String? = item.imageUrl ?: imageOverrides[item.id]

    /** Looks for an og:image when a feed entry ships without a picture. */
    fun ensureImage(item: NewsItem) {
        if (item.imageUrl != null || item.isVideo || !imageAttempts.add(item.id)) return
        viewModelScope.launch {
            repo.fetchOgImage(item.link)?.let { imageOverrides[item.id] = it }
        }
    }
}
