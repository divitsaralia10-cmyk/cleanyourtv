package com.cleanyourtv.launcher

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.util.Locale

data class MatchScore(
    val competition: String,
    val teamA: String,
    val teamB: String,
    val scoreA: String,
    val scoreB: String,
    val status: String,
    val live: Boolean,
    /** 0 = live, 1 = upcoming, 2 = finished. */
    val order: Int,
    val featured: Boolean,
) {
    fun summary(): String =
        if (scoreA.isBlank() && scoreB.isBlank()) "$teamA  v  $teamB"
        else "$teamA $scoreA  v  $teamB $scoreB"
}

/** [error] is null on success, "NO_KEY" if a key is missing, "OFFLINE" if the request failed. */
data class ScoreResult(val matches: List<MatchScore>, val error: String?)

object LiveScores {
    private var cricketCache: List<MatchScore> = emptyList()
    private var cricketAt = 0L
    private var footballCache: List<MatchScore> = emptyList()
    private var footballAt = 0L

    private fun isFeatured(a: String, b: String): Boolean =
        Config.FOLLOW_TEAMS.any { t -> a.contains(t, ignoreCase = true) || b.contains(t, ignoreCase = true) }

    private fun rank(m: MatchScore): Int = if (m.featured && m.order != 2) -1 else m.order

    /** Live and followed-team matches first, finished matches last. */
    fun sorted(list: List<MatchScore>): List<MatchScore> = list.sortedBy { rank(it) }

    /** Cricket from CricketData (cricapi.com). Free key, about 100 calls a day, so results are cached. */
    suspend fun cricket(context: Context, maxAgeMs: Long): ScoreResult {
        val key = Keys.cricket(context)
        if (key.isBlank()) return ScoreResult(emptyList(), "NO_KEY")
        val now = System.currentTimeMillis()
        if (cricketAt != 0L && now - cricketAt < maxAgeMs) return ScoreResult(cricketCache, null)

        val json = httpGetJson("https://api.cricapi.com/v1/currentMatches?apikey=$key&offset=0")
        if (json == null) return ScoreResult(cricketCache, "OFFLINE")
        if (json.optString("status") != "success") {
            return ScoreResult(cricketCache, json.optString("reason").ifBlank { "API_ERROR" })
        }

        val arr = json.optJSONArray("data")
        val out = ArrayList<MatchScore>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val d = arr.optJSONObject(i) ?: continue
                val teams = d.optJSONArray("teams") ?: continue
                if (teams.length() < 2) continue
                val a = teams.optString(0)
                val b = teams.optString(1)
                val scores = d.optJSONArray("score")

                fun teamScore(team: String): String {
                    if (scores == null) return ""
                    val parts = ArrayList<String>()
                    for (j in 0 until scores.length()) {
                        val s = scores.optJSONObject(j) ?: continue
                        val inning = s.optString("inning")
                            .replace(Regex("\\s+Inning\\s+\\d+$", RegexOption.IGNORE_CASE), "")
                        if (!inning.equals(team, ignoreCase = true)) continue
                        val wickets = s.optInt("w", -1)
                        val wk = if (wickets in 0..9) "/$wickets" else ""
                        val overs = s.optString("o")
                        parts.add("${s.optInt("r")}$wk" + if (overs.isNotBlank()) " ($overs)" else "")
                    }
                    return parts.joinToString(" & ")
                }

                val started = d.optBoolean("matchStarted")
                val ended = d.optBoolean("matchEnded")
                out.add(
                    MatchScore(
                        competition = d.optString("matchType").uppercase(Locale.ROOT).ifBlank { "Cricket" },
                        teamA = a,
                        teamB = b,
                        scoreA = teamScore(a),
                        scoreB = teamScore(b),
                        status = d.optString("status"),
                        live = started && !ended,
                        order = if (started && !ended) 0 else if (!started) 1 else 2,
                        featured = isFeatured(a, b),
                    )
                )
            }
        }
        cricketCache = sorted(out)
        cricketAt = now
        return ScoreResult(cricketCache, null)
    }

    /** Football from ESPN's public scoreboard feeds. No key needed. */
    suspend fun football(maxAgeMs: Long): ScoreResult {
        val now = System.currentTimeMillis()
        if (footballAt != 0L && now - footballAt < maxAgeMs) return ScoreResult(footballCache, null)

        val results: List<Pair<String, JSONObject?>> = coroutineScope {
            Config.FOOTBALL_LEAGUES.map { (slug, name) ->
                async {
                    name to httpGetJson("https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/scoreboard")
                }
            }.awaitAll()
        }
        if (results.all { it.second == null }) return ScoreResult(footballCache, "OFFLINE")

        val out = ArrayList<MatchScore>()
        for ((league, json) in results) {
            val events = json?.optJSONArray("events") ?: continue
            for (i in 0 until events.length()) {
                val e = events.optJSONObject(i) ?: continue
                val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                val cs = comp.optJSONArray("competitors") ?: continue
                var home: JSONObject? = null
                var away: JSONObject? = null
                for (k in 0 until cs.length()) {
                    val c = cs.optJSONObject(k) ?: continue
                    if (c.optString("homeAway") == "home") home = c else away = c
                }
                if (home == null || away == null) continue

                val type = e.optJSONObject("status")?.optJSONObject("type")
                val state = type?.optString("state").orEmpty()
                val detail = type?.optString("shortDetail").orEmpty()
                val a = teamName(home)
                val b = teamName(away)
                out.add(
                    MatchScore(
                        competition = league,
                        teamA = a,
                        teamB = b,
                        scoreA = if (state == "pre") "" else home.optString("score"),
                        scoreB = if (state == "pre") "" else away.optString("score"),
                        status = detail,
                        live = state == "in",
                        order = when (state) {
                            "in" -> 0
                            "pre" -> 1
                            else -> 2
                        },
                        featured = isFeatured(a, b),
                    )
                )
            }
        }
        footballCache = sorted(out)
        footballAt = now
        return ScoreResult(footballCache, null)
    }

    private fun teamName(c: JSONObject): String {
        val t = c.optJSONObject("team") ?: return "?"
        return t.optString("shortDisplayName").ifBlank { t.optString("displayName") }.ifBlank { "?" }
    }
}
