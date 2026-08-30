package nl.family7.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

class Family7AuthRepository(private val context: Context) {
    private val client = Family7Http.getClient(context)
    private val cookieJar = Family7Http.getCookieJar(context)
    private val authPrefs: SharedPreferences = context.getSharedPreferences("family7_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_SAVED_USERNAME = "saved_username"
        private const val KEY_UID = "user_uid"
        private const val LOGIN_URL = "https://www.family7.nl/user/login"
    }

    suspend fun login(usernameOrEmail: String, pass: String, rememberMe: Boolean = true): Result<UserSession> = withContext(Dispatchers.IO) {
        try {
            // Step 1: GET /user/login to extract form_build_id
            val getReq = Request.Builder()
                .url(LOGIN_URL)
                .build()

            val getResp = client.newCall(getReq).execute()
            val getHtml = getResp.body?.string() ?: ""
            getResp.close()

            val doc = Jsoup.parse(getHtml)
            val formBuildId = doc.select("input[name=form_build_id]").attr("value")

            // Step 2: POST /user/login with credentials and form_build_id
            val formBodyBuilder = FormBody.Builder()
                .add("name", usernameOrEmail.trim())
                .add("pass", pass)
                .add("form_id", "user_login_form")
                .add("op", "Inloggen")

            if (formBuildId.isNotEmpty()) {
                formBodyBuilder.add("form_build_id", formBuildId)
            }
            if (rememberMe) {
                formBodyBuilder.add("persistent_login", "1")
            }

            val postReq = Request.Builder()
                .url(LOGIN_URL)
                .post(formBodyBuilder.build())
                .build()

            val postResp = client.newCall(postReq).execute()
            val postHtml = postResp.body?.string() ?: ""
            val finalUrl = postResp.request.url.toString()
            postResp.close()

            // Step 3: Check if login was successful
            // On success, Drupal redirects to /user/{uid} or /plus/..., or user-logged-in class is present in HTML
            val cookies = cookieJar.getAllCookies()
            val hasSessionCookie = cookies.keys.any { it.startsWith("SSESS") || it.startsWith("SESS") }
            val isSuccess = hasSessionCookie || finalUrl.contains("/user/") || postHtml.contains("user-logged-in") || postHtml.contains("Mijn account")

            if (isSuccess) {
                val uidMatch = Regex("/user/(\\d+)").find(finalUrl)
                val uid = uidMatch?.groupValues?.get(1) ?: ""

                authPrefs.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .putString(KEY_SAVED_USERNAME, usernameOrEmail.trim())
                    .putString(KEY_UID, uid)
                    .apply()

                Result.success(
                    UserSession(
                        isLoggedIn = true,
                        username = usernameOrEmail.trim(),
                        email = usernameOrEmail.trim(),
                        uid = uid,
                        cookies = cookies
                    )
                )
            } else {
                // Parse Drupal error message if available
                val errorDoc = Jsoup.parse(postHtml)
                val errorMsg = errorDoc.select(".messages--error, .alert-danger, .messages.error").text().ifEmpty {
                    "Inloggen mislukt. Controleer uw e-mailadres en wachtwoord."
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Verbindingsfout: ${e.localizedMessage ?: "Kon geen verbinding maken met Family7"}"))
        }
    }

    suspend fun checkSession(): UserSession = withContext(Dispatchers.IO) {
        val wasLoggedIn = authPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val savedUser = authPrefs.getString(KEY_SAVED_USERNAME, "") ?: ""
        val savedUid = authPrefs.getString(KEY_UID, "") ?: ""
        val cookies = cookieJar.getAllCookies()
        val hasSessionCookie = cookies.keys.any { it.startsWith("SSESS") || it.startsWith("SESS") }

        if (wasLoggedIn && hasSessionCookie) {
            UserSession(
                isLoggedIn = true,
                username = savedUser,
                email = savedUser,
                uid = savedUid,
                cookies = cookies
            )
        } else {
            UserSession(isLoggedIn = false)
        }
    }

    fun logout() {
        cookieJar.clear()
        authPrefs.edit().clear().apply()
    }

    fun getSavedUsername(): String {
        return authPrefs.getString(KEY_SAVED_USERNAME, "") ?: ""
    }
}
