package com.cleanyourtv.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

private data class Cell(val x: Int, val y: Int)

private enum class Dir(val dx: Int, val dy: Int) {
    Up(0, -1), Down(0, 1), Left(-1, 0), Right(1, 0)
}

private const val COLS = 26
private const val ROWS = 15

private fun startSnake() = listOf(Cell(6, 7), Cell(5, 7), Cell(4, 7))

private fun randomFree(snake: List<Cell>): Cell {
    val free = ArrayList<Cell>()
    for (x in 0 until COLS) {
        for (y in 0 until ROWS) {
            val c = Cell(x, y)
            if (c !in snake) free.add(c)
        }
    }
    return if (free.isEmpty()) snake.first() else free.random()
}

@Composable
fun SnakeGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var snake by remember { mutableStateOf(startSnake()) }
    var dir by remember { mutableStateOf(Dir.Right) }
    var queued by remember { mutableStateOf(Dir.Right) }
    var food by remember { mutableStateOf(Cell(18, 7)) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(Scores.get(context, "snake")) }
    var over by remember { mutableStateOf(false) }

    fun reset() {
        snake = startSnake()
        dir = Dir.Right
        queued = Dir.Right
        food = Cell(18, 7)
        score = 0
        over = false
    }

    BackHandler { onExit() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay((150L - score * 3L).coerceAtLeast(70L))
            if (over) continue
            dir = queued
            val head = snake.first()
            // The board wraps around, which is friendlier with a TV remote.
            val next = Cell((head.x + dir.dx + COLS) % COLS, (head.y + dir.dy + ROWS) % ROWS)
            val eating = next == food
            val body = if (eating) snake else snake.dropLast(1)
            if (next in body) {
                over = true
                best = Scores.submit(context, "snake", score)
                continue
            }
            snake = listOf(next) + body
            if (eating) {
                score += 1
                food = randomFree(snake)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val want = when (e.key) {
                    Key.DirectionUp -> Dir.Up
                    Key.DirectionDown -> Dir.Down
                    Key.DirectionLeft -> Dir.Left
                    Key.DirectionRight -> Dir.Right
                    else -> null
                }
                if (want != null) {
                    // Ignore a 180-degree turn straight into the body.
                    if (!(want.dx == -dir.dx && want.dy == -dir.dy)) queued = want
                    true
                } else if (over && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    reset()
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 28.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(
                    "Snake",
                    style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    "Score $score    Best ${maxOf(best, score)}",
                    style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 22.sp),
                )
            }
            Canvas(Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) {
                val cell = minOf(size.width / COLS, size.height / ROWS)
                val bw = cell * COLS
                val bh = cell * ROWS
                val left = (size.width - bw) / 2f
                val top = (size.height - bh) / 2f
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(24f, 24f),
                )
                snake.forEachIndexed { i, c ->
                    val a = (1f - i.toFloat() / (snake.size + 4)).coerceIn(0.35f, 1f)
                    drawRoundRect(
                        color = Color(0xFF7CFFB2).copy(alpha = a),
                        topLeft = Offset(left + c.x * cell + 2f, top + c.y * cell + 2f),
                        size = Size(cell - 4f, cell - 4f),
                        cornerRadius = CornerRadius(cell * 0.3f, cell * 0.3f),
                    )
                }
                drawCircle(
                    color = Color(0xFFFF6B8A),
                    radius = cell * 0.38f,
                    center = Offset(left + food.x * cell + cell / 2f, top + food.y * cell + cell / 2f),
                )
            }
        }
        if (over) {
            BasicText(
                "Game over  -  press OK to play again",
                modifier = Modifier.align(Alignment.Center),
                style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}
