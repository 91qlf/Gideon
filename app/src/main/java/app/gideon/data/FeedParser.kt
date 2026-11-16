package app.gideon.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** RSS 2.0 / Atom (incl. YouTube) parser. Pure JVM so it can be tested against live feeds in CI. */
object FeedParser {

    data class Result(val channelTitle: String?, val items: List<NewsItem>)

    fun parse(
        input: InputStream,
        baseUrl: String,
        feed: FeedDef,
        publisher: Publisher,
        limit: Int = 30,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val doc = Jsoup.parse(input, null, baseUrl, Parser.xmlParser())
        val channelTitle = (doc.selectFirst("channel > title") ?: doc.selectFirst("feed > title"))?.text()?.trim()
        var entries: List<Element> = doc.getElementsByTag("item")
        if (entries.isEmpty()) entries = doc.getElementsByTag("entry")
        val out = ArrayList<NewsItem>()
        for (e in entries) {
            if (out.size >= limit) break
            runCatching { build(e, feed, publisher, now) }.getOrNull()?.let(out::add)
        }
        return Result(channelTitle, out)
    }

    private fun Element.child(vararg names: String): Element? =
        children().firstOrNull { it.normalName() in names }

    private fun build(e: Element, feed: FeedDef, pub: Publisher, now: Long): NewsItem? {
        val title = clean(e.child("title")?.text()).takeIf { it.isNotBlank() } ?: return null
        val link = linkOf(e) ?: return null
        val videoId = e.child("yt:videoid")?.text()?.trim()?.takeIf { it.isNotEmpty() }

        val content = e.child("content:encoded", "content")?.text().orEmpty()
        val summary: String = if (videoId != null) {
            e.getElementsByTag("media:description").firstOrNull()?.wholeText()
                ?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }.orEmpty()
        } else {
            val raw = (e.child("description", "summary")?.text()).orEmpty().ifBlank { content }
            clean(raw)
        }.let { if (it.length > 320) it.take(317).trimEnd() + "…" else it }
            .let { if (it.equals(title, ignoreCase = true)) "" else it }

        val image = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else imageOf(e, content)
        val stream = if (videoId == null) streamOf(e) else null

        return NewsItem(
            id = if (videoId != null) "yt:$videoId" else "a:" + md5(link.substringBefore('#')),
            title = title,
            summary = summary,
            link = link,
            imageUrl = image?.replace("&amp;", "&")?.let { if (it.startsWith("//")) "https:$it" else it },
            publisherId = pub.id,
            sourceName = pub.name,
            domain = pub.domain,
            kind = feed.kind,
            category = Category.fromId(feed.category).name,
            publishedAt = parseDate(e.child("pubdate", "published", "dc:date", "updated")?.text()) ?: now,
            videoId = videoId,
            isShort = videoId != null && link.contains("/shorts/"),
            streamUrl = stream,
        )
    }

    /** A playable video file attached to the entry (enclosure or Media RSS). */
    private fun streamOf(e: Element): String? {
        for (m in e.getElementsByTag("media:content") + e.getElementsByTag("enclosure")) {
            val url = m.attr("url").trim()
            if (url.isEmpty()) continue
            val type = m.attr("type").lowercase(Locale.ROOT)
            val medium = m.attr("medium").lowercase(Locale.ROOT)
            val path = url.lowercase(Locale.ROOT).substringBefore('?')
            if (type.startsWith("video/") || type.contains("mpegurl") || medium == "video" ||
                path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".webm")
            ) return url
        }
        return null
    }

    private fun linkOf(e: Element): String? {
        for (l in e.children()) {
            if (l.normalName() != "link") continue
            val href = l.attr("href")
            if (href.isNotBlank()) {
                val rel = l.attr("rel")
                if (rel.isEmpty() || rel == "alternate") return href.trim()
            } else {
                val t = l.text().trim()
                if (t.startsWith("http")) return t
            }
        }
        e.child("feedburner:origlink")?.text()?.trim()?.takeIf { it.startsWith("http") }?.let { return it }
        return e.child("guid", "id")?.text()?.trim()?.takeIf { it.startsWith("http") }
    }

    private fun imageOf(e: Element, content: String): String? {
        var best: String? = null
        var bestWidth = -1
        val media = e.getElementsByTag("media:content") + e.getElementsByTag("media:thumbnail")
        for (m in media) {
            val url = m.attr("url").takeIf { it.isNotBlank() } ?: continue
            val type = m.attr("type")
            val medium = m.attr("medium")
            if (medium.isNotEmpty() && medium != "image") continue
            if (type.isNotEmpty() && !type.startsWith("image")) continue
            val w = m.attr("width").toIntOrNull() ?: 0
            if (best == null || w > bestWidth) {
                best = url
                bestWidth = w
            }
        }
        if (best != null) return best
        for (enc in e.getElementsByTag("enclosure")) {
            val url = enc.attr("url").takeIf { it.isNotBlank() } ?: continue
            val type = enc.attr("type")
            if (type.startsWith("image") || (type.isEmpty() && url.looksLikeImage())) return url
        }
        e.child("itunes:image")?.attr("href")?.takeIf { it.isNotBlank() }?.let { return it }
        return firstImg(content) ?: firstImg(e.child("description", "summary")?.text())
    }

    private fun firstImg(html: String?): String? {
        if (html.isNullOrBlank() || !html.contains("<img", ignoreCase = true)) return null
        return Jsoup.parse(html).selectFirst("img[src]")?.attr("src")?.takeIf { it.startsWith("http") || it.startsWith("//") }
    }

    private fun String.looksLikeImage(): Boolean {
        val l = lowercase(Locale.ROOT)
        return l.contains(".jpg") || l.contains(".jpeg") || l.contains(".png") || l.contains(".webp")
    }

    /** Turns an HTML fragment into plain text. */
    fun clean(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val text = if (html.contains('<') || html.contains('&')) Jsoup.parse(html).text() else html
        return text.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
    }

    fun parseDate(s: String?): Long? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { return ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(t).toEpochMilli() }
        runCatching { return ZonedDateTime.parse(t).toInstant().toEpochMilli() }
        for (pattern in listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm zzz",
            "dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssZ",
        )) {
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(t)?.let { return it.time } }
        }
        return null
    }

    fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
