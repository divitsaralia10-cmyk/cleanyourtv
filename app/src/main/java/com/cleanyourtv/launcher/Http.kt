package com.cleanyourtv.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Simple GET returning the body, or null on any network or HTTP error. */
suspend fun httpGet(url: String, bearer: String? = null): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "CleanTV/0.3")
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

suspend fun httpGetJson(url: String, bearer: String? = null): JSONObject? =
    httpGet(url, bearer)?.let { body -> runCatching { JSONObject(body) }.getOrNull() }
