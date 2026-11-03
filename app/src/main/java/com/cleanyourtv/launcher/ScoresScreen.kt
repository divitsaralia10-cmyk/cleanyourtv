package com.cleanyourtv.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private sealed interface ScoreRow {
    data class Header(val text: String) : ScoreRow
    data class Match(val m: MatchScore) : ScoreRow
}

/** Groups matches under "Live now", then one heading per day, then "Results". */
private fun buildRows(matches: List<MatchScore>): List<ScoreRow> {
    val rows = ArrayList<ScoreRow>()
    var last = ""
    for (m in matches) {
        val header = when (m.order) {
            0 -> "Live now"
            1 -> m.dayLabel()
            else -> "Results"
        }
        if (header != last) {
            rows.add(ScoreRow.Header(header))
            last = header
        }
        rows.add(ScoreRow.Match(m))
    }
    return rows
}

@Composable
fun ScoresScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var cricket by remember { mutableStateOf<ScoreResult?>(null) }
    var football by remember { mutableStateOf<ScoreResult?>(null) }
    val firstTab = remember { FocusRequester() }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstTab.requestFocus() }
    }

    // Refresh every minute while this screen is open. Cricket is cached for 10 minutes to respect its free limit.
    LaunchedEffect(Unit) {
        while (true) {
            cricket = LiveScores.cricket(context, 10 * 60_000L)
            football = LiveScores.football(60_000L)
            delay(60_000L)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BasicText(
                "Live Scores",
                style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.size(12.dp))
            Chip("Cricket", selected = tab == 0, modifier = Modifier.focusRequester(firstTab), onFocus = { tab = 0 }, onClick = { tab = 0 })
            Chip("Football", selected = tab == 1, onFocus = { tab = 1 }, onClick = { tab = 1 })
        }

        val result = if (tab == 0) cricket else football
        when {
            result == null -> Message("Loading scores…")
            result.error == "NO_KEY" && tab == 0 -> Message(
                "Cricket scores need a free key from cricketdata.org.\n" +
                    "On your PC run:  .\\scripts\\set-keys.ps1 -Cricket YOUR_KEY"
            )
            result.matches.isEmpty() && result.error == "OFFLINE" -> Message("Couldn't reach the score service. Check Wi-Fi.")
            result.matches.isEmpty() && result.error != null -> Message("Score service says: ${result.error}")
            result.matches.isEmpty() -> Message(if (tab == 0) "No cricket matches in the next two weeks." else "No football matches today.")
            else -> {
                if (result.error != null) {
                    BasicText(
                        "Showing saved scores (update failed: ${result.error})",
                        style = TextStyle(color = Color(0xFFFFD66B), fontSize = 15.sp),
                    )
                }
                val rows = remember(result.matches) { buildRows(result.matches) }
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rows) { row ->
                        when (row) {
                            is ScoreRow.Header -> BasicText(
                                row.text,
                                modifier = Modifier.padding(top = 8.dp, start = 6.dp),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                ),
                            )
                            is ScoreRow.Match -> MatchRow(row.m)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    BasicText(
        text,
        modifier = Modifier.padding(top = 24.dp),
        style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp),
    )
}

/** One team's name on the left and its score on the right, like a scoreboard. */
@Composable
private fun TeamLine(name: String, score: String) {
    Row(Modifier.fillMaxWidth(0.8f), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            name,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            score,
            maxLines = 1,
            style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun MatchRow(m: MatchScore) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), highlighted = focused)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                m.competition,
                maxLines = 1,
                style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            TeamLine(m.teamA, if (m.order == 1) "" else m.scoreA)
            TeamLine(m.teamB, if (m.order == 1) "" else m.scoreB)
            // Upcoming matches only say "Match not started", which the time already tells.
            if (m.order != 1 && m.status.isNotBlank()) {
                BasicText(
                    m.status,
                    style = TextStyle(color = Color(0xFFFFD66B), fontSize = 17.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        when {
            m.live -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF4D5E)))
                BasicText(
                    "LIVE",
                    style = TextStyle(color = Color(0xFFFF8A94), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
            }
            m.order == 1 -> Column(horizontalAlignment = Alignment.End) {
                BasicText(
                    m.timeLabel(),
                    style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    m.dayLabel(),
                    style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp),
                )
            }
        }
    }
}
