package app.gideon.data

/** Network + parsing helpers shared by the app and the live CI check. Pure JVM. */
object FeedFetcher {

    class Outcome(val httpCode: Int, val result: FeedParser.Result?, val error: String?)

    fun fetch(feed: FeedDef, publisher: Publisher, limit: Int = 25): Outcome = try {
        Http.client.newCall(
            Http.request(feed.url, "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5")
        ).execute().use { response ->
            if (!response.isSuccessful) {
                Outcome(response.code, null, "HTTP ${response.code}")
            } else {
                val body = response.body ?: return@use Outcome(response.code, null, "empty body")
                val result = FeedParser.parse(body.byteStream(), response.request.url.toString(), feed, publisher, limit)
                Outcome(response.code, result, null)
            }
        }
    } catch (e: Exception) {
        Outcome(-1, null, e.javaClass.simpleName + ": " + (e.message ?: ""))
    }
}

object ArticleFetcher {

    class Outcome(val httpCode: Int, val article: Article?, val error: String?)

    fun fetch(url: String): Outcome = try {
        Http.client.newCall(Http.request(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")).execute().use { response ->
            if (!response.isSuccessful) {
                Outcome(response.code, null, "HTTP ${response.code}")
            } else {
                val body = response.body ?: return@use Outcome(response.code, null, "empty body")
                val html = body.string()
                val article = ArticleExtractor.extract(html, response.request.url.toString())
                Outcome(response.code, article, if (article == null) "no content" else null)
            }
        }
    } catch (e: Exception) {
        Outcome(-1, null, e.javaClass.simpleName + ": " + (e.message ?: ""))
    }
}
