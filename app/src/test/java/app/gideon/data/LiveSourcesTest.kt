package app.gideon.data

import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Hits every feed of assets/sources.json and extracts two articles per outlet, then prints a
 * markdown report. Only runs when GIDEON_LIVE=1 (see .github/workflows/check-sources.yml).
 */
class LiveSourcesTest {

    @Test
    fun liveSourcesReport() {
        Assume.assumeTrue("set GIDEON_LIVE=1 to run", System.getenv("GIDEON_LIVE") == "1")
        val catalog = SourceCatalog.fromJson(File("src/main/assets/sources.json").readText())
        val pool = Executors.newFixedThreadPool(10)
        val now = System.currentTimeMillis()
        val sb = StringBuilder()

        val feedJobs = catalog.feeds.map { f ->
            f to pool.submit(Callable { FeedFetcher.fetch(f, catalog.publisher(f.publisher)!!, 25) })
        }
        sb.appendLine("## Feeds")
        sb.appendLine("| ok | publisher | kind | cat | http | items | img% | vid% | newest | channel/error | url |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
        val samples = LinkedHashMap<String, MutableList<NewsItem>>()
        var ok = 0
        for ((f, job) in feedJobs) {
            val o = runCatching { job.get(60, TimeUnit.SECONDS) }.getOrElse { FeedFetcher.Outcome(-2, null, "timeout") }
            val items = o.result?.items.orEmpty()
            if (items.isNotEmpty()) ok++
            val img = if (items.isEmpty()) 0 else items.count { it.imageUrl != null } * 100 / items.size
            val vid = if (items.isEmpty()) 0 else items.count { it.streamUrl != null } * 100 / items.size
            val newest = items.maxOfOrNull { it.publishedAt }?.let { "${(now - it) / 3_600_000}h" } ?: "-"
            val info = (o.result?.channelTitle ?: o.error ?: "").replace("|", "/").take(50)
            sb.appendLine("| ${if (items.isNotEmpty()) "OK" else "FAIL"} | ${f.publisher} | ${f.kind} | ${f.category} | ${o.httpCode} | ${items.size} | $img | $vid | $newest | $info | ${f.url} |")
            if (items.isNotEmpty() && f.kind == SourceKind.RSS) {
                val list = samples.getOrPut(f.publisher) { mutableListOf() }
                if (list.isEmpty()) list.addAll(items.take(2))
            }
        }
        sb.appendLine()
        sb.appendLine("FEEDS_OK $ok / ${catalog.feeds.size}")

        // Candidate video feeds (not in the catalogue yet): do they publish playable files?
        val candidatesFile = File("src/test/resources/video-candidates.json")
        if (candidatesFile.exists()) {
            val candidates = kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(FeedDef.serializer()), candidatesFile.readText()
            )
            sb.appendLine()
            sb.appendLine("## Video candidates")
            sb.appendLine("| publisher | http | items | vid% | sample stream | url |")
            sb.appendLine("|---|---|---|---|---|---|")
            val jobs = candidates.map { f ->
                val pub = catalog.publisher(f.publisher) ?: Publisher(f.publisher, f.publisher, "")
                f to pool.submit(Callable { FeedFetcher.fetch(f, pub, 25) })
            }
            for ((f, job) in jobs) {
                val o = runCatching { job.get(60, TimeUnit.SECONDS) }.getOrElse { FeedFetcher.Outcome(-2, null, "timeout") }
                val items = o.result?.items.orEmpty()
                val withStream = items.filter { it.streamUrl != null }
                val vid = if (items.isEmpty()) 0 else withStream.size * 100 / items.size
                sb.appendLine("| ${f.publisher} | ${o.httpCode} | ${items.size} | $vid | ${withStream.firstOrNull()?.streamUrl?.take(90) ?: (o.error ?: "")} | ${f.url} |")
            }
        }

        sb.appendLine()
        sb.appendLine("## Articles")
        sb.appendLine("| publisher | http | paras | chars | imgs | trunc | hero | lead/error | url |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|")
        val articleJobs = samples.flatMap { (pub, items) -> items.map { pub to it } }
            .map { (pub, item) -> Triple(pub, item, pool.submit(Callable { ArticleFetcher.fetch(item.link) })) }
        var good = 0
        for ((pub, item, job) in articleJobs) {
            val o = runCatching { job.get(60, TimeUnit.SECONDS) }.getOrElse { ArticleFetcher.Outcome(-2, null, "timeout") }
            val a = o.article
            if ((a?.textLength ?: 0) > 800) good++
            val paras = a?.blocks?.count { it.type == BlockType.PARAGRAPH } ?: 0
            val imgs = a?.blocks?.count { it.type == BlockType.IMAGE } ?: 0
            val lead = (a?.lead(80) ?: o.error ?: "").replace("|", "/").take(80)
            sb.appendLine("| $pub | ${o.httpCode} | $paras | ${a?.textLength ?: 0} | $imgs | ${a?.truncated ?: "-"} | ${if (a?.heroImage != null) "y" else "n"} | $lead | ${item.link.take(100)} |")
        }
        sb.appendLine()
        sb.appendLine("ARTICLES_GOOD $good / ${articleJobs.size}")
        pool.shutdown()

        val report = sb.toString()
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/live-sources.md").writeText(report)
    }
}
