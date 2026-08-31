package nl.family7.tv.data

import android.content.Context
import okhttp3.Cache
import java.io.File
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences = context.getSharedPreferences("family7_cookies", Context.MODE_PRIVATE)
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    init {
        loadCookies()
    }

    private fun loadCookies() {
        val allEntries = prefs.all
        for ((host, serializedCookies) in allEntries) {
            if (serializedCookies is String && serializedCookies.isNotEmpty()) {
                val list = serializedCookies.split("||").mapNotNull { cookieStr ->
                    val url = HttpUrl.Builder().scheme("https").host(host).build()
                    Cookie.parse(url, cookieStr)
                }.toMutableList()
                cookieStore[host] = list
            }
        }
    }

    private fun saveCookies(host: String) {
        val list = cookieStore[host] ?: return
        val serialized = list.joinToString("||") { "${it.name}=${it.value}; domain=${it.domain}; path=${it.path}" }
        prefs.edit().putString(host, serialized).apply()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val current = cookieStore.getOrPut(host) { mutableListOf() }
        for (cookie in cookies) {
            current.removeAll { it.name == cookie.name }
            current.add(cookie)
        }
        saveCookies(host)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()
        for ((storedHost, list) in cookieStore) {
            if (host.endsWith(storedHost) || storedHost.endsWith(host)) {
                cookies.addAll(list)
            }
        }
        return cookies
    }

    fun clear() {
        cookieStore.clear()
        prefs.edit().clear().apply()
    }

    fun getAllCookies(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (list in cookieStore.values) {
            for (c in list) {
                map[c.name] = c.value
            }
        }
        return map
    }
}

object Family7Http {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @Volatile private var clientInstance: OkHttpClient? = null
    @Volatile private var cookieJarInstance: PersistentCookieJar? = null

    fun getClient(context: Context): OkHttpClient = synchronized(this) {
        clientInstance ?: buildClient(context).also { clientInstance = it }
    }

    fun getCookieJar(context: Context): PersistentCookieJar = synchronized(this) {
        cookieJarInstance
            ?: PersistentCookieJar(context.applicationContext).also { cookieJarInstance = it }
    }

    private fun buildClient(context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        // Disk-cache van 20 MB, zodat een koude start en het verversen tegen
        // procesdood kunnen en niet elke keer alles opnieuw over het netwerk halen.
        val httpCache = Cache(File(context.applicationContext.cacheDir, "family7_http"), 20L * 1024 * 1024)
        return OkHttpClient.Builder()
            .cache(httpCache)
            .cookieJar(getCookieJar(context))
            .addInterceptor(logging)
            // Family7 stuurt op ingelogde pagina's "no-cache"; op dit ene apparaat
            // is een korte bewaartijd prima en scheelt telkens opnieuw ophalen.
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().method == "GET") {
                    response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=60, stale-while-revalidate=600")
                        .build()
                } else {
                    response
                }
            }
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder().header("User-Agent", USER_AGENT)
                if (original.header("Referer") == null) {
                    builder.header("Referer", "https://www.family7.nl/")
                }
                chain.proceed(builder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
