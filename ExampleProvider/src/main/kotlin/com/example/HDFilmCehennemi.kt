package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

// EKSİK OLAN VE SİSTEMİN ÇALIŞMASINI SAĞLAYAN KAYIT KISMI BURASIYDI:
@CloudstreamPlugin
class HDFilmCehennemiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDFilmCehennemi())
    }
}

class HDFilmCehennemi : MainAPI() {

    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"

    override val hasMainPage = true
    override val hasQuickSearch = true

    override var lang = "tr"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    // ---------------------------------------------------------
    // ANA SAYFALAR
    // ---------------------------------------------------------

    override val mainPage = mainPageOf(
        mainUrl to "Yeni Eklenen Filmler",
        "$mainUrl/yabancidiziizle-2" to "Yeni Eklenen Diziler",
        "$mainUrl/category/tavsiye-filmler-izle2" to "Tavsiye Filmler",
        "$mainUrl/imdb-7-puan-uzeri-filmler" to "IMDB 7+ Filmler",
        "$mainUrl/en-cok-yorumlananlar-1" to "En Çok Yorumlananlar",
        "$mainUrl/en-cok-begenilen-filmleri-izle" to "En Çok Beğenilenler",
        "$mainUrl/tur/aile-filmleri-izleyin-6" to "Aile Filmleri",
        "$mainUrl/tur/aksiyon-filmleri-izleyin-3" to "Aksiyon Filmleri",
        "$mainUrl/tur/animasyon-filmlerini-izleyin-4" to "Animasyon Filmleri",
        "$mainUrl/tur/belgesel-filmlerini-izle-1" to "Belgesel Filmleri",
        "$mainUrl/tur/bilim-kurgu-filmlerini-izleyin-2" to "Bilim Kurgu Filmleri",
        "$mainUrl/tur/komedi-filmlerini-izleyin-1" to "Komedi Filmleri",
        "$mainUrl/tur/korku-filmlerini-izle-2" to "Korku Filmleri",
        "$mainUrl/tur/romantik-filmleri-izle-1" to "Romantik Filmler"
    )

