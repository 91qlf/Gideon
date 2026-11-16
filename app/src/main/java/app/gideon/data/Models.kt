package app.gideon.data

import kotlinx.serialization.Serializable

@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    val summary: String = "",
    val link: String,
    val imageUrl: String? = null,
    val publisherId: String,
    val sourceName: String,
    val domain: String = "",
    val kind: String = SourceKind.RSS,
    val category: String,
    val publishedAt: Long,
    val videoId: String? = null,
    val isShort: Boolean = false,
    /** Direct video file (MP4 / HLS) published in the feed, played natively. */
    val streamUrl: String? = null,
) {
    val isVideo: Boolean get() = videoId != null || streamUrl != null
    val isYouTube: Boolean get() = videoId != null
    val categoryEnum: Category get() = Category.fromId(category)
    val groupKey: String get() = "$kind:$publisherId"
}

@Serializable
data class FeedCache(
    val updatedAt: Long = 0L,
    val items: List<NewsItem> = emptyList(),
)

enum class Category(val label: String) {
    UNE("À la une"),
    MONDE("Monde"),
    FRANCE("France"),
    ECO("Économie"),
    TECH("Tech"),
    SCIENCES("Sciences"),
    SPORT("Sport"),
    CULTURE("Culture");

    companion object {
        fun fromId(id: String): Category = entries.firstOrNull { it.name == id } ?: UNE
    }
}

enum class FeedTab(val label: String) { FOR_YOU("Pour toi"), VIDEOS("Vidéos"), ARTICLES("Articles") }

// ---------- Full articles (reader) ----------

enum class BlockType { PARAGRAPH, HEADING, QUOTE, BULLET, IMAGE }

@Serializable
data class ArticleBlock(val type: BlockType, val text: String = "", val url: String? = null)

@Serializable
data class Article(
    val url: String,
    val title: String? = null,
    val byline: String? = null,
    val publishedAt: Long? = null,
    val heroImage: String? = null,
    val standfirst: String? = null,
    val blocks: List<ArticleBlock> = emptyList(),
    /** True when the page only exposes the beginning of the text (paywall, teaser…). */
    val truncated: Boolean = false,
) {
    val textLength: Int get() = blocks.filter { it.type != BlockType.IMAGE }.sumOf { it.text.length }
    val readingMinutes: Int
        get() = (blocks.filter { it.type != BlockType.IMAGE }.sumOf { b -> b.text.count { it == ' ' } + 1 } / 230)
            .coerceAtLeast(1)

    /** The first real paragraphs, used as a teaser in the feed. */
    fun lead(maxChars: Int = 320): String? {
        val sb = StringBuilder()
        for (b in blocks) {
            if (b.type != BlockType.PARAGRAPH) continue
            if (b.text.length < 60) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(b.text)
            if (sb.length >= maxChars) break
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }
}
