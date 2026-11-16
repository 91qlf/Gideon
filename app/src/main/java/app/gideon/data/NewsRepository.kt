package app.gideon.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

class NewsRepository(context: Context, val catalog: SourceCatalog) {

    private val cacheFile = File(context.filesDir, "feed_cache_v2.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val articleCache = object : LinkedHashMap<String, Article>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Article>?): Boolean = size > 80
    }

    suspend fun loadCache(): FeedCache = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(FeedCache.serializer(), cacheFile.readText()) }.getOrDefault(FeedCache())
    }

    private fun saveCache(cache: FeedCache) {
        runCatching {
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(json.encodeToString(FeedCache.serializer(), cache))
            tmp.renameTo(cacheFile)
        }
    }

    /**
     * Downloads every enabled feed in parallel. Returns null if nothing at all
     * could be fetched (offline, …) so callers keep their current content.
     */
    suspend fun refresh(disabledGroups: Set<String>): FeedCache? = withContext(Dispatchers.IO) {
        val feeds = catalog.feeds.filter { it.groupKey !in disabledGroups }
        val gate = Semaphore(8)
        val results = coroutineScope {
            feeds.map { feed ->
                async {
                    gate.withPermit {
                        val pub = catalog.publisher(feed.publisher) ?: return@withPermit emptyList()
                        val limit = if (feed.kind == SourceKind.YOUTUBE) 15 else 25
                        FeedFetcher.fetch(feed, pub, limit).result?.items.orEmpty()
                    }
                }
            }.awaitAll()
        }
        val all = results.flatten()
        if (all.isEmpty()) return@withContext null

        val now = System.currentTimeMillis()
        val cutoff = now - 3L * 24 * 3600 * 1000
        val merged = all
            .filter { it.publishedAt in cutoff..(now + 3600_000) }
            .groupBy { it.id }
            .map { (_, dupes) -> dupes.firstOrNull { it.category != Category.UNE.name } ?: dupes.first() }
            .distinctBy { normalize(it.title) }
            .sortedByDescending { it.publishedAt }
            .take(1000)

        val cache = FeedCache(updatedAt = now, items = merged)
        saveCache(cache)
        cache
    }

    /** Full text of an article, extracted from its web page (memory-cached). */
    suspend fun article(url: String): Article? = withContext(Dispatchers.IO) {
        synchronized(articleCache) { articleCache[url] }?.let { return@withContext it }
        val article = ArticleFetcher.fetch(url).article ?: return@withContext null
        synchronized(articleCache) { articleCache[url] = article }
        article
    }

    private fun normalize(title: String): String =
        title.lowercase(Locale.FRENCH).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
