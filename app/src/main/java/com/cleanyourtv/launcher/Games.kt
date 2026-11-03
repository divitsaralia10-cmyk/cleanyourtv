package com.cleanyourtv.launcher

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

enum class GameId(val title: String, val key: String, val blurb: String) {
    Cricket("Cricket 3D", "cricket", "Bat in 3D, 12 balls"),
    Snake("Snake", "snake", "Eat and grow"),
    Game2048("2048", "2048", "Slide and merge"),
    Breakout("Breakout", "breakout", "Smash the bricks"),
    Flappy("Flappy", "flappy", "Press OK to flap"),
    Pong("Pong", "pong", "Beat the computer"),
    Tetris("Tetris", "tetris", "Stack the lines"),
    Memory("Memory", "memory", "Match the pairs"),
}

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

    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        BasicText(
            "Arcade",
            style = TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            "Pick a game  ·  Back returns home",
            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp),
        )
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(GameId.entries) { index, game ->
                val best = remember(game) { Scores.get(context, game.key) }
                GameTile(
                    game = game,
                    best = best,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onPick(game) },
                )
            }
        }
    }
}

@Composable
private fun GameTile(game: GameId, best: Int, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "gameTileScale",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .height(128.dp)
            .glass(RoundedCornerShape(26.dp), highlighted = focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            BasicText(
                game.title,
                maxLines = 1,
                style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                game.blurb,
                maxLines = 1,
                style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp),
            )
        }
        BasicText(
            if (best > 0) "Best $best" else "Not played yet",
            style = TextStyle(color = Color(0xFFFFD66B), fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
    }
}
