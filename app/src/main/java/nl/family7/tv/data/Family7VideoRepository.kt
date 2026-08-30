package nl.family7.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.regex.Pattern

class Family7VideoRepository(private val context: Context) {
    private val client = Family7Http.getClient(context)

    companion object {
        private const val BASE_URL = "https://www.family7.nl"
    }

    suspend fun getProgramDetail(slug: String): Result<ProgramDetail> = withContext(Dispatchers.IO) {
        try {
            val url = if (slug.startsWith("http")) slug else "$BASE_URL/plus/programmas/$slug"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)
            val title = doc.select(".series-page-title, h1.on-demand-title, .page-title, h1").text().ifEmpty {
                slug.replace("-", " ").replaceFirstChar { it.uppercase() }
            }
            var posterUrl = doc.select(".series-page-image img, .hero-slider_element img, .main-image img").attr("src")
            if (posterUrl.startsWith("/")) posterUrl = "$BASE_URL$posterUrl"

            val description = doc.select(".series-page-description, .field--name-body, .series-description, .content-body p").text()
            val category = doc.select(".series-page-category, .field--name-field-category").text()

            val episodes = mutableListOf<EpisodeItem>()

            // Find all episode elements or video links on the program page
            doc.select(".views-row, .view-block_element, .episode-item, .series-episodes_element, a[href*='/video/']").forEachIndexed { idx, el ->
                val a = el.select("a[href*='/video/']").firstOrNull() ?: if (el.tagName() == "a" && el.attr("href").contains("/video/")) el else null
                if (a != null) {
                    val href = a.attr("href")
                    val epSlug = href.substringAfterLast("/").substringBefore("?")
                    var epTitle = el.select(".episode-title, .views-field-title, h3, h4, .title").text().ifEmpty {
                        a.select("img").attr("title").ifEmpty {
                            a.select("img").attr("alt").ifEmpty {
                                "Aflevering ${idx + 1}"
                            }
                        }
                    }
                    val epNumber = el.select(".episode-number, .views-field-field-episode-number").text().ifEmpty {
                        "${idx + 1}"
                    }
                    val epDesc = el.select(".episode-description, .views-field-body, p").text()
                    val duration = el.select(".duration, .time, .views-field-field-duration").text()
                    var epThumb = el.select("img").attr("src")
                    if (epThumb.startsWith("/")) epThumb = "$BASE_URL$epThumb"

                    episodes.add(
                        EpisodeItem(
                            id = epSlug,
                            episodeNumber = epNumber,
                            title = epTitle,
                            description = epDesc,
                            duration = duration,
                            thumbnailUrl = epThumb.ifEmpty { posterUrl },
                            videoSlug = epSlug,
                            videoUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                        )
                    )
                }
            }

            val seasons = if (episodes.isNotEmpty()) {
                listOf(SeasonInfo(seasonNumber = "1", title = "Afleveringen", episodes = episodes.distinctBy { it.videoSlug }))
            } else {
                emptyList()
            }

            Result.success(
                ProgramDetail(
                    slug = slug,
                    title = title,
                    posterUrl = posterUrl,
                    description = description,
                    category = category,
                    seasons = seasons
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveEpisodeStreamUrl(videoSlugOrUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = if (videoSlugOrUrl.startsWith("http")) videoSlugOrUrl else "$BASE_URL/video/$videoSlugOrUrl"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)
            var playerUrl = doc.select(".video-player--loader, .video-player--frame").attr("data-src")
            if (playerUrl.isEmpty()) {
                // Check iframes or video tags
                playerUrl = doc.select("iframe[src*='player.php']").attr("src")
            }

            if (playerUrl.isNotEmpty()) {
                if (playerUrl.startsWith("/")) playerUrl = "$BASE_URL$playerUrl"

                val playerReq = Request.Builder()
                    .url(playerUrl)
                    .header("Referer", BASE_URL)
                    .build()

                val playerResp = client.newCall(playerReq).execute()
                val playerHtml = playerResp.body?.string() ?: ""
                playerResp.close()

                // Extract stream m3u8 or mp4 from player page
                val streamUrl = extractStreamFromPlayerHtml(playerHtml)
                if (streamUrl.isNotEmpty()) {
                    return@withContext Result.success(streamUrl)
                }
            }

            // Fallback: search for direct m3u8 in page
            val directMatch = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*").matcher(html)
            if (directMatch.find()) {
                return@withContext Result.success(directMatch.group(0) ?: "")
            }

            Result.failure(Exception("Kon geen afspeelbare videobron vinden voor deze aflevering."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractStreamFromPlayerHtml(html: String): String {
        // Pattern 1: src: "https://highvolume08.streampartner.nl/..."
        val p1 = Pattern.compile("src:\\s*[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']")
        val m1 = p1.matcher(html)
        if (m1.find()) return m1.group(1) ?: ""

        // Pattern 2: Any m3u8 URL in the page
        val p2 = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*")
        val m2 = p2.matcher(html)
        if (m2.find()) return m2.group(0) ?: ""

        // Pattern 3: mp4 direct URL
        val p3 = Pattern.compile("https?://[^\\s\"'<>]+\\.mp4[^\\s\"'<>]*")
        val m3 = p3.matcher(html)
        if (m3.find()) return m3.group(0) ?: ""

        return ""
    }
}
