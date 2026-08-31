package nl.family7.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Mijn lijst" per ingelogd account.
 *
 * Family7+ heeft geen openbaar eindpunt om de lijst van een account op te halen of
 * te wijzigen, dus de lijst wordt lokaal bewaard, met de uid van het account als
 * sleutel. Uitloggen laat de lijst staan; bij opnieuw inloggen met hetzelfde
 * account is die er dus weer.
 */
class Family7MyListRepository(appContext: Context) {
    private val prefs = appContext.applicationContext
        .getSharedPreferences("family7_my_list", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow<List<ProgramItem>>(emptyList())
    val items: StateFlow<List<ProgramItem>> = _items.asStateFlow()

    private var currentUid: String = ANONYMOUS

    /** Laadt de lijst van het opgegeven account in het geheugen. */
    suspend fun load(uid: String) = withContext(Dispatchers.IO) {
        currentUid = uid.ifBlank { ANONYMOUS }
        _items.value = read(currentUid)
    }

    fun contains(slug: String): Boolean = _items.value.any { it.slug == slug }

    suspend fun toggle(item: ProgramItem): Boolean = withContext(Dispatchers.IO) {
        val current = _items.value
        val exists = current.any { it.slug == item.slug }
        val updated = if (exists) {
            current.filterNot { it.slug == item.slug }
        } else {
            // Nieuwste bovenaan, zoals gebruikelijk in een kijklijst.
            listOf(item) + current.filterNot { it.slug == item.slug }
        }
        write(currentUid, updated)
        _items.value = updated
        !exists
    }

    private fun read(uid: String): List<ProgramItem> {
        val raw = prefs.getString(key(uid), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ProgramItem(
                    id = o.optString("id"),
                    slug = o.optString("slug"),
                    title = o.optString("title"),
                    thumbnailUrl = o.optString("thumbnailUrl"),
                    badge = o.optString("badge"),
                    url = o.optString("url")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(uid: String, items: List<ProgramItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("slug", item.slug)
                    .put("title", item.title)
                    .put("thumbnailUrl", item.thumbnailUrl)
                    .put("badge", item.badge)
                    .put("url", item.url)
            )
        }
        prefs.edit().putString(key(uid), array.toString()).apply()
    }

    private fun key(uid: String) = "list_$uid"

    companion object {
        private const val ANONYMOUS = "anon"
    }
}
