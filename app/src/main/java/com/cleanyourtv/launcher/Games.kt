package com.cleanyourtv.launcher

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class GameId { Snake, Game2048 }

/** Best scores, stored on the TV. */
object Scores {
    private fun prefs(context: Context) = context.getSharedPreferences("cleantv", Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Int = prefs(context).getInt("best_$key", 0)

    fun submit(context: Context, key: String, score: Int): Int {
        val best = maxOf(get(context, key), score)
        prefs(context).edit().putInt("best_$key", best).apply()
        return best
    }
}

@Composable
fun ArcadePicker(onPick: (GameId) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    val snakeBest = remember { Scores.get(context, "snake") }
    val best2048 = remember { Scores.get(context, "2048") }

    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            "Arcade",
            style = TextStyle(color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            "Pick a game  ·  Back returns home",
            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp),
        )
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            GameTile("Snake", "Best $snakeBest", Modifier.focusRequester(first)) { onPick(GameId.Snake) }
            GameTile("2048", "Best $best2048", Modifier) { onPick(GameId.Game2048) }
        }
    }
}

@Composable
private fun GameTile(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "gameTileScale",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(width = 280.dp, height = 160.dp)
            .glass(RoundedCornerShape(28.dp), highlighted = focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            subtitle,
            style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp),
        )
    }
}
