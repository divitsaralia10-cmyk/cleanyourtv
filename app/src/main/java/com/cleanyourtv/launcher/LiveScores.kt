package com.cleanyourtv.launcher

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun zone(): TimeZone = TimeZone.getTimeZone(Config.TIME_ZONE)

/** Whole days since 1970 in the configured time zone, so "today" and "tomorrow" are computed correctly. */
private fun dayNumber(millis: Long): Long = Math.floorDiv(millis + zone().getOffset(millis), 86_400_000L)

private fun fmt(pattern: String, millis: Long): String {
    val f = SimpleDateFormat(pattern, Locale.getDefault())
    f.timeZone = zone()
    return f.format(Date(millis))
}

private fun parseUtc(text: String, pattern: String): Long {
    if (text.isBlank()) return 0L
    return runCatching {
        val f = SimpleDateFormat(pattern, Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(text)?.time ?: 0L
    }.getOrDefault(0L)
}

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
    /** 0 = a followed team (India), 1 = two major teams, 2 = one major team, 3 = everything else. */
    val importance: Int,
    /** Scheduled start in epoch milliseconds, or 0 if unknown. */
    val startMillis: Long = 0L,
    val id: String = "",
) {
    fun day(): Long = if (startMillis <= 0L) 0L else dayNumber(startMillis)

    /** "Today", "Tomorrow", "Yesterday" or "Wed 5 Nov". */
    fun dayLabel(): String {
        if (startMillis <= 0L) return "Upcoming"
        return when (dayNumber(startMillis) - dayNumber(System.currentTimeMillis())) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> fmt("EEE d MMM", startMillis)
        }
    }

    fun timeLabel(): String = if (startMillis <= 0L) "" else fmt("h:mm a", startMillis)

    fun dateLabel(): String = listOf(dayLabel(), timeLabel()).filter { it.isNotBlank() }.joinToString(", ")

    /** Short form for the home card: "Today 3:30 pm", "Tomorrow" or "5 Nov". */
    fun shortWhen(): String {
        if (startMillis <= 0L) return ""
        return when (dayNumber(startMillis) - dayNumber(System.currentTimeMillis())) {
            0L -> "Today " + timeLabel()
            1L -> "Tomorrow " + timeLabel()
            else -> fmt("d MMM", startMillis)
        }
    }

    fun summary(): String =
        if (scoreA.isBlank() && scoreB.isBlank()) "$teamA  v  $teamB"
        else "$teamA $scoreA  v  $teamB $scoreB"
}

/** [error] is null on success, "NO_KEY" if a key is missing, "OFFLINE" if the request failed, else the service's message. */
data class ScoreResult(val matches: List<MatchScore>, val error: String?)

object LiveScores {
    private const val HOUR = 3_600_000L

    private var currentCache: List<MatchScore> = emptyList()
    private var currentAt = 0L
    private var scheduleCache: List<MatchScore> = emptyList()
    private var scheduleAt = 0L
    private var backoffUntil = 0L
    private var footballCache: List<MatchScore> = emptyList()
    private var footballAt = 0L

    private fun importanceOf(a: String, b: String): Int {
        if (Config.FOLLOW_TEAMS.any { a.contains(it, ignoreCase = true) || b.contains(it, ignoreCase = true) }) return 0
        val majorA = Config.MAJOR_TEAMS.any { a.contains(it, ignoreCase = true) }
        val majorB = Config.MAJOR_TEAMS.any { b.contains(it, ignoreCase = true) }
        return if (majorA && majorB) 1 else if (majorA || majorB) 2 else 3
    }

    private val comparator = Comparator<MatchScore> { a, b ->
        if (a.order != b.order) {
            a.order.compareTo(b.order)
        } else {
            when (a.order) {
                0 -> compareValuesBy(a, b, { it.importance }, { it.startMillis })
                1 -> compareValuesBy(a, b, { it.day() }, { it.importance }, { it.startMillis })
                else -> b.startMillis.compareTo(a.startMillis)
            }
        }
    }

    /** Live first, then upcoming by day (most important match first within a day), then recent results. */
    fun sorted(list: List<MatchScore>): List<MatchScore> = list.sortedWith(comparator)

