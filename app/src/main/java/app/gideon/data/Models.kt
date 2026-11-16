package app.gideon.data

import kotlinx.serialization.Serializable

@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    val summary: String = "",
    val link: String,
    val imageUrl: String? = null,
    val sourceKey: String,
    val sourceName: String,
    val category: String,
    val publishedAt: Long,
    val videoId: String? = null,
    val isShort: Boolean = false,
) {
    val isVideo: Boolean get() = videoId != null
    val categoryEnum: Category get() = Category.fromId(category)
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
