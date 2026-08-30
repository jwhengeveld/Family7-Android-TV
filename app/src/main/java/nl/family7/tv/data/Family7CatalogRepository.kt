package nl.family7.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Family7CatalogRepository(private val context: Context) {
    private val client = Family7Http.getClient(context)

    companion object {
        private const val BASE_URL = "https://www.family7.nl"
        private const val PLUS_HOME_URL = "$BASE_URL/plus"
        private const val PLUS_NIEUW_URL = "$BASE_URL/plus/nieuw"
        private const val PLUS_AZ_URL = "$BASE_URL/plus/a-z?title=All"
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
                    if (rows.none { it.id == rowId }) {
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
            val req = Request.Builder()
                .url(PLUS_AZ_URL)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)
            val items = parseCardsFromDoc(doc)

            Result.success(items.distinctBy { it.slug })
        } catch (e: Exception) {
            Result.failure(e)
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
}
