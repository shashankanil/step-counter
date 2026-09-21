package dev.stepcounter.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import dev.stepcounter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Tokens never enter UI state, logs, backups, or the step database.
data class AuthProfile(val name: String, val email: String, val photo: String?, val givenName: String, val connected: Boolean) {
    val initial: String? get() = givenName.trim().firstOrNull()?.uppercase()
}

class AuthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val credentials = CredentialManager.create(context)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("step_auth", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("step_auth", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(): JSONObject? = try {
        prefs.getString("account", null)?.let {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        }
    } catch (_: Exception) { prefs.edit().clear().apply(); null }
    private fun save(data: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("account", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
    val profile: AuthProfile? get() = read()?.let {
        AuthProfile(it.getString("name"), it.getString("email"), it.optString("photo").takeIf(String::isNotBlank),
            it.optString("givenName"), it.optString("sessionToken").isNotBlank())
    }
    val idToken: String? get() = read()?.optString("idToken")?.takeIf(String::isNotBlank)
    val sessionToken: String? get() = read()?.optString("sessionToken")?.takeIf(String::isNotBlank)
    fun authorizationHeader(): String? = sessionToken?.let { "Bearer $it" }

    suspend fun signIn(activity: Context) {
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Google sign-in is not configured in this build." }
        val result = credentials.getCredential(activity, GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()).build())
        val custom = result.credential as? CustomCredential ?: error("Unexpected credential")
        check(custom.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        val google = GoogleIdTokenCredential.createFrom(custom.data)
        // A Google credential is useful locally even when the optional backend is offline.
        val claims = JSONObject(String(Base64.decode(google.idToken.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
        val identity = JSONObject().put("name", claims.optString("name", google.displayName ?: google.id))
            .put("email", claims.optString("email", google.id))
            .put("givenName", claims.optString("given_name", google.givenName.orEmpty()))
            .put("photo", claims.optString("picture", google.profilePictureUri?.toString().orEmpty()))
            .put("idToken", google.idToken).put("sessionToken", "").put("sessionMode", "local")
        withContext(Dispatchers.IO) { save(identity) }
        val session = try { if (BuildConfig.API_CONFIGURED) exchange(google.idToken) else null }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { null }
        withContext(Dispatchers.IO) {
            save(identity.put("sessionToken", session.orEmpty()).put("sessionMode", if (session == null) "local" else "cloud"))
        }
    }
    private suspend fun exchange(token: String): String = withContext(Dispatchers.IO) {
        val connection = connection("/api/auth/sign-in/social")
        try {
            connection.outputStream.use { it.write(JSONObject().put("provider", "google")
                .put("idToken", JSONObject().put("token", token)).toString().toByteArray()) }
            check(connection.responseCode in 200..299) { "Account service could not sign you in." }
            connection.getHeaderField("set-auth-token")?.takeIf(String::isNotBlank)
                ?: error("Account service did not return a session.")
        } finally { connection.disconnect() }
    }
    private fun connection(path: String): HttpURLConnection {
        val base = URI(BuildConfig.API_BASE_URL)
        require(base.scheme == "https" || (BuildConfig.DEBUG && base.scheme == "http" && base.host in listOf("10.0.2.2", "localhost")))
        return (URI(BuildConfig.API_BASE_URL.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 10_000
            instanceFollowRedirects = false; doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
    }
    // Always remove local credentials, even if remote revocation is unavailable.
    suspend fun signOut(): Boolean {
        val token = sessionToken
        withContext(Dispatchers.IO) { check(prefs.edit().clear().commit()) }
        var complete = true
        try { credentials.clearCredentialState(ClearCredentialStateRequest()) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { complete = false }
        if (token != null && BuildConfig.API_CONFIGURED) {
            try {
                withContext(Dispatchers.IO) {
                    val connection = connection("/api/auth/sign-out")
                    try {
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.outputStream.use { it.write("{}".toByteArray()) }
                        check(connection.responseCode in 200..299)
                    } finally { connection.disconnect() }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { complete = false }
        }
        return complete
    }
}
