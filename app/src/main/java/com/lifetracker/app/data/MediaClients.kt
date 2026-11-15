package com.lifetracker.app.data

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** The calls to AniList, Jikan (which reads public MyAnimeList lists) and Simkl. Each throws a [MediaException] that says what to do. */
object MediaClients {
    private const val APP_NAME = "kairos"
    private const val APP_VERSION = "1.0"
    private const val USER_AGENT = "Kairos/1.0 (personal life tracker)"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun send(request: Request, who: String): Pair<Int, String> = try {
        client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
    } catch (e: java.io.IOException) {
        throw MediaException("Could not reach $who. Check the internet and try again.", offline = true)
    }

    // ---- AniList: no sign-in, just the user name (the list must be public) ----

    private const val ANILIST = "https://graphql.anilist.co"

    private fun aniList(query: String, variables: JSONObject): String {
        val body = JSONObject().put("query", query).put("variables", variables).toString().toRequestBody(JSON)
        val request = Request.Builder().url(ANILIST).post(body)
            .addHeader("Accept", "application/json").addHeader("User-Agent", USER_AGENT).build()
        val (code, text) = send(request, "AniList")
        // AniList sends its errors as JSON with a status, so the parser can say more than the number can.
        if (code == 429) throw MediaException("AniList says too many requests. Try again in a minute.")
        if (code >= 500) throw MediaException("AniList had a problem (error $code). Try again in a minute.")
        return text
    }

    suspend fun aniListUserId(name: String): Int = withContext(Dispatchers.IO) {
        MediaParsers.aniListUserId(aniList("query(\$name:String){User(name:\$name){id name}}", JSONObject().put("name", name)))
    }

    suspend fun aniListItems(userId: Int): List<ListItem> = withContext(Dispatchers.IO) {
        val query = "query(\$id:Int){Page(perPage:50){mediaList(userId:\$id,sort:UPDATED_TIME_DESC){" +
            "id progress status updatedAt media{id type duration title{userPreferred}}}}}"
        MediaParsers.aniListItems(aniList(query, JSONObject().put("id", userId)))
    }

    // ---- MyAnimeList: Jikan reads the public list history, so no key is needed ----

    private const val JIKAN = "https://api.jikan.moe/v4"

    private suspend fun jikan(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$JIKAN$path").get().addHeader("User-Agent", USER_AGENT).build()
        for (attempt in 0..2) {
            val (code, text) = send(request, "Jikan (the service that reads MyAnimeList lists)")
            when {
                code in 200..299 -> return@withContext text
                code == 404 -> throw MediaException("MyAnimeList has no user with that name, or the list is private. Check the spelling and that the list is public.")
                code == 429 && attempt < 2 -> delay(1500L * (attempt + 1))
                code == 429 -> break
                else -> throw MediaException("Jikan (the service that reads MyAnimeList lists) had a problem (error $code). Try again later.")
            }
        }
        throw MediaException("Jikan says too many requests. Try again in a minute.")
    }

    suspend fun malUserExists(name: String): Boolean = MediaParsers.jikanUserExists(jikan("/users/" + URLEncoder.encode(name, "UTF-8")))

    suspend fun malHistory(name: String, sinceMs: Long): List<MediaEvent> {
        val encoded = URLEncoder.encode(name, "UTF-8")
        val manga = MediaParsers.jikanHistory(jikan("/users/$encoded/history?type=manga"), sinceMs)
        delay(500)
        val anime = MediaParsers.jikanHistory(jikan("/users/$encoded/history?type=anime"), sinceMs)
        return manga + anime
    }

    // ---- Simkl: your own client id, then a code typed at simkl.com ----

    private const val SIMKL = "https://api.simkl.com"

    suspend fun simklDeviceStart(clientId: String): MediaParsers.DeviceCode = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("client_id", clientId).add("scope", "media:read").build()
        val request = Request.Builder().url("$SIMKL/oauth2/device").post(form).addHeader("User-Agent", USER_AGENT).build()
        val (code, text) = send(request, "Simkl")
        if (code == 401 || code == 403) throw MediaException("Simkl did not accept that client ID. Copy it again from your Simkl app.")
        if (code !in 200..299) throw MediaException("Simkl had a problem (error $code). Try again in a minute.")
        MediaParsers.simklDeviceCode(text)
    }

    suspend fun simklPoll(clientId: String, deviceCode: String): MediaParsers.TokenPoll = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .build()
        val request = Request.Builder().url("$SIMKL/oauth2/token").post(form).addHeader("User-Agent", USER_AGENT).build()
        val (_, text) = send(request, "Simkl")
        MediaParsers.simklToken(text)
    }

    private fun simklGet(path: String, clientId: String, token: String, extra: String = ""): String {
        val url = "$SIMKL$path?client_id=${URLEncoder.encode(clientId, "UTF-8")}&app-name=$APP_NAME&app-version=$APP_VERSION$extra"
        val request = Request.Builder().url(url).get()
            .addHeader("Authorization", "Bearer $token").addHeader("User-Agent", USER_AGENT).build()
        val (code, text) = send(request, "Simkl")
        return when {
            code in 200..299 -> text
            code == 401 -> throw MediaException("Simkl no longer accepts the sign-in. Sign in again in the Simkl settings.")
            code == 429 -> throw MediaException("Simkl says too many requests. Try again later.")
            else -> throw MediaException("Simkl had a problem (error $code). Try again in a minute.")
        }
    }

    /** The cheap "did anything change" stamp; null when Simkl did not send one. */
    suspend fun simklStamp(clientId: String, token: String): String? = withContext(Dispatchers.IO) {
        MediaParsers.simklActivitiesStamp(simklGet("/sync/activities", clientId, token))
    }

    /** Everything watched that changed after [dateFrom] (an ISO time as Simkl gave it), as events newer than [sinceMs]. */
    suspend fun simklEvents(clientId: String, token: String, dateFrom: String, sinceMs: Long): List<MediaEvent> = withContext(Dispatchers.IO) {
        val extra = "&extended=full&episode_watched_at=yes&episode_runtime=yes&include_all_episodes=original" +
            "&date_from=" + URLEncoder.encode(dateFrom, "UTF-8")
        MediaParsers.simklEvents(simklGet("/sync/all-items", clientId, token, extra), sinceMs)
    }
}
