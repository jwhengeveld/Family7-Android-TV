package nl.family7.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Family7CatalogRepository(private val context: Context) {
    private val client = Family7Http.getClient(context)

    companion object {
        private const val BASE_URL = "https://www.family7.nl"
        private const val ONDEMAND_HOME_URL = "$BASE_URL/ondemandkijken"
    }

    suspend fun getOnDemandHome(): Result<List<CategoryRow>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(ONDEMAND_HOME_URL)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)
            val rows = mutableListOf<CategoryRow>()

            // 1. Featured / Highlights row if available
            val heroItems = mutableListOf<ProgramItem>()
            doc.select(".hero-slider_element, .hero-element, .view-on-demand-highlights .views-row").forEach { el ->
                val link = el.select("a").firstOrNull() ?: return@forEach
                val href = link.attr("href")
                val title = el.select("h2, h3, .title, img").attr("alt").ifEmpty {
                    el.select("h2, h3, .title").text()
                }
                var img = el.select("img").attr("src")
                if (img.startsWith("/")) img = "$BASE_URL$img"
                val badge = el.select(".ribbon, .badge, .label").text()

                if (href.isNotEmpty() && title.isNotEmpty()) {
                    heroItems.add(
                        ProgramItem(
                            id = href,
                            slug = extractSlug(href),
                            title = title,
                            thumbnailUrl = img,
                            badge = badge,
                            url = if (href.startsWith("http")) href else "$BASE_URL$href"
                        )
                    )
                }
            }
            if (heroItems.isNotEmpty()) {
                rows.add(
                    CategoryRow(
                        id = "featured",
                        title = "Uitgelicht",
                        items = heroItems
                    )
                )
            }

            // 2. Scan all category sections on the ondemand home page
            // Drupal outputs blocks with .block-view_header / .series-on-demand-*
            val sectionHeaders = doc.select(".block-view-header_element-title, h3.block-view-header_element-title, h2.on-demand-title, .view-header h3")
            for (headerEl in sectionHeaders) {
                val rowTitle = headerEl.text().trim()
                if (rowTitle.isEmpty()) continue

                // Find parent container or following slider container
                val parentContainer = headerEl.parents().firstOrNull { p ->
                    p.select(".view-block_element, .more-series-on-demand_element, .views-row, a[href*='/plus/programmas/']").isNotEmpty()
                } ?: headerEl.parent()

                val moreLink = parentContainer?.select(".more-link a")?.attr("href") ?: ""
                val items = mutableListOf<ProgramItem>()

                parentContainer?.select(".view-block_element, .more-series-on-demand_element, .views-row, .slider-default_element")?.forEach { card ->
                    val a = card.select("a").firstOrNull() ?: return@forEach
                    val href = a.attr("href")
                    if (!href.contains("/plus/programmas/") && !href.contains("/video/")) return@forEach

                    var title = card.select("img").attr("title").ifEmpty {
                        card.select("img").attr("alt").ifEmpty {
                            card.select(".title, h4, h3").text()
                        }
                    }
                    var img = card.select("img").attr("src")
                    if (img.startsWith("/")) img = "$BASE_URL$img"
                    val badge = card.select(".ribbon, .badge, .teaser-ribbon").text()

                    if (title.isEmpty()) {
                        title = extractSlug(href).replace("-", " ").replaceFirstChar { it.uppercase() }
                    }

                    if (href.isNotEmpty()) {
                        items.add(
                            ProgramItem(
                                id = href,
                                slug = extractSlug(href),
                                title = title,
                                thumbnailUrl = img,
                                badge = badge,
                                url = if (href.startsWith("http")) href else "$BASE_URL$href"
                            )
                        )
                    }
                }

                if (items.isNotEmpty()) {
                    rows.add(
                        CategoryRow(
                            id = rowTitle.lowercase().replace(" ", "_"),
                            title = rowTitle,
                            moreUrl = if (moreLink.startsWith("/")) "$BASE_URL$moreLink" else moreLink,
                            items = items.distinctBy { it.slug }
                        )
                    )
                }
            }

            // Fallback: If no rows extracted, parse all program links
            if (rows.isEmpty()) {
                val allProgs = parseProgramsFromElements(doc.select("a[href*='/plus/programmas/']"))
                if (allProgs.isNotEmpty()) {
                    rows.add(CategoryRow(id = "all", title = "On Demand Programma's", items = allProgs))
                }
            }

            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategoryItems(pathOrUrl: String, titleFallback: String): Result<CategoryRow> = withContext(Dispatchers.IO) {
        try {
            val url = if (pathOrUrl.startsWith("http")) pathOrUrl else "$BASE_URL$pathOrUrl"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)
            val title = doc.select("h1, .page-title").text().ifEmpty { titleFallback }
            val items = parseProgramsFromDoc(doc)

            Result.success(
                CategoryRow(
                    id = extractSlug(url),
                    title = title,
                    moreUrl = url,
                    items = items
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAZCatalog(letter: String = "All"): Result<List<ProgramItem>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/plus/a-z?title=$letter"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)
            val items = parseProgramsFromDoc(doc)
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun search(query: String): Result<List<ProgramItem>> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/plus/search?search_api_fulltext=$encoded"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)
            val items = parseProgramsFromDoc(doc)
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseProgramsFromDoc(doc: org.jsoup.nodes.Document): List<ProgramItem> {
        val list = mutableListOf<ProgramItem>()
        doc.select(".view-content .views-row, .view-block_element, .views-view-grid .views-col, .search-result").forEach { el ->
            val a = el.select("a[href*='/plus/programmas/'], a[href*='/video/']").firstOrNull() ?: el.select("a").firstOrNull() ?: return@forEach
            val href = a.attr("href")
            var title = el.select("img").attr("title").ifEmpty {
                el.select("img").attr("alt").ifEmpty {
                    el.select(".views-field-title, h3, h4, .title").text()
                }
            }
            if (title.isEmpty()) {
                title = extractSlug(href).replace("-", " ").replaceFirstChar { it.uppercase() }
            }
            var img = el.select("img").attr("src")
            if (img.startsWith("/")) img = "$BASE_URL$img"
            val badge = el.select(".ribbon, .badge, .teaser-ribbon").text()

            if (href.isNotEmpty()) {
                list.add(
                    ProgramItem(
                        id = href,
                        slug = extractSlug(href),
                        title = title,
                        thumbnailUrl = img,
                        badge = badge,
                        url = if (href.startsWith("http")) href else "$BASE_URL$href"
                    )
                )
            }
        }
        return list.distinctBy { it.slug }
    }

    private fun parseProgramsFromElements(elements: org.jsoup.select.Elements): List<ProgramItem> {
        val list = mutableListOf<ProgramItem>()
        for (a in elements) {
            val href = a.attr("href")
            val slug = extractSlug(href)
            var title = a.select("img").attr("title").ifEmpty {
                a.select("img").attr("alt").ifEmpty {
                    a.text().ifEmpty { slug.replace("-", " ").replaceFirstChar { it.uppercase() } }
                }
            }
            var img = a.select("img").attr("src")
            if (img.startsWith("/")) img = "$BASE_URL$img"

            list.add(
                ProgramItem(
                    id = href,
                    slug = slug,
                    title = title,
                    thumbnailUrl = img,
                    url = if (href.startsWith("http")) href else "$BASE_URL$href"
                )
            )
        }
        return list.distinctBy { it.slug }
    }

    private fun extractSlug(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#").trimEnd('/')
        return clean.substringAfterLast("/")
    }
}
