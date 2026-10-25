package com.lifetracker.app.data

import android.content.Context
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

/**
 * Starts the map library once, with a web client that names the app (a User-Agent of its own) and asks for at most 4 tiles at a
 * time. That is what OpenFreeMap's terms ask for downloads, and it is gentle on any other provider too.
 */
object MapEngine {
    private var started = false

    /** Safe to call again; only the first call does anything. Call it on the main thread. */
    fun start(context: Context) {
        if (started) return
        val app = context.applicationContext
        val agent = userAgent(app)
        val dispatcher = Dispatcher().apply {
            maxRequests = MapStyles.OPENFREEMAP_LIMIT.parallel
            maxRequestsPerHost = MapStyles.OPENFREEMAP_LIMIT.parallel
        }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", agent).build()) }
            .build()
        // Initialise MapLibre first: installing the HTTP client can initialise its HTTP implementation,
        // which reads MapLibre's application context.
        MapLibre.getInstance(app)
        HttpRequestUtil.setOkHttpClient(client)
        started = true
    }

    /** "Kairos/0.9.42 (com.lifetracker.app; private life tracker)": names this app and its version. */
    fun userAgent(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
        return "Kairos/$version (${context.packageName}; private life tracker)"
    }
}
