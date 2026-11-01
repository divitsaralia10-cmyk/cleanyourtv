package com.cleanyourtv.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private fun spawn(board: List<Int>): List<Int> {
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return board
    val i = empty.random()
    val out = board.toMutableList()
    out[i] = if (Random.nextInt(10) == 0) 4 else 2
    return out
}

private fun newBoard(): List<Int> = spawn(spawn(List(16) { 0 }))

/** Slides one line toward index 0, merging equal neighbours once. Returns the new line and score gained. */
private fun slide(line: List<Int>): Pair<List<Int>, Int> {
    val tiles = line.filter { it != 0 }
    val out = ArrayList<Int>()
    var gain = 0
    var i = 0
    while (i < tiles.size) {
        if (i + 1 < tiles.size && tiles[i] == tiles[i + 1]) {
            val v = tiles[i] * 2
            out.add(v)
            gain += v
            i += 2
        } else {
            out.add(tiles[i])
            i += 1
        }
    }
    while (out.size < 4) out.add(0)
    return out to gain
}

/** dir: 0 = left, 1 = right, 2 = up, 3 = down. */
private fun move(board: List<Int>, dir: Int): Pair<List<Int>, Int> {
    val res = board.toMutableList()
    var gained = 0
    for (k in 0 until 4) {
        val idx = when (dir) {
            0 -> listOf(k * 4, k * 4 + 1, k * 4 + 2, k * 4 + 3)
            1 -> listOf(k * 4 + 3, k * 4 + 2, k * 4 + 1, k * 4)
            2 -> listOf(k, k + 4, k + 8, k + 12)
            else -> listOf(k + 12, k + 8, k + 4, k)
        }
        val (line, g) = slide(idx.map { board[it] })
        gained += g
        idx.forEachIndexed { j, pos -> res[pos] = line[j] }
    }
    return res to gained
}

private fun canMove(board: List<Int>): Boolean = (0..3).any { d -> move(board, d).first != board }

private fun tileColor(v: Int): Color = when (v) {
    0 -> Color.White.copy(alpha = 0.10f)
    2 -> Color(0xFF7DD3FC)
    4 -> Color(0xFF60A5FA)
    8 -> Color(0xFFA78BFA)
    16 -> Color(0xFFC084FC)
    32 -> Color(0xFFF472B6)
    64 -> Color(0xFFFB7185)
    128 -> Color(0xFFFBBF24)
    256 -> Color(0xFFF59E0B)
    512 -> Color(0xFF34D399)
    1024 -> Color(0xFF10B981)
    2048 -> Color(0xFFFDE047)
    else -> Color.White
}

@Composable
fun Game2048(onExit: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var board by remember { mutableStateOf(newBoard()) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(Scores.get(context, "2048")) }
    var over by remember { mutableStateOf(false) }

    BackHandler { onExit() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val d = when (e.key) {
                    Key.DirectionLeft -> 0
                    Key.DirectionRight -> 1
                    Key.DirectionUp -> 2
                    Key.DirectionDown -> 3
                    else -> -1
                }
                if (d >= 0) {
                    if (!over) {
                        val (moved, gained) = move(board, d)
                        if (moved != board) {
                            board = spawn(moved)
                            score += gained
                            if (score > best) best = score
                            if (!canMove(board)) {
                                over = true
                                best = Scores.submit(context, "2048", score)
                            }
                        }
                    }
                    true
                } else if (over && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    board = newBoard()
                    score = 0
                    over = false
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                "2048     Score $score     Best $best",
                style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .glass(RoundedCornerShape(22.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (r in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0 until 4) {
                            val v = board[r * 4 + c]
                            Box(
                                Modifier
                                    .size(78.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tileColor(v)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (v != 0) {
                                    BasicText(
                                        v.toString(),
                                        style = TextStyle(
                                            color = Color(0xFF0B1030),
                                            fontSize = if (v < 100) 30.sp else if (v < 1000) 26.sp else 21.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                if (over) "Game over  -  press OK to play again" else "Arrow keys to slide  ·  Back to exit",
                style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp),
            )
        }
    }
}
