package nl.family7.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Family7CatalogRepository(appContext: Context) {
    private val context: Context = appContext.applicationContext

    private val client = Family7Http.getClient(context)

    /** Onthoudt de gevonden specialpagina's, zodat ze niet elke keer opnieuw gezocht hoeven te worden. */
    private val cache = context.getSharedPreferences("family7_catalog_cache", Context.MODE_PRIVATE)

    companion object {
        private const val BASE_URL = "https://www.family7.nl"
        private const val PLUS_HOME_URL = "$BASE_URL/plus"
        private const val PLUS_NIEUW_URL = "$BASE_URL/plus/nieuw"
        private const val PLUS_AZ_URL = "$BASE_URL/plus/a-z?title=All"

        /**
         * Laatste terugval voor de kidssectie. De app zoekt de specialpagina eerst
         * op in het menu van Family7+, zodat een naams- of adreswijziging vanzelf
         * meegaat; deze waarde wordt alleen gebruikt als er niets gevonden wordt.
         */
        private const val KIDS_FALLBACK_URL = "$BASE_URL/plus/special/Kinderprogramma%27s"

        private const val KEY_KIDS_URL = "kids_url"

        /** Rijen die de app zelf al bovenaan toont, om dubbelingen te voorkomen. */
        private val SUPPRESSED_ROW_IDS = setOf("mijn_lijst", "mijn_lijstje")

        /** Veiligheidsgrens bij het doorbladeren van gepagineerde overzichten. */
        private const val MAX_PAGES = 20
    }

    /**
     * Alle specialpagina's die Family7+ op dit moment aanbiedt, rechtstreeks uit
     * het menu gelezen. Nieuwe specials verschijnen zo vanzelf.
     */
    suspend fun getSpecials(): Result<List<CategoryRow>> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(PLUS_HOME_URL)
            val specials = doc.select("a[href*='/plus/special/']")
                .mapNotNull { a ->
                    val href = a.attr("href").trim()
                    if (href.isEmpty()) return@mapNotNull null
                    val url = if (href.startsWith("http")) href else "$BASE_URL$href"
                    val label = a.text().trim().ifEmpty {
                        java.net.URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
                    }
                    CategoryRow(id = url.substringAfterLast('/'), title = label, moreUrl = url)
                }
                .distinctBy { it.moreUrl }

            Result.success(specials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Haalt alle programma's van een willekeurige overzichtspagina, inclusief vervolgpagina's. */
    suspend fun getProgramsFrom(url: String): Result<List<ProgramItem>> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetchAllPages(url))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * De kinderprogramma's van Family7+. Het adres van de specialpagina wordt in
     * het menu opgezocht (op naam), zodat de sectie blijft werken als Family7 de
     * pagina hernoemt of verplaatst. De pagina zit achter de login.
     */
    suspend fun getKidsPrograms(): Result<List<ProgramItem>> = withContext(Dispatchers.IO) {
        try {
            val kidsUrl = resolveKidsUrl()
            val items = fetchAllPages(kidsUrl)

            if (items.isEmpty()) {
                return@withContext Result.failure(
                    Exception(
                        "Geen kinderprogramma's gevonden. Controleer of u bent ingelogd " +
                            "met een Family7 Plus account."
                    )
                )
            }
            Result.success(items)
        } catch (e: UnauthorizedException) {
            Result.failure(
                Exception("De kinderprogramma's zijn alleen zichtbaar als u bent ingelogd.")
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Zoekt de kids-specialpagina op in het menu; onthoudt wat er gevonden is. */
    private fun resolveKidsUrl(): String {
        val remembered = cache.getString(KEY_KIDS_URL, null)
        val discovered = runCatching {
            fetchDocument(PLUS_HOME_URL)
                .select("a[href*='/plus/special/']")
                .map { it.attr("href") to it.text() }
                .firstOrNull { (href, text) ->
                    val haystack = (java.net.URLDecoder.decode(href, "UTF-8") + " " + text).lowercase()
                    haystack.contains("kinder") || haystack.contains("kids") || haystack.contains("jeugd")
                }
                ?.first
                ?.let { if (it.startsWith("http")) it else "$BASE_URL$it" }
        }.getOrNull()

        if (discovered != null) {
            cache.edit().putString(KEY_KIDS_URL, discovered).apply()
            return discovered
        }
        return remembered ?: KIDS_FALLBACK_URL
    }

    /**
     * Dynamically fetches the full On Demand home layout directly from Family7+.
     * Includes:
     * - Top Featured Hero banner
     * - Latest releases ("Nieuw toegevoegd" from /plus/nieuw)
     * - Personalized "Mijn lijst"
     * - Category swimlanes (Aanbevolen, Originals, Kinderen, Bijbelstudie, Drama, etc.)
     */
    suspend fun getOnDemandHome(): Result<List<CategoryRow>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(PLUS_HOME_URL)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)
            val rows = mutableListOf<CategoryRow>()

            // 1. Featured Hero Banner
            val heroItems = mutableListOf<ProgramItem>()
            doc.select(".hero-slider_element, .hero-element, .view-on-demand-highlights .views-row, .view-highlight_programme .views-row").forEach { el ->
                parseProgramCard(el)?.let { heroItems.add(it) }
            }
            if (heroItems.isNotEmpty()) {
                rows.add(CategoryRow(id = "featured", title = "Uitgelicht", items = heroItems))
            }

            // 2. Fetch Latest Videos ("Nieuw toegevoegd") from /plus/nieuw
            try {
                val nieuwReq = Request.Builder().url(PLUS_NIEUW_URL).build()
                val nieuwResp = client.newCall(nieuwReq).execute()
                val nieuwHtml = nieuwResp.body?.string() ?: ""
                nieuwResp.close()

                val nieuwDoc = Jsoup.parse(nieuwHtml)
                val nieuwItems = parseCardsFromDoc(nieuwDoc)
                if (nieuwItems.isNotEmpty()) {
                    rows.add(
                        CategoryRow(
                            id = "nieuw_toegevoegd",
                            title = "Nieuw toegevoegd",
                            items = nieuwItems.distinctBy { it.slug }
                        )
                    )
                }
            } catch (_: Exception) {}

            // 3. Scan all category slider rows from the page
            // On Drupal Family7+, each row has a .block-view-header_element-title / h3 header and contains .view-block_element
            val sliderBlocks = doc.select(".block-view_standard, .view-block_slider, .series-on-demand-nodes, .views-element-container")
            for (block in sliderBlocks) {
                val titleEl = block.select(".block-view-header_element-title, h3, h2").firstOrNull() ?: continue
                val rowTitle = titleEl.text().trim()
                if (rowTitle.isEmpty() || rowTitle.equals("Home", ignoreCase = true)) continue

                val items = mutableListOf<ProgramItem>()
                block.select(".view-block_element-wrapper, .view-block_element, .more-series-on-demand_element").forEach { card ->
                    parseProgramCard(card)?.let { items.add(it) }
                }

                if (items.isNotEmpty()) {
                    val rowId = rowTitle.lowercase().replace(" ", "_").replace("'", "")
                    // Avoid duplicate rows
                    if (rows.none { it.id == rowId } && rowId !in SUPPRESSED_ROW_IDS) {
                        rows.add(
                            CategoryRow(
                                id = rowId,
                                title = rowTitle,
                                items = items.distinctBy { it.slug }
                            )
                        )
                    }
                }
            }

            // Fallback: If no rows extracted, load from A-Z
            if (rows.isEmpty()) {
                val allProgs = getAllAZPrograms().getOrDefault(emptyList())
                if (allProgs.isNotEmpty()) {
                    rows.add(CategoryRow(id = "all", title = "Alle Programma's", items = allProgs))
                }
            }

            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Dynamically fetches all 194+ programs from /plus/a-z?title=All in real-time.
     */
    suspend fun getAllAZPrograms(): Result<List<ProgramItem>> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetchAllPages(PLUS_AZ_URL))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Leest een overzichtspagina en volgt de pagineringslinks, zodat ook
     * programma's op vervolgpagina's meekomen. Stopt zodra een pagina niets
     * nieuws meer oplevert.
     */
    private fun fetchAllPages(startUrl: String): List<ProgramItem> {
        val collected = LinkedHashMap<String, ProgramItem>()
        var page = 0

        while (page < MAX_PAGES) {
            val url = if (page == 0) startUrl else appendPageParam(startUrl, page)
            val doc = runCatching { fetchDocument(url) }.getOrNull() ?: break

            val items = parseCardsFromDoc(doc)
            val before = collected.size
            items.forEach { collected.putIfAbsent(it.slug, it) }

            val hasMore = doc.select(".pager__item--next a, li.pager-next a, a[rel=next]").isNotEmpty()
            if (collected.size == before || !hasMore) break
            page++
        }
        return collected.values.toList()
    }

    private fun appendPageParam(url: String, page: Int): String =
        if (url.contains("?")) "$url&page=$page" else "$url?page=$page"

    private fun fetchDocument(url: String): Document {
        val req = Request.Builder()
            .url(url)
            .header("Referer", PLUS_HOME_URL)
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw UnauthorizedException()
            return Jsoup.parse(resp.body?.string() ?: "")
        }
    }

    /**
     * Search dynamically across program catalog.
     */
    suspend fun searchPrograms(query: String): Result<List<ProgramItem>> = withContext(Dispatchers.IO) {
        try {
            val allRes = getAllAZPrograms()
            val all = allRes.getOrNull() ?: emptyList()
            if (query.isBlank()) {
                return@withContext Result.success(all)
            }

            val cleanQ = query.trim().lowercase()
            val filtered = all.filter {
                it.title.lowercase().contains(cleanQ) ||
                it.slug.lowercase().contains(cleanQ)
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCardsFromDoc(doc: Document): List<ProgramItem> {
        val items = mutableListOf<ProgramItem>()
        doc.select(".view-block_element-wrapper, .view-block_element, .views-row").forEach { el ->
            parseProgramCard(el)?.let { items.add(it) }
        }
        return items
    }

    private fun parseProgramCard(card: Element): ProgramItem? {
        val a = card.select("a[href*='/plus/programmas/'], a[href*='/programmas/'], a[href*='/video/']").firstOrNull()
            ?: card.select("a").firstOrNull() ?: return null

        val href = a.attr("href")
        if (href.isEmpty() || href.startsWith("#") || href.contains("/live") || href.contains("/privacy") || href.contains("/veelgestelde")) {
            return null
        }

        // Extract precise human title from .titleProgramme or .title
        var title = card.select(".titleProgramme, .view-block_element-title, .title, h3, h4").text().trim()
        if (title.isEmpty()) {
            title = card.select("img").attr("title").ifEmpty {
                card.select("img").attr("alt")
            }
        }
        if (title.isEmpty() || title.contains("slider", ignoreCase = true)) {
            title = extractSlug(href)
                .replace("-", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }

        var img = card.select("img").attr("src")
        if (img.startsWith("/")) img = "$BASE_URL$img"
        val badge = card.select(".ribbon, .badge, .label, .view-block_element-badge").text().trim()

        return ProgramItem(
            id = href,
            slug = extractSlug(href),
            title = title,
            thumbnailUrl = img,
            badge = badge,
            url = if (href.startsWith("http")) href else "$BASE_URL$href"
        )
    }

    private fun extractSlug(url: String): String {
        return url.substringBefore("?").trimEnd('/').substringAfterLast('/')
    }

    private class UnauthorizedException : Exception("Niet ingelogd")
}
