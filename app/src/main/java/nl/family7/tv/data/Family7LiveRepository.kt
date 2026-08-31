package nl.family7.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.regex.Pattern

class Family7LiveRepository(appContext: Context) {
    private val context: Context = appContext.applicationContext

    private val client = Family7Http.getClient(context)

    /**
     * Onthoudt de laatst werkende speler- en stream-URL. Streampartner wisselt
     * regelmatig van host, dus een vaste URL in de code veroudert; een geleerde
     * waarde uit een eerdere sessie is een betere noodgreep.
     */
    private val cache = context.getSharedPreferences("family7_live_cache", Context.MODE_PRIVATE)

    companion object {
        private const val LIVE_PAGE_URL = "https://www.family7.nl/plus/live"
        private const val KEY_LAST_PLAYER_URL = "last_player_url"
        private const val KEY_LAST_STREAM_URL = "last_stream_url"
    }

    suspend fun getLiveInfo(): Result<LiveStreamInfo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(LIVE_PAGE_URL)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            resp.close()

            val doc = Jsoup.parse(html)

            // Extract TV Guide "NU OP TV" metadata
            val currentProgTitle = doc.select(".tv-guide-teaser_title, .tv-guide-teaser h2, .tv-guide-teaser h3").text().ifEmpty {
                "Family7 Live Uitzending"
            }
            val timeRange = doc.select(".tv-guide-teaser_time, .tv-guide-teaser--info-first p:nth-child(2)").text()
            val description = doc.select(".tv-guide-teaser_description, .tv-guide-teaser--info-second p").text()
            
            var imageUrl = doc.select(".tv-guide-teaser--image img").attr("src")
            if (imageUrl.startsWith("/")) {
                imageUrl = "https://www.family7.nl$imageUrl"
            }

            // De speler-URL wordt van de pagina zelf gehaald, zodat een wijziging
            // bij Family7 of Streampartner meteen wordt overgenomen.
            val playerUrl = discoverPlayerUrl(doc, html)
            if (playerUrl.isNotEmpty()) {
                cache.edit().putString(KEY_LAST_PLAYER_URL, playerUrl).apply()
            }

            val streamUrl = resolveLiveStreamUrl(playerUrl)
                .ifEmpty { resolveLiveStreamUrl(cache.getString(KEY_LAST_PLAYER_URL, "").orEmpty()) }
                .ifEmpty { firstM3u8(html) }
                .ifEmpty { cache.getString(KEY_LAST_STREAM_URL, "").orEmpty() }

            if (streamUrl.isNotEmpty()) {
                cache.edit().putString(KEY_LAST_STREAM_URL, streamUrl).apply()
            }

