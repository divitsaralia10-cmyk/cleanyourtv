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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import kotlin.random.Random

private val PIECES: List<IntArray> = listOf(
    intArrayOf(0, 1, 1, 1, 2, 1, 3, 1),
    intArrayOf(0, 0, 0, 1, 1, 1, 2, 1),
    intArrayOf(2, 0, 0, 1, 1, 1, 2, 1),
    intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
    intArrayOf(1, 0, 2, 0, 0, 1, 1, 1),
    intArrayOf(1, 0, 0, 1, 1, 1, 2, 1),
    intArrayOf(0, 0, 1, 0, 1, 1, 2, 1),
)

private val PIECE_COLORS = listOf(
    Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFFF97316), Color(0xFFFACC15),
    Color(0xFF22C55E), Color(0xFFA855F7), Color(0xFFEF4444),
)

private class TetrisState {
    val cols = 10
    val rows = 20
    val board = Array(rows) { IntArray(cols) }
    var cells = IntArray(8)
    var kind = 0
    var px = 3
    var py = 0
    var next = Random.nextInt(7)
    var score = 0
    var lines = 0
    var over = false
    var acc = 0f

    init { reset() }

    fun reset() {
        for (r in board) r.fill(0)
        score = 0; lines = 0; over = false; acc = 0f
        next = Random.nextInt(7)
        spawn()
    }

    private fun spawn() {
        kind = next
        next = Random.nextInt(7)
        cells = PIECES[kind].copyOf()
        // 4x4 box rotation uses the I piece's size; others live in 3x3.
        px = 3; py = 0
        if (collides(cells, px, py)) over = true
    }

    private fun size() = if (kind == 0) 4 else if (kind == 3) 2 else 3

    private fun collides(c: IntArray, ox: Int, oy: Int): Boolean {
        var i = 0
        while (i < 8) {
            val x = c[i] + ox
            val y = c[i + 1] + oy
            if (x < 0 || x >= cols || y >= rows) return true
            if (y >= 0 && board[y][x] != 0) return true
            i += 2
        }
        return false
    }

    fun move(dx: Int): Boolean {
        if (over || collides(cells, px + dx, py)) return false
        px += dx
        return true
    }

    fun rotate() {
        if (over || kind == 3) return
        val n = size()
        val r = IntArray(8)
        var i = 0
        while (i < 8) {
            r[i] = n - 1 - cells[i + 1]
            r[i + 1] = cells[i]
            i += 2
        }
        for (kick in intArrayOf(0, -1, 1, -2, 2)) {
            if (!collides(r, px + kick, py)) {
                cells = r
                px += kick
                return
            }
        }
    }

    fun drop(): Boolean {
        if (over) return false
        if (!collides(cells, px, py + 1)) {
            py++
            return true
        }
        lock()
        return false
    }

    fun hardDrop() {
        if (over) return
        while (!collides(cells, px, py + 1)) { py++; score += 2 }
        lock()
    }

    private fun lock() {
        var i = 0
        while (i < 8) {
            val y = cells[i + 1] + py
            val x = cells[i] + px
            if (y >= 0) board[y][x] = kind + 1
            i += 2
        }
        var cleared = 0
        var r = rows - 1
        while (r >= 0) {
            if (board[r].all { it != 0 }) {
                for (k in r downTo 1) board[k] = board[k - 1]
                board[0] = IntArray(cols)
                cleared++
            } else r--
        }
        if (cleared > 0) {
            lines += cleared
            score += intArrayOf(0, 100, 300, 500, 800)[cleared] * (1 + lines / 10)
        }
        spawn()
    }

    fun update(dt: Float, soft: Boolean) {
        if (over) return
        val interval = if (soft) 0.04f else maxOf(0.1f, 0.8f - 0.06f * (lines / 10))
        acc += dt
        while (acc >= interval) {
            acc -= interval
            drop()
            if (over) break
        }
    }
}

@Composable
fun TetrisGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val st = remember { TetrisState() }
    var soft by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(Scores.get(context, "tetris")) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        frameLoop { dt ->
            st.update(dt, soft)
            if (st.over && !submitted) {
                submitted = true
                best = Scores.submit(context, "tetris", st.score)
            }
            if (!st.over) submitted = false
            hud = "Score ${st.score}    Lines ${st.lines}    Best $best"
            msg = if (st.over) "Game over" else ""
            tick++
        }
    }

    GameSurface(
        onExit = onExit,
        onKeyDown = { k ->
            when {
                k == Key.DirectionLeft -> { st.move(-1); true }
                k == Key.DirectionRight -> { st.move(1); true }
                k == Key.DirectionDown -> { soft = true; true }
                k == Key.DirectionUp -> { st.rotate(); true }
                isOk(k) -> { if (st.over) st.reset() else st.hardDrop(); true }
                else -> false
            }
        },
        onKeyUp = { k -> if (k == Key.DirectionDown) soft = false },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawRect(Color(0xFF0B1020))
            virtual {
                val cs = 32f
                val ox = (VW - st.cols * cs) / 2f
                val oy = (VH - st.rows * cs) / 2f + 10f
                drawRoundRect(Color(0xFF141B34), Offset(ox - 6f, oy - 6f), Size(st.cols * cs + 12f, st.rows * cs + 12f), CornerRadius(10f))
                for (r in 0 until st.rows) for (c in 0 until st.cols) {
                    val v = st.board[r][c]
                    if (v != 0) drawRoundRect(PIECE_COLORS[v - 1], Offset(ox + c * cs + 1f, oy + r * cs + 1f), Size(cs - 2f, cs - 2f), CornerRadius(5f))
                }
                if (!st.over) {
                    var i = 0
                    while (i < 8) {
                        val x = st.cells[i] + st.px
                        val y = st.cells[i + 1] + st.py
                        if (y >= 0) drawRoundRect(PIECE_COLORS[st.kind], Offset(ox + x * cs + 1f, oy + y * cs + 1f), Size(cs - 2f, cs - 2f), CornerRadius(5f))
                        i += 2
                    }
                }
                // Next piece preview.
                val nx = ox + st.cols * cs + 60f
                var i = 0
                val nc = PIECES[st.next]
                while (i < 8) {
                    drawRoundRect(PIECE_COLORS[st.next], Offset(nx + nc[i] * 26f, oy + 40f + nc[i + 1] * 26f), Size(24f, 24f), CornerRadius(4f))
                    i += 2
                }
            }
        }
        GameHud("Tetris", hud)
        CenterMessage(msg, if (st.over) "Press OK to play again" else "")
        BottomHint("Left/Right move  ·  Up rotate  ·  Down soft drop  ·  OK hard drop")
    }
}
