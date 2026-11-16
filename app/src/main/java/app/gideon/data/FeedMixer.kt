package app.gideon.data

import kotlin.math.exp

/** Builds the "Pour toi" order: fresh news first, nudged by what the user likes, videos woven in. */
object FeedMixer {

    fun forYou(items: List<NewsItem>, affinity: Map<String, Float>): List<NewsItem> {
        val now = System.currentTimeMillis()
        val maxAff = (affinity.values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        fun score(it: NewsItem): Double {
            val hours = (now - it.publishedAt).coerceAtLeast(0) / 3_600_000.0
            val fresh = exp(-hours / 14.0) * 10.0
            val aff = (affinity[it.category] ?: 0f) / maxAff * 3.0
            val une = if (it.category == Category.UNE.name) 1.2 else 0.0
            return fresh + aff + une
        }

        val articles = ArrayDeque(items.filter { !it.isVideo }.sortedByDescending(::score))
        val videos = ArrayDeque(items.filter { it.isVideo }.sortedByDescending(::score))
        val out = ArrayList<NewsItem>(items.size)
        var sinceVideo = 0
        while (articles.isNotEmpty() || videos.isNotEmpty()) {
            val wantVideo = videos.isNotEmpty() && (sinceVideo >= 2 || articles.isEmpty())
            val queue = if (wantVideo) videos else articles
            val last = out.lastOrNull()?.sourceName
            // avoid the same outlet twice in a row when a close alternative exists
            val idx = (0 until minOf(4, queue.size)).firstOrNull { queue[it].sourceName != last } ?: 0
            out += queue.removeAt(idx)
            sinceVideo = if (wantVideo) 0 else sinceVideo + 1
        }
        return out
    }
}