    // ---------------------------------------------------------
    // ANA SAYFA
    // ---------------------------------------------------------

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}/page/$page"
        val document = app.get(url).document
        val results = document.select("div.section-content a.poster").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, results, hasNext = results.isNotEmpty())
    }

    // ---------------------------------------------------------
    // ARAMA
    // ---------------------------------------------------------

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/search?q=${query}", headers = mapOf("X-Requested-With" to "fetch")).parsedSafe<SearchResults>() ?: return emptyList()
        return response.results.mapNotNull { html ->
            val document = Jsoup.parse(html)
            val title = document.selectFirst("h4.title")?.text() ?: return@mapNotNull null
            val href = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(document.selectFirst("img")?.attr("src")) ?: fixUrlNull(document.selectFirst("img")?.attr("data-src"))
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster?.replace("/thumb/", "/list/")
            }
        }
    }

    // ---------------------------------------------------------
    // ARAMA / ANA SAYFA RESULT PARSER
    // ---------------------------------------------------------

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("strong.poster-title")?.text() ?: return null
        val href = fixUrlNull(attr("href")) ?: return null
        val poster = fixUrlNull(selectFirst("img")?.attr("data-src")) ?: fixUrlNull(selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
    }

    // ---------------------------------------------------------
    // FILM / DİZİ DETAY
    // ---------------------------------------------------------

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle") ?: return null
        val poster = fixUrlNull(document.select("aside.post-info-poster img.lazyload").lastOrNull()?.attr("data-src"))
        val tags = document.select("div.post-info-genres a").map { it.text() }
        val year = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
        val description = document.selectFirst("article.post-info-content > p")?.text()?.trim()
        val rating = document.selectFirst("div.post-info-imdb-rating span")?.text()?.substringBefore("(")?.trim()?.toRatingInt()
        val actors = document.select("div.post-info-cast a").mapNotNull {
            val actorName = it.selectFirst("strong")?.text() ?: return@mapNotNull null
            val actorImage = it.selectFirst("img")?.attr("data-src")
            Actor(actorName, actorImage)
        }
        val recommendations = document.select("div.section-slider-container div.slider-slide").mapNotNull {
            val link = it.selectFirst("a") ?: return@mapNotNull null
            val recTitle = link.attr("title").ifBlank { link.text() }
            val recUrl = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
            val recPoster = fixUrlNull(it.selectFirst("img")?.attr("data-src")) ?: fixUrlNull(it.selectFirst("img")?.attr("src"))
            newMovieSearchResponse(recTitle, recUrl, TvType.Movie) { posterUrl = recPoster }
        }
        val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/")?.let { "https://www.youtube.com/embed/$it" }

        if (document.select("div.seasons").isNotEmpty()) {
            val episodes = document.select("div.seasons-tab-content a").mapNotNull {
                val episodeName = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                val episodeUrl = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val episodeNumber = Regex("""(\d+)\. ?Bölüm""").find(episodeName)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val seasonNumber = Regex("""(\d+)\. ?Sezon""").find(episodeName)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                newEpisode(episodeUrl) { name = episodeName; season = seasonNumber; episode = episodeNumber }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = poster
            this.year = year
            plot = description
            this.tags = tags
            this.rating = rating
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    // ---------------------------------------------------------
    // VIDEO PLAYER + ALTYAZI
    // ---------------------------------------------------------

    private suspend fun invokeLocalSource(sourceName: String, iframeUrl: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val script = app.get(iframeUrl, referer = "$mainUrl/").document.select("script").find { it.data().contains("sources:") }?.data() ?: return
        val packedVideo = script.let { getAndUnpack(it) }.substringAfter("file_link=\"").substringBefore("\";")
        val videoUrl = base64Decode(packedVideo)

        if (videoUrl.isNotBlank()) {
            callback(ExtractorLink(source = sourceName, name = sourceName, url = videoUrl, referer = "$mainUrl/", quality = Qualities.Unknown.value, type = INFER_TYPE))
        }

        val tracks = script.substringAfter("tracks: [").substringBefore("]")
        AppUtils.tryParseJson<List<SubtitleSource>>("[$tracks]")?.filter { it.kind == "captions" }?.forEach {
            val subtitleUrl = it.file ?: return@forEach
            val subtitleLabel = it.label ?: "Subtitle"
            subtitleCallback(SubtitleFile(subtitleLabel, fixUrl(subtitleUrl)))
        }
    }

    // ---------------------------------------------------------
    // LINK ÇÖZÜMLEME
    // ---------------------------------------------------------

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("div.alternative-links").forEach { alternative ->
            val language = alternative.attr("data-lang").uppercase()
            alternative.select("button.alternative-link").forEach { button ->
                val sourceName = button.text().replace("(HDrip Xbet)", "").trim().let { "$it $language" }
                val videoId = button.attr("data-video")
                if (videoId.isBlank()) return@forEach
                val apiResponse = app.get("$mainUrl/video/$videoId/", headers = mapOf("Content-Type" to "application/json", "X-Requested-With" to "fetch"), referer = data).text
                var iframe = Regex("""data-src=\\"([^"]+)""").find(apiResponse)?.groupValues?.getOrNull(1)?.replace("\\", "") ?: return@forEach
                if (iframe.contains("?rapidrame_id=")) {
                    iframe = "$mainUrl/playerr/" + iframe.substringAfter("?rapidrame_id=")
                }
                invokeLocalSource(sourceName, iframe, subtitleCallback, callback)
            }
        }
        return true
    }

    data class SearchResults(@JsonProperty("results") val results: List<String> = emptyList())
    data class SubtitleSource(@JsonProperty("file") val file: String? = null, @JsonProperty("label") val label: String? = null, @JsonProperty("kind") val kind: String? = null)
}

