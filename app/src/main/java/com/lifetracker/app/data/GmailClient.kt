package com.lifetracker.app.data

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** The calls to Gmail (read only). Each throws a [MediaException] that says what to do. */
object GmailClient {
    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()
    }

    private fun get(url: String, token: String): String {
        val request = Request.Builder().url(url).get().addHeader("Authorization", "Bearer $token").addHeader("Accept", "application/json").build()
        val (code, text) = try {
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: java.io.IOException) {
            throw MediaException("Could not reach Gmail (${e.javaClass.simpleName}). Check the internet and try again.", offline = true)
        }
        return when {
            code in 200..299 -> text
            code == 401 -> throw MediaException("Google no longer accepts the sign-in. Press Reconnect in Gmail purchases.")
            code == 403 && (text.contains("SERVICE_DISABLED") || text.contains("accessNotConfigured") || text.contains("has not been used")) ->
                throw MediaException("The Gmail API is not switched on in your Google project. Enable it at console.cloud.google.com under APIs and Services, Library, Gmail API.")
            code == 403 -> throw MediaException("Gmail refused (403). Check that the app in your Google project may read Gmail, then Reconnect.")
            code == 429 -> throw MediaException("Gmail says too many requests. It will carry on at the next check.")
            else -> throw MediaException("Gmail had a problem (error $code). Try again in a minute.")
        }
    }

    suspend fun profileEmail(token: String): String? = withContext(Dispatchers.IO) { GmailParsers.profileEmail(get("$BASE/profile", token)) }

    suspend fun list(token: String, query: String, pageToken: String?): GmailParsers.ListPage = withContext(Dispatchers.IO) {
        var url = "$BASE/messages?maxResults=100&q=" + URLEncoder.encode(query, "UTF-8")
        if (pageToken != null) url += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8")
        GmailParsers.listPage(get(url, token))
    }

    suspend fun message(token: String, id: String): MailDoc? = withContext(Dispatchers.IO) {
        GmailParsers.message(get("$BASE/messages/${URLEncoder.encode(id, "UTF-8")}?format=full", token))
    }

    /** Only the date of an email, which is cheap; used to move on past emails already read. */
    suspend fun dateOf(token: String, id: String): Long? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(get("$BASE/messages/${URLEncoder.encode(id, "UTF-8")}?format=minimal&fields=internalDate", token)).optString("internalDate").toLongOrNull() }.getOrNull()
    }
}
