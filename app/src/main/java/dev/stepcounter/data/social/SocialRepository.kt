package dev.stepcounter.data.social

import android.content.Context
import dev.stepcounter.BuildConfig
import dev.stepcounter.data.auth.AuthRepository
import dev.stepcounter.domain.StepSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** All preferences, consent and cached comparisons are scoped to the current Google identity. */
class SocialRepository(context: Context) {
    private val auth = AuthRepository(context)
    private val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)
    private val account = auth.profile?.email.orEmpty()
    private val accountKey = java.security.MessageDigest.getInstance("SHA-256")
        .digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun key(name: String) = "$accountKey:$name"
    val enabled get() = account.isNotBlank() && prefs.getBoolean(key("enabled"), false)
    val pendingDisable get() = prefs.getBoolean(key("pendingDisable"), false)
    val target get() = prefs.getString(key("target"), "").orEmpty()
    val targetName get() = prefs.getString(key("targetName"), "").orEmpty()
    val cachedComparison: JSONObject? get() = if (account.isBlank()) null else
        prefs.getString(key("comparison"), null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun selectTarget(value: String, name: String) {
        prefs.edit().putString(key("target"), value).putString(key("targetName"), name).remove(key("comparison")).apply()
    }
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        check(auth.profile?.email.orEmpty() == account) { "Account changed. Reopen Social." }
        val token = auth.authorizationHeader() ?: error("Connect your Google account to the backend in You.")
        check(BuildConfig.API_CONFIGURED) { "Set API_BASE_URL to a reachable backend and rebuild." }
        val base = URI(BuildConfig.API_BASE_URL)
        check(base.scheme == "https" || (BuildConfig.DEBUG && base.scheme == "http" && base.host in listOf("10.0.2.2", "localhost"))) {
            "Use an HTTPS backend URL."
        }
        val conn = URI(BuildConfig.API_BASE_URL.trimEnd('/') + "/api" + path).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method; conn.connectTimeout = 7000; conn.readTimeout = 7000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Authorization", token)
            if (body != null) {
                conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = conn.responseCode
            val raw = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            check(status in 200..299) {
                if (status == 401) "Session expired. Reconnect your Google account in You."
                else json.optString("error", "Cloud service unavailable. Try again.")
            }
            json
        } catch (e: java.io.IOException) {
            error("Cloud service unreachable. Check your connection and API_BASE_URL. Refresh to confirm any changes; no offline actions are queued.")
        } finally { conn.disconnect() }
    }
    suspend fun setSharing(value: Boolean) {
        // Turning off immediately stops this device's uploads, even without a connection.
        if (!value) prefs.edit().putBoolean(key("enabled"), false).putBoolean(key("pendingDisable"), true)
            .remove(key("comparison")).commit()
        request("/sync", "PUT", JSONObject().put("enabled", value))
        prefs.edit().putBoolean(key("enabled"), value).putBoolean(key("pendingDisable"), false).apply()
    }
    suspend fun sync(summary: StepSummary) {
        if (pendingDisable) setSharing(false)
        if (!enabled) return
        val days = JSONArray()
        summary.days.filterKeys { it >= summary.today.minusDays(29) && it <= summary.today }.forEach { (date, count) ->
            days.put(JSONObject().put("date", date.toString()).put("steps", count))
        }
        request("/steps", "PUT", JSONObject().put("days", days))
    }
    suspend fun compare(): JSONObject {
        check(target.isNotBlank()) { "Choose an accepted friend or group below." }
        val result = request("/compare?$target&date=${java.time.LocalDate.now()}")
        prefs.edit().putString(key("comparison"), result.put("fetchedAt", System.currentTimeMillis()).toString()).apply()
        return result
    }
}
