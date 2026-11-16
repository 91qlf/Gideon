package app.gideon.data

import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object FeedParser {

    fun parse(input: InputStream, source: FeedSource, limit: Int = 30): List<NewsItem> {
        val p = Xml.newPullParser()
        runCatching { p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
        runCatching { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        p.setInput(input, null)

        val out = ArrayList<NewsItem>()
        var cur: HashMap<String, String>? = null
        var bestImageWidth = -1
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < limit) {
            if (event == XmlPullParser.START_TAG) {
                val name = p.name.lowercase(Locale.ROOT)
                if (name == "item" || name == "entry") {
                    cur = HashMap()
                    bestImageWidth = -1
                } else if (cur != null) {
                    when (name) {
                        "title" -> cur.putIfAbsent("title", p.readAllText())
                        "link" -> {
                            val href = p.getAttributeValue(null, "href")
                            if (href != null) {
                                val rel = p.getAttributeValue(null, "rel")
                                if (rel == null || rel == "alternate") cur.putIfAbsent("link", href)
                            } else {
                                val t = p.readAllText()
                                if (t.startsWith("http")) cur.putIfAbsent("link", t)
                            }
                        }
                        "description", "summary", "media:description" ->
                            cur.putIfAbsent("desc", p.readAllText())
                        "content:encoded", "content" -> cur.putIfAbsent("content", p.readAllText())
                        "pubdate", "published", "dc:date", "updated" ->
                            cur.putIfAbsent("date", p.readAllText())
                        "guid", "id" -> cur.putIfAbsent("guid", p.readAllText())
                        "yt:videoid" -> cur["videoId"] = p.readAllText()
                        "enclosure" -> {
                            val url = p.getAttributeValue(null, "url")
                            val type = p.getAttributeValue(null, "type").orEmpty()
                            if (url != null && (type.startsWith("image") || (type.isEmpty() && url.looksLikeImage()))) {
                                cur.putIfAbsent("image", url)
                            }
                        }
                        "media:content", "media:thumbnail" -> {
                            val url = p.getAttributeValue(null, "url")
                            val type = p.getAttributeValue(null, "type")
                            val medium = p.getAttributeValue(null, "medium")
                            val isImage = (medium == null || medium == "image") &&
                                (type == null || type.startsWith("image"))
                            if (url != null && isImage) {
                                val w = p.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                if (w > bestImageWidth || !cur.containsKey("image")) {
                                    cur["image"] = url
                                    bestImageWidth = w
                                }
                            }
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                val name = p.name.lowercase(Locale.ROOT)
                if ((name == "item" || name == "entry") && cur != null) {
                    build(cur, source)?.let(out::add)
                    cur = null
                }
            }
            event = p.next()
        }
        return out
    }

    private fun build(m: Map<String, String>, source: FeedSource): NewsItem? {
        val title = clean(m["title"]).takeIf { it.isNotBlank() } ?: return null
        val link = m["link"] ?: m["guid"]?.takeIf { it.startsWith("http") } ?: return null
        val rawDesc = m["desc"] ?: m["content"] ?: ""
        val videoId = m["videoId"]?.takeIf { it.isNotBlank() }
        val image = when {
            videoId != null -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            else -> m["image"] ?: firstImg(m["content"]) ?: firstImg(m["desc"])
        }
        var summary = clean(rawDesc)
        if (videoId != null) summary = summary.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        if (summary.length > 320) summary = summary.take(317).trimEnd() + "…"
        if (summary.equals(title, ignoreCase = true)) summary = ""

        return NewsItem(
            id = if (videoId != null) "yt:$videoId" else "a:" + md5(link),
            title = title,
            summary = summary,
            link = link,
            imageUrl = image?.replace("&amp;", "&")?.let { if (it.startsWith("//")) "https:$it" else it },
            sourceKey = source.groupKey,
            sourceName = source.publisher,
            category = source.category.name,
            publishedAt = parseDate(m["date"]) ?: System.currentTimeMillis(),
            videoId = videoId,
            isShort = videoId != null && link.contains("/shorts/"),
        )
    }

    /** Reads every text node until the matching end tag (tolerates nested markup). */
    private fun XmlPullParser.readAllText(): String {
        val startDepth = depth
        val sb = StringBuilder()
        var e = next()
        while (e != XmlPullParser.END_DOCUMENT && !(e == XmlPullParser.END_TAG && depth == startDepth)) {
            if (e == XmlPullParser.TEXT) sb.append(text)
            e = next()
        }
        return sb.toString().trim()
    }

    private val imgRegex = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private fun firstImg(html: String?): String? =
        html?.let { imgRegex.find(it)?.groupValues?.get(1) }

    private fun String.looksLikeImage(): Boolean {
        val l = lowercase(Locale.ROOT)
        return l.contains(".jpg") || l.contains(".jpeg") || l.contains(".png") || l.contains(".webp")
    }

    fun clean(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val noImg = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<img[^>]*>"), " ")
        return HtmlCompat.fromHtml(noImg, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('￼', ' ')
            .replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun parseDate(s: String?): Long? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { return ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(t).toEpochMilli() }
        for (pattern in listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm zzz")) {
            runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(t)?.let { return it.time }
            }
        }
        return null
    }

    fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
