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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class PongState {
    val paddleW = 18f
    val paddleH = 140f
    val leftX = 60f
    val rightX = VW - 60f
    val radius = 12f
    val winScore = 7

    var py = VH / 2f
    var ay = VH / 2f
    var bx = VW / 2f
    var by = VH / 2f
    var vx = 0f
    var vy = 0f
    var me = 0
    var cpu = 0
    var serve = 1.0f
    var serveDir = 1
    var over = false

    fun reset() {
        me = 0
        cpu = 0
        over = false
        serveDir = 1
        resetBall()
    }

    private fun resetBall() {
        bx = VW / 2f
        by = VH / 2f
        vx = 0f
        vy = 0f
        serve = 1.0f
    }

    private fun moveAi(dt: Float) {
        val target = if (vx > 0f) by else VH / 2f
        val maxMove = 340f * dt
        ay = (ay + (target - ay).coerceIn(-maxMove, maxMove)).coerceIn(paddleH / 2f, VH - paddleH / 2f)
    }

    fun update(dt: Float, dir: Int) {
        py = (py + dir * 640f * dt).coerceIn(paddleH / 2f, VH - paddleH / 2f)
        if (over) return
        moveAi(dt)

        if (serve > 0f) {
            serve -= dt
            if (serve <= 0f) {
                val angle = (Random.nextFloat() - 0.5f) * 0.8f
                vx = 520f * serveDir * cos(angle)
                vy = 520f * sin(angle)
            }
            return
        }

        bx += vx * dt
        by += vy * dt
        if (by < radius) {
            by = radius
            vy = abs(vy)
        }
        if (by > VH - radius) {
            by = VH - radius
            vy = -abs(vy)
        }

        // Player paddle (left).
        if (vx < 0f && bx - radius <= leftX + paddleW / 2f && bx >= leftX - paddleW / 2f &&
            abs(by - py) <= paddleH / 2f + radius
        ) {
            bx = leftX + paddleW / 2f + radius
            vx = minOf(1000f, abs(vx) * 1.06f)
            vy += ((by - py) / (paddleH / 2f)) * 240f
        }
        // Computer paddle (right).
        if (vx > 0f && bx + radius >= rightX - paddleW / 2f && bx <= rightX + paddleW / 2f &&
            abs(by - ay) <= paddleH / 2f + radius
        ) {
            bx = rightX - paddleW / 2f - radius
            vx = -minOf(1000f, abs(vx) * 1.06f)
            vy += ((by - ay) / (paddleH / 2f)) * 240f
        }

        if (bx < -30f) {
            cpu++
            serveDir = -1
            resetBall()
        } else if (bx > VW + 30f) {
            me++
            serveDir = 1
            resetBall()
        }
        if (me >= winScore || cpu >= winScore) over = true
    }
}

@Composable
fun PongGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val st = remember { PongState() }
    val held = remember { BooleanArray(2) }
    var tick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(Scores.get(context, "pong")) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        frameLoop { dt ->
            st.update(dt, (if (held[1]) 1 else 0) - (if (held[0]) 1 else 0))
            if (st.over && !submitted) {
                submitted = true
                best = Scores.submit(context, "pong", st.me * 10 + if (st.me > st.cpu) 30 else 0)
            }
            if (!st.over) submitted = false
            hud = "You ${st.me}  :  ${st.cpu} CPU    First to ${st.winScore}    Best $best"
            msg = if (st.over) (if (st.me > st.cpu) "You win!" else "CPU wins") else ""
            sub = if (st.over) "Press OK to play again" else ""
            tick++
        }
    }

    GameSurface(
        onExit = onExit,
        onKeyDown = { k ->
            when {
                k == Key.DirectionUp -> {
                    held[0] = true
                    true
                }
                k == Key.DirectionDown -> {
                    held[1] = true
                    true
                }
                isOk(k) -> {
                    if (st.over) st.reset()
                    true
                }
                else -> false
            }
        },
        onKeyUp = { k ->
            when (k) {
                Key.DirectionUp -> held[0] = false
                Key.DirectionDown -> held[1] = false
                else -> {}
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawRect(Color(0xFF0A0F24))
            virtual {
                drawRect(Color(0xFF0A0F24), size = Size(VW, VH))
                var y = 20f
                while (y < VH) {
                    drawRect(Color.White.copy(alpha = 0.25f), Offset(VW / 2f - 3f, y), Size(6f, 26f))
                    y += 48f
                }
                drawRoundRect(
                    Color.White,
                    Offset(st.leftX - st.paddleW / 2f, st.py - st.paddleH / 2f),
                    Size(st.paddleW, st.paddleH),
                    CornerRadius(8f),
                )
                drawRoundRect(
                    Color(0xFFFF8A65),
                    Offset(st.rightX - st.paddleW / 2f, st.ay - st.paddleH / 2f),
                    Size(st.paddleW, st.paddleH),
                    CornerRadius(8f),
                )
                drawCircle(Color(0xFFFFE066), st.radius, Offset(st.bx, st.by))
            }
        }
        GameHud("Pong", hud)
        CenterMessage(msg, sub)
        BottomHint("Up and Down move your paddle")
    }
}