    private fun parseCricket(arr: JSONArray?, out: MutableList<MatchScore>) {
        if (arr == null) return
        val now = System.currentTimeMillis()
        val finishedWords = Regex("won|draw|tied|no result|abandoned", RegexOption.IGNORE_CASE)
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            val teams = d.optJSONArray("teams") ?: continue
            if (teams.length() < 2) continue
            val a = teams.optString(0)
            val b = teams.optString(1)
            if (a.isBlank() || b.isBlank()) continue
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

            val status = d.optString("status")
            val start = parseUtc(d.optString("dateTimeGMT"), "yyyy-MM-dd'T'HH:mm:ss")
            val ended = d.optBoolean("matchEnded", false) || finishedWords.containsMatchIn(status)
            val started = d.optBoolean("matchStarted", start in 1..now)
            val order = when {
                ended -> 2
                started -> 0
                else -> 1
            }
            val series = d.optString("name").substringAfter(',', "").trim().take(60)
            val type = d.optString("matchType").uppercase(Locale.ROOT)
            out.add(
                MatchScore(
                    competition = listOf(type, series).filter { it.isNotBlank() }.joinToString("  ·  ").ifBlank { "Cricket" },
                    teamA = a,
                    teamB = b,
                    scoreA = teamScore(a),
                    scoreB = teamScore(b),
                    status = status,
                    live = order == 0,
                    order = order,
                    importance = importanceOf(a, b),
                    startMillis = start,
                    id = d.optString("id"),
                )
            )
        }
    }

    private suspend fun fetchSchedule(key: String): List<MatchScore>? {
        val base = "https://api.cricapi.com/v1/matches?apikey=$key&offset="
        val first = httpGetJson(base + "0") ?: return null
        if (first.optString("status") != "success") return null
        val out = ArrayList<MatchScore>()
        parseCricket(first.optJSONArray("data"), out)

        // The feed is paged. Also read the last two pages so upcoming fixtures are included
        // whichever end of the list they are on.
        val total = first.optJSONObject("info")?.optInt("totalRows", 0) ?: 0
        if (total > 25) {
            val last = ((total - 1) / 25) * 25
            for (offset in linkedSetOf(maxOf(0, last - 25), last)) {
                if (offset == 0) continue
                val page = httpGetJson(base + offset)
                if (page != null && page.optString("status") == "success") parseCricket(page.optJSONArray("data"), out)
            }
        }
        return out
    }

    /**
     * Cricket from CricketData (cricapi.com). The free key allows about 100 calls a day, so the live feed is
     * cached for [maxAgeMs] and the schedule for three hours.
     */
    suspend fun cricket(context: Context, maxAgeMs: Long): ScoreResult {
        val key = Keys.cricket(context)
        if (key.isBlank()) return ScoreResult(emptyList(), "NO_KEY")
        val now = System.currentTimeMillis()
        var error: String? = null

        if (now < backoffUntil) {
            error = "Daily limit reached, retrying later"
        } else {
            if (currentAt == 0L || now - currentAt >= maxAgeMs) {
                val json = httpGetJson("https://api.cricapi.com/v1/currentMatches?apikey=$key&offset=0")
                if (json == null) {
                    error = "OFFLINE"
                } else if (json.optString("status") != "success") {
                    error = json.optString("reason").ifBlank { "API_ERROR" }
                    backoffUntil = now + 30 * 60_000L
                } else {
                    val list = ArrayList<MatchScore>()
                    parseCricket(json.optJSONArray("data"), list)
                    currentCache = list
                    currentAt = now
                }
            }
            if (error == null && (scheduleAt == 0L || now - scheduleAt >= 3 * HOUR)) {
                val sched = fetchSchedule(key)
                if (sched != null) {
                    scheduleCache = sched
                    scheduleAt = now
                }
            }
        }

        // Live feed first (it has scores), then fill in fixtures that are not in it.
        val merged = (currentCache + scheduleCache).distinctBy {
            if (it.id.isBlank()) "${it.teamA}|${it.teamB}|${it.startMillis}" else it.id
        }
        val from = now - 36 * HOUR
        val to = now + 14 * 24 * HOUR
        val shown = merged.filter { it.live || it.startMillis == 0L || it.startMillis in from..to }
        return ScoreResult(sorted(shown), error)
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
                out.add(
                    MatchScore(
                        competition = league,
                        teamA = teamName(home),
                        teamB = teamName(away),
                        scoreA = if (state == "pre") "" else home.optString("score"),
                        scoreB = if (state == "pre") "" else away.optString("score"),
                        status = type?.optString("shortDetail").orEmpty(),
                        live = state == "in",
                        order = when (state) {
                            "in" -> 0
                            "pre" -> 1
                            else -> 2
                        },
                        importance = if (league == "Champions League") 1 else 2,
                        startMillis = parseUtc(e.optString("date"), "yyyy-MM-dd'T'HH:mm'Z'"),
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