            Result.success(
                LiveStreamInfo(
                    title = "Family7 Live TV",
                    currentProgram = currentProgTitle,
                    timeRange = timeRange,
                    imageUrl = imageUrl,
                    description = description,
                    streamUrl = streamUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Zoekt de speler-URL op de livepagina. Probeert achtereenvolgens de bekende
     * Drupal-containers, elke iframe, en als laatste elke Streampartner-verwijzing
     * in de ruwe HTML, zodat een gewijzigde opmaak niet meteen alles breekt.
     */
    private fun discoverPlayerUrl(doc: org.jsoup.nodes.Document, html: String): String {
        val candidates = buildList {
            add(doc.select(".video-player--loader, .video-player--frame").attr("data-src"))
            add(doc.select("[data-src*='player']").attr("data-src"))
            add(doc.select("iframe[src*='player']").attr("src"))
            add(doc.select("iframe[src*='streampartner']").attr("src"))
            add(doc.select("iframe[src]").attr("src"))
            val raw = Pattern.compile("https?://[^\\s\"'<>]*streampartner\\.nl/[^\\s\"'<>]+")
                .matcher(html)
            if (raw.find()) add(raw.group(0) ?: "")
        }

        return candidates
            .firstOrNull { it.isNotBlank() }
            ?.let { if (it.startsWith("/")) "https://www.family7.nl$it" else it }
            .orEmpty()
    }

    private fun firstM3u8(html: String): String {
        val m = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*").matcher(html)
        return if (m.find()) m.group(0) ?: "" else ""
    }

    private fun resolveLiveStreamUrl(playerUrl: String): String {
        if (playerUrl.isBlank()) return ""
        return try {
            val playerReq = Request.Builder()
                .url(playerUrl)
                .header("Referer", "https://www.family7.nl/")
                .build()

            val playerResp = client.newCall(playerReq).execute()
            val playerHtml = playerResp.body?.string() ?: ""
            playerResp.close()

            val streams = decodeRecursive(playerHtml, 0)
            streams.firstOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun decodeRecursive(text: String, depth: Int): List<String> {
        if (depth > 6) return emptyList()
        val results = mutableListOf<String>()

        // Check for direct m3u8 URL in current text
        val m3u8Matcher = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*").matcher(text)
        while (m3u8Matcher.find()) {
            results.add(m3u8Matcher.group(0) ?: "")
        }

        // Check for eval(function(w,i,s,e){...}('...')) pattern
        val evalPattern = Pattern.compile("eval\\(function\\(w,i,s,e\\)\\{.*?\\}\\((.*?)\\)\\)", Pattern.DOTALL)
        val matcher = evalPattern.matcher(text)
        while (matcher.find()) {
            val argsRaw = matcher.group(1) ?: continue
            val args = parseJsStringArgs(argsRaw)
            if (args.isNotEmpty()) {
                val unpacked = if (args.size == 4 && args[1].isEmpty() && args[2].isEmpty() && args[3].isEmpty()) {
                    unpackBase36(args[0])
                } else {
                    val w = args.getOrElse(0) { "" }
                    val i = args.getOrElse(1) { "" }
                    val s = args.getOrElse(2) { "" }
                    val e = args.getOrElse(3) { "" }
                    unpackStreampartner(w, i, s, e)
                }
                results.addAll(decodeRecursive(unpacked, depth + 1))
            }
        }
        return results
    }

    private fun parseJsStringArgs(raw: String): List<String> {
        val list = mutableListOf<String>()
        val p = Pattern.compile("'(.*?)'")
        val m = p.matcher(raw)
        while (m.find()) {
            list.add(m.group(1) ?: "")
        }
        return list
    }

    private fun unpackBase36(w: String): String {
        val sb = StringBuilder()
        var s = 0
        while (s < w.length) {
            val chunk = w.substring(s, minOf(s + 2, w.length))
            try {
                val code = chunk.toInt(36)
                sb.append(code.toChar())
            } catch (_: Exception) {}
            s += 2
        }
        return sb.toString()
    }

    private fun unpackStreampartner(w: String, i: String, s: String, e: String): String {
        var lIll = 0
        var ll1I = 0
        var Il1l = 0
        val ll1l = StringBuilder()
        val l1lI = StringBuilder()

        while (true) {
            if (lIll < 5) {
                if (lIll < w.length) l1lI.append(w[lIll])
            } else if (lIll < w.length) {
                ll1l.append(w[lIll])
            }
            lIll++

            if (ll1I < 5) {
                if (ll1I < i.length) l1lI.append(i[ll1I])
            } else if (ll1I < i.length) {
                ll1l.append(i[ll1I])
            }
            ll1I++

            if (Il1l < 5) {
                if (Il1l < s.length) l1lI.append(s[Il1l])
            } else if (Il1l < s.length) {
                ll1l.append(s[Il1l])
            }
            Il1l++

            if (w.length + i.length + s.length + e.length == ll1l.length + l1lI.length + e.length) break
        }

        val lI1l = ll1l.toString()
        val I1lI = l1lI.toString()
        if (I1lI.isEmpty()) return ""

        ll1I = 0
        val l1ll = StringBuilder()
        var pos = 0
        while (pos < lI1l.length) {
            var ll11 = -1
            if (I1lI[ll1I].code % 2 != 0) ll11 = 1
            val chunk = lI1l.substring(pos, minOf(pos + 2, lI1l.length))
            try {
                val charCode = chunk.toInt(36) - ll11
                l1ll.append(charCode.toChar())
            } catch (_: Exception) {}
            ll1I++
            if (ll1I >= I1lI.length) ll1I = 0
            pos += 2
        }
        return l1ll.toString()
    }
}
