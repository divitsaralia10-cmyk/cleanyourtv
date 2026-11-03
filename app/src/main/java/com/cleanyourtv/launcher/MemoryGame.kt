package com.cleanyourtv.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext

private val FACE_COLORS = listOf(
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFACC15), Color(0xFF22C55E),
    Color(0xFF14B8A6), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899),
)

private class MemoryState {
    val cols = 4
    val rows = 4
    var faces = IntArray(16)
    val open = BooleanArray(16)
    val matched = BooleanArray(16)
    var cursor = 0
    var first = -1
    var second = -1
    var hideIn = 0f
    var moves = 0
    var time = 0f
    var done = false

    init { reset() }

    fun reset() {
        val list = (0 until 8).flatMap { listOf(it, it) }.shuffled()
        faces = list.toIntArray()
        open.fill(false); matched.fill(false)
        cursor = 0; first = -1; second = -1; hideIn = 0f; moves = 0; time = 0f; done = false
    }

    fun move(dx: Int, dy: Int) {
        val x = (cursor % cols + dx).coerceIn(0, cols - 1)
        val y = (cursor / cols + dy).coerceIn(0, rows - 1)
        cursor = y * cols + x
    }

    fun pick() {
        if (done) { reset(); return }
        if (second >= 0 || matched[cursor] || open[cursor]) return
        open[cursor] = true
        if (first < 0) { first = cursor; return }
        second = cursor
        moves++
        if (faces[first] == faces[second]) {
            matched[first] = true; matched[second] = true
            first = -1; second = -1
            if (matched.all { it }) done = true
        } else hideIn = 0.8f
    }

    fun update(dt: Float) {
        if (!done) time += dt
        if (second >= 0) {
            hideIn -= dt
            if (hideIn <= 0f) {
                open[first] = false; open[second] = false
                first = -1; second = -1
            }
        }
    }
}

@Composable
fun MemoryGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val st = remember { MemoryState() }
    var tick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    // Best = highest score; score is higher for fewer moves.
    var best by remember { mutableIntStateOf(Scores.get(context, "memory")) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        frameLoop { dt ->
            st.update(dt)
            val score = maxOf(0, 200 - st.moves * 8)
            if (st.done && !submitted) {
                submitted = true
                best = Scores.submit(context, "memory", score)
            }
            if (!st.done) submitted = false
            hud = "Moves ${st.moves}    Time ${st.time.toInt()}s    Best $best"
            msg = if (st.done) "Cleared in ${st.moves} moves!" else ""
            tick++
        }
    }

    GameSurface(
        onExit = onExit,
        onKeyDown = { k ->
            when {
                k == Key.DirectionLeft -> { st.move(-1, 0); true }
                k == Key.DirectionRight -> { st.move(1, 0); true }
                k == Key.DirectionUp -> { st.move(0, -1); true }
                k == Key.DirectionDown -> { st.move(0, 1); true }
                isOk(k) -> { st.pick(); true }
                else -> false
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawRect(Color(0xFF111A3A))
            virtual {
                val cw = 120f
                val gap = 16f
                val ox = (VW - (st.cols * (cw + gap) - gap)) / 2f
                val oy = 110f
                for (i in 0 until 16) {
                    val x = ox + (i % st.cols) * (cw + gap)
                    val y = oy + (i / st.cols) * (cw + gap)
                    val shown = st.open[i] || st.matched[i]
                    val c = if (shown) FACE_COLORS[st.faces[i]] else Color(0xFF2B3A78)
                    drawRoundRect(c.copy(alpha = if (st.matched[i]) 0.45f else 1f), Offset(x, y), Size(cw, cw), CornerRadius(16f))
                    if (shown) drawCircle(Color.White.copy(alpha = 0.85f), 18f + (st.faces[i] % 4) * 6f, Offset(x + cw / 2f, y + cw / 2f))
                    if (i == st.cursor) drawRoundRect(Color.White, Offset(x - 4f, y - 4f), Size(cw + 8f, cw + 8f), CornerRadius(18f), style = Stroke(5f))
                }
            }
        }
        GameHud("Memory", hud)
        CenterMessage(msg, if (st.done) "Press OK to play again" else "")
        BottomHint("Arrows move  ·  OK flips a card")
    }
}
