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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val BRICK_COLORS = listOf(
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFACC15),
    Color(0xFF22C55E), Color(0xFF38BDF8), Color(0xFFA78BFA),
)

private class BreakoutState {
    val cols = 10
    val rows = 6
    val brickW = 110f
    val brickH = 34f
    val gap = 8f
    val left0 = (VW - (cols * (brickW + gap) - gap)) / 2f
    val top0 = 110f
    val paddleW = 190f
    val paddleH = 18f
    val paddleY = 650f
    val radius = 11f

    var paddleX = VW / 2f
    var bx = VW / 2f
    var by = 600f
    var vx = 0f
    var vy = 0f
    var speed = 520f
    var launched = false
    var lives = 3
    var score = 0
    var level = 1
    var over = false
    val alive = BooleanArray(cols * rows) { true }

    fun reset() {
        lives = 3
        score = 0
        level = 1
        speed = 520f
        over = false
        alive.fill(true)
        resetBall()
    }

    fun resetBall() {
        launched = false
        bx = paddleX
        by = paddleY - paddleH / 2f - radius - 1f
        vx = 0f
        vy = 0f
    }

    fun launch() {
        if (launched || over) return
        launched = true
        val angle = Random.nextFloat() * 0.6f - 0.3f
        vx = speed * sin(angle)
        vy = -speed * cos(angle)
    }

    fun update(dt: Float, dir: Int) {
        paddleX = (paddleX + dir * 900f * dt).coerceIn(paddleW / 2f, VW - paddleW / 2f)
        if (over) return
        if (!launched) {
            bx = paddleX
            return
        }
        bx += vx * dt
        by += vy * dt

        if (bx < radius) {
            bx = radius
            vx = abs(vx)
        }
        if (bx > VW - radius) {
            bx = VW - radius
            vx = -abs(vx)
        }
        if (by < radius) {
            by = radius
            vy = abs(vy)
        }

        val paddleTop = paddleY - paddleH / 2f
        if (vy > 0f && by + radius >= paddleTop && by - radius <= paddleY + paddleH / 2f &&
            bx >= paddleX - paddleW / 2f - radius && bx <= paddleX + paddleW / 2f + radius
        ) {
            val offset = ((bx - paddleX) / (paddleW / 2f)).coerceIn(-1f, 1f)
            val angle = offset * 1.1f
            val sp = maxOf(hypot(vx, vy), speed)
            vx = sp * sin(angle)
            vy = -sp * cos(angle)
            by = paddleTop - radius - 0.5f
        }

        if (by - radius > VH) {
            lives--
            if (lives <= 0) over = true else resetBall()
            return
        }

        for (i in alive.indices) {
            if (!alive[i]) continue
            val x = left0 + (i % cols) * (brickW + gap)
            val y = top0 + (i / cols) * (brickH + gap)
            val nearX = bx.coerceIn(x, x + brickW)
            val nearY = by.coerceIn(y, y + brickH)
            val dx = bx - nearX
            val dy = by - nearY
            if (dx * dx + dy * dy <= radius * radius) {
                alive[i] = false
                score += 10 + (rows - 1 - i / cols) * 2
                if (abs(dx) > abs(dy)) vx = -vx else vy = -vy
                break
            }
        }

        if (alive.none { it }) {
            level++
            speed = minOf(900f, speed + 40f)
            alive.fill(true)
            resetBall()
        }
    }
}

@Composable
fun BreakoutGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val st = remember { BreakoutState() }
    val held = remember { BooleanArray(2) }
    var tick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(Scores.get(context, "breakout")) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        frameLoop { dt ->
            st.update(dt, (if (held[1]) 1 else 0) - (if (held[0]) 1 else 0))
            if (st.over && !submitted) {
                submitted = true
                best = Scores.submit(context, "breakout", st.score)
            }
            if (!st.over) submitted = false
            hud = "Score ${st.score}    Lives ${st.lives}    Level ${st.level}    Best $best"
            msg = if (st.over) "Game over" else ""
            sub = when {
                st.over -> "Press OK to play again"
                !st.launched -> "Press OK to launch the ball"
                else -> ""
            }
            tick++
        }
    }

    GameSurface(
        onExit = onExit,
        onKeyDown = { k ->
            when {
                k == Key.DirectionLeft -> {
                    held[0] = true
                    true
                }
                k == Key.DirectionRight -> {
                    held[1] = true
                    true
                }
                isOk(k) -> {
                    if (st.over) st.reset() else st.launch()
                    true
                }
                else -> false
            }
        },
        onKeyUp = { k ->
            when (k) {
                Key.DirectionLeft -> held[0] = false
                Key.DirectionRight -> held[1] = false
                else -> {}
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawRect(Color(0xFF0B1030))
            virtual {
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1B2A6B)), startY = 0f, endY = VH),
                    size = Size(VW, VH),
                )
                for (i in st.alive.indices) {
                    if (!st.alive[i]) continue
                    val r = i / st.cols
                    drawRoundRect(
                        BRICK_COLORS[r % BRICK_COLORS.size],
                        Offset(st.left0 + (i % st.cols) * (st.brickW + st.gap), st.top0 + r * (st.brickH + st.gap)),
                        Size(st.brickW, st.brickH),
                        CornerRadius(8f),
                    )
                }
                drawRoundRect(
                    Color.White,
                    Offset(st.paddleX - st.paddleW / 2f, st.paddleY - st.paddleH / 2f),
                    Size(st.paddleW, st.paddleH),
                    CornerRadius(9f),
                )
                drawCircle(Color(0xFFFFE066), st.radius, Offset(st.bx, st.by))
            }
        }
        GameHud("Breakout", hud)
        CenterMessage(msg, sub)
        BottomHint("Left and Right move the paddle")
    }
}
