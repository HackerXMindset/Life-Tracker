package com.lifetracker.app.data

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Signing in to Comick: OAuth with PKCE through the phone's browser, using a Comick app of your own (a public app, so no secret
 * is kept). The pure parts (the PKCE values, the sign-in address, reading the answer) have no Android in them and are tested.
 */
object ComickAuth {
    const val SCHEME = "com.lifetracker.app"
    const val REDIRECT = "com.lifetracker.app:/oauth/comick"
    const val RESOURCE = "https://api.comick.dev/integrations/v1"
    const val AUTHORIZE = "https://comick.dev/api/auth/oauth2/authorize"
    const val SCOPE = "library:read offline_access"
    const val RECONNECT = "Comick no longer accepts the sign-in. Connect Comick again."
    const val ATTEMPT_MS = 10 * 60_000L

    /** One sign-in in progress: the random state and the PKCE verifier, kept until the browser comes back. */
    class Attempt(val state: String, val verifier: String, val createdMs: Long)

    private fun base64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun newAttempt(now: Long): Attempt {
        val random = SecureRandom()
        fun token(): String = ByteArray(32).also { random.nextBytes(it) }.let(::base64url)
        return Attempt(token(), token(), now)
    }

    /** The base64url SHA-256 of the verifier, without padding. */
    fun challenge(verifier: String): String = base64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun authorizeUrl(clientId: String, attempt: Attempt): String {
        val params = listOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to REDIRECT,
            "scope" to SCOPE,
            "resource" to RESOURCE,
            "state" to attempt.state,
            "code_challenge" to challenge(attempt.verifier),
            "code_challenge_method" to "S256",
        )
        return AUTHORIZE + "?" + params.joinToString("&") { enc(it.first) + "=" + enc(it.second) }
    }

    sealed class Callback {
        class Code(val code: String, val verifier: String) : Callback()
        object Denied : Callback()
        class Invalid(val message: String) : Callback()
    }

    fun queryOf(url: String): Map<String, List<String>> {
        val query = url.substringAfter('?', "").substringBefore('#')
        val out = LinkedHashMap<String, MutableList<String>>()
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val key = URLDecoder.decode(part.substringBefore('='), "UTF-8")
            val value = URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
            out.getOrPut(key) { ArrayList() } += value
        }
        return out
    }

    /** Checks what the browser sent back against the sign-in that was started: the state, how old it is, a single code. */
    fun parseCallback(url: String, attempt: Attempt?, now: Long): Callback {
        if (attempt == null || now - attempt.createdMs > ATTEMPT_MS) {
            return Callback.Invalid("That sign-in was not started here or has run out. Press Connect Comick again.")
        }
        val q = queryOf(url)
        if (q["state"]?.singleOrNull() != attempt.state) return Callback.Invalid("That sign-in did not match. Press Connect Comick again.")
        val error = q["error"]?.firstOrNull()
        if (error != null) {
            return if (error == "access_denied") Callback.Denied else Callback.Invalid("Comick said no ($error). Check the app settings at Comick and try again.")
        }
        val code = q["code"]?.singleOrNull()
        if (code.isNullOrEmpty()) return Callback.Invalid("Comick did not send a sign-in code. Press Connect Comick again.")
        return Callback.Code(code, attempt.verifier)
    }

    private val refreshMutex = Mutex()

    /** A working access token: the saved one, or a new one got with the refresh token (one refresh at a time, saved before use). */
    suspend fun accessToken(context: Context, now: Long = System.currentTimeMillis()): String = refreshMutex.withLock {
        val accounts = MediaAccounts(context)
        if (accounts.comickAccess.isNotBlank() && accounts.comickExpiryMs > now + 60_000L) return@withLock accounts.comickAccess
        val refresh = accounts.comickRefresh
        if (refresh.isBlank()) {
            accounts.clearComickTokens()
            throw MediaException("Comick's sign-in ran out and gave no way to renew it. Connect Comick again, and keep ongoing access ticked.")
        }
        try {
            val tokens = MediaClients.comickRefresh(accounts.comickClientId, refresh)
            accounts.saveComickTokens(tokens, now)
            tokens.access
        } catch (e: MediaException) {
            if (e.message == RECONNECT) accounts.clearComickTokens()
            throw e
        }
    }

    /** Called with the address the browser opened the app with. Saves the result for the settings page to show. */
    suspend fun handleCallback(context: Context, url: String, now: Long = System.currentTimeMillis()) {
        val accounts = MediaAccounts(context)
        val attempt = accounts.comickAttempt
        accounts.comickAttempt = null
        when (val result = parseCallback(url, attempt, now)) {
            is Callback.Denied -> accounts.comickMessage = "Connection cancelled."
            is Callback.Invalid -> accounts.comickMessage = result.message
            is Callback.Code -> try {
                val tokens = MediaClients.comickExchange(accounts.comickClientId, result.code, result.verifier)
                if (!tokens.scope.split(' ').contains("library:read")) {
                    throw MediaException("Comick did not give read access to your library. Connect again and leave it ticked.")
                }
                accounts.saveComickTokens(tokens, now)
                accounts.comickName = runCatching { MediaClients.comickMe(tokens.access) }.getOrNull().orEmpty()
                val state = MediaSyncState(context)
                state.clear(MediaService.COMICK)
                state.lastSyncMs = 0L
                accounts.comickMessage = if (tokens.refresh == null) {
                    "Comick is connected, but ongoing access was declined, so it needs connecting again in 15 minutes."
                } else {
                    "Comick is connected."
                }
                MediaSyncWorker.schedule(context)
                MediaSync.run(context, now)
            } catch (e: MediaException) {
                accounts.comickMessage = e.message ?: "Could not finish connecting Comick."
            }
        }
    }
}
