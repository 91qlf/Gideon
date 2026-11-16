package app.gideon.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Turns a news web page into clean reading blocks (a compact take on Mozilla's Readability):
 * it scores paragraph containers, drops sharing/related/ads chrome, and falls back on the
 * JSON-LD `articleBody` that most French outlets publish.
 */
object ArticleExtractor {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val junkTokens = listOf(
        "related", "newsletter", "share", "sharing", "social", "comment", "outbrain", "taboola", "advert",
        "pub-", "-pub", "breadcrumb", "subscribe", "abonnement", "read-also", "readalso", "lire-aussi",
        "lireaussi", "cookie", "promo", "sidebar", "recommend", "most-read", "popular", "author-bio",
        "keywords", "toolbar", "sticky", "banner", "inread", "dfp", "ad-slot", "adslot", "twitter-tweet",
        "instagram", "tiktok-embed", "player", "podcast", "audio", "related-links", "see-also", "voir-aussi",
    )

    private val junkParagraph = Regex(
        "^(lire aussi|à lire aussi|a lire aussi|lire également|à voir aussi|voir aussi|newsletter|abonnez-vous|" +
            "inscrivez-vous|suivez-nous|publicité|tous droits réservés|votre navigateur|©|découvrez notre|" +
            "le monde avec afp$|avec afp$|recevez)",
        RegexOption.IGNORE_CASE,
    )

    private val paywallText = Regex(
        "(réservée? aux abonnés|il vous reste \\d|la suite est réservée|pour lire la suite|abonnez-vous pour|" +
            "contenu réservé|déjà abonné|cet article est réservé|offre d'abonnement)",
        RegexOption.IGNORE_CASE,
    )

    private val articleTypes = setOf(
        "NewsArticle", "Article", "ReportageNewsArticle", "AnalysisNewsArticle", "OpinionNewsArticle",
        "BackgroundNewsArticle", "ReviewNewsArticle", "BlogPosting", "LiveBlogPosting", "TechArticle",
    )

    fun extract(html: String, url: String): Article? {
        val doc = Jsoup.parse(html, url)
        val ld = jsonLd(doc)
        val heroMeta = meta(doc, "og:image", "og:image:url", "twitter:image")?.let { resolve(url, it) }
        val description = meta(doc, "og:description", "description", "twitter:description")?.let { FeedParser.clean(it) }
        val metaTitle = meta(doc, "og:title", "twitter:title")
        val metaAuthor = meta(doc, "author", "article:author")?.takeIf { !it.startsWith("http") }
        val hasPaywallMarkup = doc.select("[class*=paywall], [id*=paywall], [class*=premium-lock]").isNotEmpty()

        doc.select("script, style, noscript, iframe, form, button, svg, nav, aside, footer, select, input, template").remove()

        var blocks = collectBlocks(pickContainer(doc))
        var truncated = ld?.free == false || hasPaywallMarkup

        // Paywall teasers and boilerplate lines
        if (blocks.any { it.type == BlockType.PARAGRAPH && paywallText.containsMatchIn(it.text) && it.text.length < 260 }) {
            truncated = true
        }
        blocks = blocks.filterNot {
            (it.type == BlockType.PARAGRAPH || it.type == BlockType.BULLET) &&
                (junkParagraph.containsMatchIn(it.text) || (paywallText.containsMatchIn(it.text) && it.text.length < 260))
        }

        val text = blocks.filter { it.type != BlockType.IMAGE }.sumOf { it.text.length }
        val ldBody = ld?.body?.let { FeedParser.clean(it) }
        if (ldBody != null && ldBody.length > text + 300) {
            val images = blocks.filter { it.type == BlockType.IMAGE }.take(1)
            blocks = images + splitBody(ldBody)
        }

        val title = ld?.headline ?: metaTitle
        blocks = blocks
            .filterNot { it.type == BlockType.HEADING && title != null && it.text.equals(title, ignoreCase = true) }
            .distinctBy { it.type to it.text.ifEmpty { it.url } }
            .take(400)

        val finalText = blocks.filter { it.type != BlockType.IMAGE }.sumOf { it.text.length }
        if (finalText < 200 && ldBody.isNullOrBlank()) return null
        if (finalText < 700) truncated = true

        val hero = heroMeta ?: ld?.image?.let { resolve(url, it) } ?: blocks.firstOrNull { it.type == BlockType.IMAGE }?.url
        if (hero != null) blocks = blocks.filterNot { it.type == BlockType.IMAGE && it.url == hero }

        val firstPara = blocks.firstOrNull { it.type == BlockType.PARAGRAPH }?.text.orEmpty()
        val standfirst = description?.takeIf {
            it.length > 40 && !firstPara.startsWith(it.take(60)) && !it.startsWith(firstPara.take(60))
        }

        return Article(
            url = url,
            title = title,
            byline = ld?.author ?: metaAuthor,
            publishedAt = ld?.date,
            heroImage = hero,
            standfirst = standfirst,
            blocks = blocks,
            truncated = truncated,
        )
    }

    // ---------------------------------------------------------------- container

    private fun pickContainer(doc: Document): Element {
        doc.selectFirst("[itemprop=articleBody]")?.let { if (it.text().length > 400) return wrap(listOf(it)) }

        val scores = LinkedHashMap<Element, Double>()
        for (p in doc.getElementsByTag("p")) {
            val text = p.text()
            if (text.length < 40) continue
            val parent = p.parent() ?: continue
            val s = 1.0 + text.count { it == ',' } + minOf(text.length / 100, 3)
            scores[parent] = (scores[parent] ?: 0.0) + s
            parent.parent()?.let { gp -> scores[gp] = (scores[gp] ?: 0.0) + s / 2 }
        }
        var best: Element? = null
        var bestScore = 0.0
        for ((el, s) in scores) {
            val adjusted = s * (1.0 - linkDensity(el)) * bonus(el)
            if (adjusted > bestScore) {
                best = el
                bestScore = adjusted
            }
        }
        var top = best ?: return doc.selectFirst("article") ?: doc.body()

        // Paragraphs wrapped one by one (div > p): climb while the parent holds much more text.
        repeat(2) {
            val up = top.parent() ?: return@repeat
            if (up.normalName() == "body" || up.normalName() == "html") return@repeat
            if (paragraphText(up) > paragraphText(top) * 1.8 && linkDensity(up) < 0.35) top = up
        }

        // Readability-style sibling merge: pages often split the text around ads.
        val parent = top.parent() ?: return wrap(listOf(top))
        val parts = parent.children().filter { sib ->
            if (sib == top) return@filter true
            val ld = linkDensity(sib)
            val s = (scores[sib] ?: 0.0) * (1.0 - ld)
            s >= maxOf(6.0, bestScore * 0.25) ||
                (sib.normalName() == "p" && sib.text().length > 80 && ld < 0.25)
        }
        return wrap(parts)
    }

    private fun paragraphText(el: Element): Int =
        el.getElementsByTag("p").sumOf { p -> p.text().length.takeIf { it >= 40 } ?: 0 }

    private fun wrap(parts: List<Element>): Element {
        val box = Element("div")
        parts.forEach { box.appendChild(it.clone()) }
        val before = box.text().length
        val cleaned = box.clone()
        cleaned.getAllElements().filter { it !== cleaned && isJunk(it) }.forEach { it.remove() }
        return if (cleaned.text().length >= before * 0.5) cleaned else box
    }

    private fun isJunk(el: Element): Boolean {
        val sig = (el.className() + " " + el.id()).lowercase()
        if (sig.isBlank()) return false
        return junkTokens.any { sig.contains(it) }
    }

    private fun bonus(el: Element): Double {
        val sig = (el.className() + " " + el.id()).lowercase()
        var b = 1.0
        if (el.normalName() == "article") b *= 1.25
        if (Regex("article|body|content|story|texte|corps|post|entry|chapo|paragraph").containsMatchIn(sig)) b *= 1.15
        if (Regex("comment|related|footer|sidebar|aside|promo|teaser|newsletter").containsMatchIn(sig)) b *= 0.4
        return b
    }

    private fun linkDensity(el: Element): Double {
        val total = el.text().length
        if (total == 0) return 0.0
        val links = el.getElementsByTag("a").sumOf { it.text().length }
        return (links.toDouble() / total).coerceAtMost(1.0)
    }

    // ---------------------------------------------------------------- blocks

    private fun collectBlocks(root: Element): List<ArticleBlock> {
        val out = ArrayList<ArticleBlock>()
        walk(root, out)
        return out
    }

    private fun walk(el: Element, out: MutableList<ArticleBlock>) {
        for (child in el.children()) {
            when (child.normalName()) {
                "p" -> {
                    child.selectFirst("img")?.let { img -> imageOf(img)?.let { out += ArticleBlock(BlockType.IMAGE, url = it) } }
                    val t = child.text().trim()
                    if (t.length > 1) out += ArticleBlock(BlockType.PARAGRAPH, t)
                }
                "h2", "h3", "h4" -> child.text().trim().takeIf { it.length in 2..200 }
                    ?.let { out += ArticleBlock(BlockType.HEADING, it) }
                "blockquote" -> child.text().trim().takeIf { it.length > 1 }
                    ?.let { out += ArticleBlock(BlockType.QUOTE, it) }
                "ul", "ol" -> {
                    val items = child.children().filter { it.normalName() == "li" }
                    if (items.size in 1..25 && linkDensity(child) < 0.5) {
                        items.forEach { li -> li.text().trim().takeIf { it.length > 2 }?.let { out += ArticleBlock(BlockType.BULLET, it) } }
                    }
                }
                "figure", "picture" -> {
                    imageOf(child)?.let { src ->
                        val caption = child.selectFirst("figcaption")?.text()?.trim().orEmpty()
                        out += ArticleBlock(BlockType.IMAGE, caption, src)
                    }
                }
                "img" -> imageOf(child)?.let { out += ArticleBlock(BlockType.IMAGE, url = it) }
                "h1", "header", "time" -> Unit
                else -> {
                    val hasBlocks = child.children().any {
                        it.normalName() in setOf("p", "div", "section", "article", "ul", "ol", "h2", "h3", "h4", "figure", "blockquote", "picture", "img")
                    }
                    if (!hasBlocks && child.text().length > 80 && linkDensity(child) < 0.3) {
                        out += ArticleBlock(BlockType.PARAGRAPH, child.text().trim())
                    } else {
                        walk(child, out)
                    }
                }
            }
        }
    }

    private fun imageOf(el: Element): String? {
        val img = if (el.normalName() == "img") el else el.selectFirst("img")
        val source = el.selectFirst("source[srcset], source[data-srcset]")
        val candidates = listOfNotNull(
            img?.let { srcset(it, "data-srcset") ?: srcset(it, "srcset") },
            source?.let { srcset(it, "data-srcset") ?: srcset(it, "srcset") },
            img?.absUrl("data-src")?.ifBlank { null },
            img?.absUrl("data-lazy-src")?.ifBlank { null },
            img?.absUrl("data-original")?.ifBlank { null },
            img?.absUrl("src")?.ifBlank { null },
        )
        val url = candidates.firstOrNull {
            it.startsWith("http") && !it.endsWith(".svg") && !it.contains("pixel", true) &&
                !it.contains("logo", true) && !it.contains("avatar", true)
        } ?: return null
        val w = img?.attr("width")?.toIntOrNull()
        if (w != null && w in 1..119) return null
        return url
    }

    private fun srcset(el: Element, attr: String): String? {
        val raw = el.attr(attr).takeIf { it.isNotBlank() } ?: return null
        var best: String? = null
        var bestW = -1
        for (part in raw.split(Regex(",\\s+"))) {
            val bits = part.trim().split(Regex("\\s+"))
            val u = bits.firstOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val d = bits.getOrNull(1).orEmpty()
            val w = d.removeSuffix("w").toIntOrNull() ?: d.removeSuffix("x").toDoubleOrNull()?.times(1000)?.toInt() ?: 0
            if (w >= bestW) {
                bestW = w
                best = u
            }
        }
        return best?.let { resolve(el.baseUri(), it) }
    }

    private fun splitBody(body: String): List<ArticleBlock> {
        val chunks = body.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (chunks.size > 2) return chunks.map { ArticleBlock(BlockType.PARAGRAPH, it) }
        val sentences = body.split(Regex("(?<=[.!?…»])\\s+(?=[A-ZÀÂÉÈÊÎÔÛÇ«“\"])"))
        val out = ArrayList<ArticleBlock>()
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s.trim())
            if (sb.length > 420) {
                out += ArticleBlock(BlockType.PARAGRAPH, sb.toString())
                sb.clear()
            }
        }
        if (sb.isNotBlank()) out += ArticleBlock(BlockType.PARAGRAPH, sb.toString())
        return out
    }

    // ---------------------------------------------------------------- metadata

    private fun meta(doc: Document, vararg names: String): String? {
        for (n in names) {
            val el = doc.selectFirst("meta[property=$n], meta[name=$n]") ?: continue
            val c = el.attr("content").trim()
            if (c.isNotEmpty()) return c
        }
        return null
    }

    private fun resolve(base: String, url: String): String =
        if (url.startsWith("http")) url
        else if (url.startsWith("//")) "https:$url"
        else runCatching { URI(base).resolve(url.replace(" ", "%20")).toString() }.getOrDefault(url)

    private class Ld(
        val body: String?,
        val headline: String?,
        val image: String?,
        val author: String?,
        val date: Long?,
        val free: Boolean?,
    )

    private fun jsonLd(doc: Document): Ld? {
        for (s in doc.select("script[type=application/ld+json]")) {
            val raw = s.data().trim().takeIf { it.isNotEmpty() } ?: continue
            val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: continue
            val obj = findArticle(root, 0) ?: continue
            return Ld(
                body = obj.str("articleBody"),
                headline = obj.str("headline")?.let { FeedParser.clean(it) },
                image = imageField(obj["image"]),
                author = authorField(obj["author"]),
                date = FeedParser.parseDate(obj.str("datePublished")),
                free = (obj["isAccessibleForFree"] as? JsonPrimitive)?.let { p ->
                    p.booleanOrNull ?: p.contentOrNull?.lowercase()?.let { it == "true" }
                },
            )
        }
        return null
    }

    private fun findArticle(el: JsonElement, depth: Int): JsonObject? {
        if (depth > 4) return null
        return when (el) {
            is JsonArray -> el.firstNotNullOfOrNull { findArticle(it, depth + 1) }
            is JsonObject -> {
                val types = when (val t = el["@type"]) {
                    is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    is JsonPrimitive -> listOfNotNull(t.contentOrNull)
                    else -> emptyList()
                }
                if (types.any { it in articleTypes }) el
                else el["@graph"]?.let { findArticle(it, depth + 1) } ?: el["mainEntity"]?.let { findArticle(it, depth + 1) }
            }
            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun imageField(el: JsonElement?): String? = when (el) {
        is JsonPrimitive -> el.contentOrNull
        is JsonObject -> el.str("url") ?: el.str("contentUrl")
        is JsonArray -> el.firstNotNullOfOrNull { imageField(it) }
        else -> null
    }

    private fun authorField(el: JsonElement?): String? = when (el) {
        is JsonPrimitive -> el.contentOrNull
        is JsonObject -> el.str("name")
        is JsonArray -> el.mapNotNull { authorField(it) }.distinct().take(3).joinToString(", ").ifBlank { null }
        else -> null
    }?.takeIf { it.isNotBlank() && !it.startsWith("http") }
}
