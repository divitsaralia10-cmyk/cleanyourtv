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
import androidx.compose.foundation.layout.height
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

    // Refresh every minute while this screen is open (cricket is cached for 5 minutes to respect its free limit).
    LaunchedEffect(Unit) {
        while (true) {
            cricket = LiveScores.cricket(context, 5 * 60_000L)
            football = LiveScores.football(60_000L)
            delay(60_000L)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
            result.matches.isEmpty() -> Message("No matches right now.")
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(result.matches) { m -> MatchRow(m) }
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
                style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                m.summary(),
                style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold),
            )
            if (m.status.isNotBlank()) {
                BasicText(
                    m.status,
                    style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp),
                )
            }
        }
        if (m.live) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF4D5E)))
                BasicText(
                    "LIVE",
                    style = TextStyle(color = Color(0xFFFF8A94), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
