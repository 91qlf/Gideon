package app.gideon.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    private val pub = Publisher("test", "Test", "example.com")

    @Test
    fun rssWithMediaAndCdata() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/">
<channel><title>Flux test</title><link>https://example.com</link>
<item>
  <title><![CDATA[Grève : la « mobilisation » continue]]></title>
  <link>https://example.com/a1</link>
  <description><![CDATA[<p>Un <b>résumé</b> &amp; des détails.</p>]]></description>
  <pubDate>Mon, 16 Nov 2026 10:00:00 +0100</pubDate>
  <media:content url="https://img.example.com/small.jpg" width="320" medium="image"/>
  <media:content url="https://img.example.com/big.jpg" width="1200" medium="image"><media:description>Légende</media:description></media:content>
</item>
<item>
  <title>Second</title><link>https://example.com/a2</link>
  <enclosure url="https://img.example.com/enc.jpg" type="image/jpeg" length="1"/>
  <description>Texte simple</description>
</item>
<item>
  <title>Third</title><link>https://example.com/a3</link>
  <content:encoded><![CDATA[<p><img src="https://img.example.com/inline.png"/>Corps</p>]]></content:encoded>
</item>
</channel></rss>"""
        val r = FeedParser.parse(xml.byteInputStream(), "https://example.com/rss", FeedDef("test", "rss", "TECH", "u"), pub)
        assertEquals("Flux test", r.channelTitle)
        assertEquals(3, r.items.size)
        val a = r.items[0]
        assertEquals("Grève : la « mobilisation » continue", a.title)
        assertEquals("Un résumé & des détails.", a.summary)
        assertEquals("https://img.example.com/big.jpg", a.imageUrl)
        assertEquals("TECH", a.category)
        assertEquals(1794819600000L, a.publishedAt)
        assertEquals("https://img.example.com/enc.jpg", r.items[1].imageUrl)
        assertEquals("https://img.example.com/inline.png", r.items[2].imageUrl)
    }

    @Test
    fun youtubeAtom() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
 <link rel="self" href="https://www.youtube.com/feeds/videos.xml?channel_id=X"/>
 <title>Chaîne Test</title>
 <entry>
  <id>yt:video:abc123</id>
  <yt:videoId>abc123</yt:videoId>
  <title>Vidéo 1</title>
  <link rel="alternate" href="https://www.youtube.com/shorts/abc123"/>
  <published>2026-11-16T09:00:00+00:00</published>
  <media:group>
   <media:title>Vidéo 1</media:title>
   <media:content url="https://www.youtube.com/v/abc123?version=3" type="application/x-shockwave-flash" width="640" height="390"/>
   <media:thumbnail url="https://i2.ytimg.com/vi/abc123/hqdefault.jpg" width="480" height="360"/>
   <media:description>Première ligne
Deuxième ligne</media:description>
  </media:group>
 </entry>
</feed>"""
        val r = FeedParser.parse(xml.byteInputStream(), "https://www.youtube.com", FeedDef("test", "youtube", "UNE", "u"), pub)
        assertEquals("Chaîne Test", r.channelTitle)
        val v = r.items.single()
        assertEquals("abc123", v.videoId)
        assertTrue(v.isShort)
        assertEquals("Première ligne", v.summary)
        assertEquals("yt:abc123", v.id)
    }

    @Test
    fun extractsArticleBody() {
        val para = "Ceci est un long paragraphe d'article, avec des virgules, des détails et encore du texte pour atteindre une taille réaliste."
        val html = """<html><head>
<meta property="og:image" content="/img/hero.jpg">
<meta property="og:description" content="Le chapô de l'article qui résume tout ce qui s'est passé.">
<script type="application/ld+json">{"@context":"https://schema.org","@type":"NewsArticle","headline":"Titre","author":{"@type":"Person","name":"Jeanne Martin"},"isAccessibleForFree":"False"}</script>
</head><body>
<nav><a href="/">Accueil</a><p>Menu menu menu menu menu menu menu menu menu menu menu menu</p></nav>
<article class="article">
 <h1>Titre</h1>
 <div class="article__content">
  <p>$para</p><p>$para 2</p>
  <div class="related-links"><p>À lire aussi : un autre article très intéressant sur le même sujet ici</p></div>
  <h2>Un intertitre</h2>
  <figure><img src="https://cdn.example.com/photo.jpg" width="800"><figcaption>Une légende</figcaption></figure>
  <p>$para 3</p><p>Lire aussi : autre chose</p>
  <p>Il vous reste 60% de cet article à lire. La suite est réservée aux abonnés.</p>
 </div>
</article>
<aside><p>Les plus lus, les plus lus, les plus lus, les plus lus, les plus lus</p></aside>
</body></html>"""
        val a = ArticleExtractor.extract(html, "https://example.com/news/1")
        assertNotNull(a)
        a!!
        assertEquals("https://example.com/img/hero.jpg", a.heroImage)
        assertEquals("Jeanne Martin", a.byline)
        assertTrue(a.truncated)
        val types = a.blocks.map { it.type }
        assertEquals(listOf(BlockType.PARAGRAPH, BlockType.PARAGRAPH, BlockType.HEADING, BlockType.IMAGE, BlockType.PARAGRAPH), types)
        assertEquals("Une légende", a.blocks[3].text)
        assertEquals("Le chapô de l'article qui résume tout ce qui s'est passé.", a.standfirst)
    }
}
