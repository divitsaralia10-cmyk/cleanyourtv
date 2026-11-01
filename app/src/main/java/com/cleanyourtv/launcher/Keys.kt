package com.cleanyourtv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * API keys live on the TV only (never in the repo or the APK).
 * Set them from your PC with scripts/set-keys.ps1.
 */
object Keys {
    private fun prefs(c: Context) = c.getSharedPreferences("cleantv_keys", Context.MODE_PRIVATE)

    fun tmdb(c: Context): String = prefs(c).getString("tmdb", "").orEmpty()
    fun omdb(c: Context): String = prefs(c).getString("omdb", "").orEmpty()
    fun cricket(c: Context): String = prefs(c).getString("cricket", "").orEmpty()

    fun set(c: Context, name: String, value: String) {
        prefs(c).edit().putString(name, value).apply()
    }
}

class KeysReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        for (name in listOf("tmdb", "omdb", "cricket")) {
            val v = intent.getStringExtra(name)?.trim().orEmpty()
            if (v.isNotEmpty()) Keys.set(context, name, v)
        }
    }
}
