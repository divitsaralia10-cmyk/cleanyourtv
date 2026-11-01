package com.cleanyourtv.launcher

/** Personal settings. Edit these and rebuild. */
object Config {
    const val USER_NAME = "Divit Saralia"
    const val CITY = "Bhubaneswar"
    const val LATITUDE = 20.2961
    const val LONGITUDE = 85.8245

    /** Two-letter country code used for "where to watch" lookups. */
    const val REGION = "IN"

    /** Seconds without a key press before the full-screen ambient clock appears. */
    const val IDLE_SECONDS = 90L

    /** Shown first in the dock, in this order. Apps that are not installed are skipped. */
    val FAVORITE_PACKAGES: List<String> = listOf(
        "com.google.android.youtube.tv",        // YouTube
        "in.startv.hotstar",                    // JioHotstar
        "com.amazon.amazonvideo.livingroom",    // Prime Video
        "com.netflix.ninja",                    // Netflix
        "com.sonyliv",                          // SonyLIV
        "com.graymatrix.did",                   // ZEE5
        "com.spotify.tv.android",               // Spotify
        "com.crunchyroll.crunchyroid",          // Crunchyroll
        "com.mxtech.videoplayer.television",    // MX Player
    )

    /** Matches containing these team names are listed first. */
    val FOLLOW_TEAMS: List<String> = listOf("India")

    /** ESPN league slug to display name, for football scores. */
    val FOOTBALL_LEAGUES: List<Pair<String, String>> = listOf(
        "uefa.champions" to "Champions League",
        "eng.1" to "Premier League",
        "esp.1" to "La Liga",
        "ger.1" to "Bundesliga",
        "ita.1" to "Serie A",
        "fra.1" to "Ligue 1",
        "ind.1" to "ISL",
    )
}
