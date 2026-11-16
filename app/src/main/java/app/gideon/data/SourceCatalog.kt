package app.gideon.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object SourceKind {
    const val RSS = "rss"
    const val YOUTUBE = "youtube"
}

@Serializable
data class Publisher(val id: String, val name: String, val domain: String) {
    /** Square logo served by Google's favicon service (sharp up to 128 px). */
    val logoUrl: String get() = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
}

@Serializable
data class FeedDef(
    val publisher: String,
    val kind: String,
    val category: String,
    val url: String,
) {
    val groupKey: String get() = "$kind:$publisher"
}

@Serializable
private data class SourcesFile(val publishers: List<Publisher>, val feeds: List<FeedDef>)

/** The list of outlets and their public feeds, read from assets/sources.json. */
class SourceCatalog private constructor(
    val publishers: List<Publisher>,
    val feeds: List<FeedDef>,
) {
    private val byId = publishers.associateBy { it.id }

    fun publisher(id: String): Publisher? = byId[id]

    /** Outlets having at least one feed of [kind], in catalogue order. */
    fun publishersOf(kind: String): List<Publisher> {
        val ids = feeds.filter { it.kind == kind }.map { it.publisher }.toSet()
        return publishers.filter { it.id in ids }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): SourceCatalog {
            val file = json.decodeFromString(SourcesFile.serializer(), text)
            return SourceCatalog(file.publishers, file.feeds)
        }
    }
}
