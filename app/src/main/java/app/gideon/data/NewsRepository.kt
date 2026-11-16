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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class NewsRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "feed_cache.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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
        val sources = Sources.all.filter { it.groupKey !in disabledGroups }
        val gate = Semaphore(6)
        val results = coroutineScope {
            sources.map { src ->
                async { gate.withPermit { runCatching { fetch(src) }.getOrDefault(emptyList()) } }
            }.awaitAll()
        }
        val all = results.flatten()
        if (all.isEmpty()) return@withContext null

        val now = System.currentTimeMillis()
        val cutoff = now - 4L * 24 * 3600 * 1000
        val merged = all
            .filter { it.publishedAt in cutoff..(now + 3600_000) }
            .groupBy { it.id }
            .map { (_, dupes) -> dupes.firstOrNull { it.category != Category.UNE.name } ?: dupes.first() }
            .distinctBy { normalize(it.title) }
            .sortedByDescending { it.publishedAt }
            .take(600)

        val cache = FeedCache(updatedAt = now, items = merged)
        saveCache(cache)
        cache
    }

    private fun fetch(source: FeedSource): List<NewsItem> {
        val request = Request.Builder()
            .url(source.url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body ?: return emptyList()
            return FeedParser.parse(body.byteStream(), source, limit = if (source.kind == SourceKind.YOUTUBE) 15 else 30)
        }
    }

    /** Finds the og:image of an article page for feeds that ship no picture. */
    suspend fun fetchOgImage(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val source = response.body?.source() ?: return@use null
                source.request(200_000)
                val html = source.buffer.snapshot().utf8()
                OG_A.find(html)?.groupValues?.get(1) ?: OG_B.find(html)?.groupValues?.get(1)
            }
        }.getOrNull()?.replace("&amp;", "&")
    }

    private fun normalize(title: String): String =
        title.lowercase(Locale.FRENCH).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36 Gideon/1.0"
        private val OG_A = Regex("""<meta[^>]+property=["']og:image["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val OG_B = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*property=["']og:image["']""", RegexOption.IGNORE_CASE)
    }
}
