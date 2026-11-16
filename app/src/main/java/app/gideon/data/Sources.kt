package app.gideon.data

enum class SourceKind { RSS, YOUTUBE }

/**
 * A public feed. [publisher] groups several feeds of the same outlet so the
 * user can switch a whole outlet on or off in the settings.
 */
data class FeedSource(
    val publisher: String,
    val url: String,
    val kind: SourceKind,
    val category: Category,
) {
    val groupKey: String get() = "${kind.name}:$publisher"
}

object Sources {
    private fun rss(publisher: String, url: String, category: Category) =
        FeedSource(publisher, url, SourceKind.RSS, category)

    private fun yt(publisher: String, channelId: String, category: Category = Category.UNE) =
        FeedSource(
            publisher,
            "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId",
            SourceKind.YOUTUBE,
            category
        )

    val all: List<FeedSource> = listOf(
        // ---- Articles ----
        rss("Le Monde", "https://www.lemonde.fr/rss/une.xml", Category.UNE),
        rss("Le Monde", "https://www.lemonde.fr/international/rss_full.xml", Category.MONDE),
        rss("Le Monde", "https://www.lemonde.fr/politique/rss_full.xml", Category.FRANCE),
        rss("Le Monde", "https://www.lemonde.fr/economie/rss_full.xml", Category.ECO),
        rss("Le Monde", "https://www.lemonde.fr/pixels/rss_full.xml", Category.TECH),
        rss("Le Monde", "https://www.lemonde.fr/sciences/rss_full.xml", Category.SCIENCES),
        rss("Le Monde", "https://www.lemonde.fr/sport/rss_full.xml", Category.SPORT),
        rss("Le Monde", "https://www.lemonde.fr/culture/rss_full.xml", Category.CULTURE),

        rss("franceinfo", "https://www.francetvinfo.fr/titres.rss", Category.UNE),
        rss("franceinfo", "https://www.francetvinfo.fr/monde.rss", Category.MONDE),
        rss("franceinfo", "https://www.francetvinfo.fr/france.rss", Category.FRANCE),
        rss("franceinfo", "https://www.francetvinfo.fr/economie.rss", Category.ECO),
        rss("franceinfo", "https://www.francetvinfo.fr/internet.rss", Category.TECH),
        rss("franceinfo", "https://www.francetvinfo.fr/sante.rss", Category.SCIENCES),
        rss("franceinfo", "https://www.francetvinfo.fr/sports.rss", Category.SPORT),
        rss("franceinfo", "https://www.francetvinfo.fr/culture.rss", Category.CULTURE),

        rss("France 24", "https://www.france24.com/fr/rss", Category.UNE),
        rss("France 24", "https://www.france24.com/fr/europe/rss", Category.MONDE),
        rss("France 24", "https://www.france24.com/fr/france/rss", Category.FRANCE),
        rss("France 24", "https://www.france24.com/fr/%C3%A9co-tech/rss", Category.ECO),

        rss("20 Minutes", "https://www.20minutes.fr/feeds/rss-une.xml", Category.UNE),
        rss("Numerama", "https://www.numerama.com/feed/", Category.TECH),
        rss("L'Équipe", "https://dwh.lequipe.fr/api/edito/rss?path=/", Category.SPORT),

        // ---- Vidéos (flux publics YouTube) ----
        yt("franceinfo", "UCO6K_kkdP-lnSCiO3tPx7WA"),
        yt("France 24", "UCCCPCZNChQdGa9EkATeye4g", Category.MONDE),
        yt("HugoDécrypte", "UCAcAnMF0OrCtUep3Y4M-ZPw"),
        yt("Brut", "UCSKdvgqdnj72_SLggp7BDTg"),
        yt("Le Monde", "UCYpRDnhk5H8h16jpS84uqsA"),
        yt("BFMTV", "UCXwDLMDV86ldKoFVc_g8P0g", Category.FRANCE),
    )

    /** Distinct outlet groups, in display order, for the settings screen. */
    val groups: List<Pair<String, SourceKind>> =
        all.map { it.publisher to it.kind }.distinct()
}
