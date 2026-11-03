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
import kotlin.math.sin
import kotlin.random.Random

private class FlappyState {
    val birdX = 300f
    val birdR = 22f
    val groundY = 650f
    val pipeW = 110f
    val gapH = 230f
    val spacing = 430f
    val pipeCount = 4

    var birdY = 360f
    var vy = 0f
    var started = false
    var over = false
    var score = 0
    var time = 0f
    var scroll = 0f
    val pipeX = FloatArray(pipeCount)
    val gapY = FloatArray(pipeCount)
    val passed = BooleanArray(pipeCount)

    init {
        reset()
    }

    fun reset() {
        birdY = 360f
        vy = 0f
        started = false
        over = false
        score = 0
        for (i in 0 until pipeCount) {
            pipeX[i] = 900f + i * spacing
            gapY[i] = randomGap()
            passed[i] = false
        }
    }

    private fun randomGap(): Float = 220f + Random.nextFloat() * 260f

    fun flap() {
        if (over) {
            reset()
            return
        }
        started = true
        vy = -520f
    }

    fun update(dt: Float) {
        time += dt
        if (over) return
        scroll = (scroll + 260f * dt) % 80f
        if (!started) {
            birdY = 360f + sin(time * 4f) * 12f
            return
        }
        vy += 1500f * dt
        birdY += vy * dt
        for (i in 0 until pipeCount) {
            pipeX[i] -= 260f * dt
            if (pipeX[i] < -pipeW) {
                pipeX[i] += pipeCount * spacing
                gapY[i] = randomGap()
                passed[i] = false
            }
            if (!passed[i] && pipeX[i] + pipeW < birdX - birdR) {
                passed[i] = true
                score++
            }
            val withinX = birdX + birdR > pipeX[i] && birdX - birdR < pipeX[i] + pipeW
            if (withinX && (birdY - birdR < gapY[i] - gapH / 2f || birdY + birdR > gapY[i] + gapH / 2f)) over = true
        }
        if (birdY - birdR < 0f || birdY + birdR > groundY) over = true
    }
}

@Composable
fun FlappyGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val st = remember { FlappyState() }
    var tick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(Scores.get(context, "flappy")) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        frameLoop { dt ->
            st.update(dt)
            if (st.over && !submitted) {
                submitted = true
                best = Scores.submit(context, "flappy", st.score)
            }
            if (!st.over) submitted = false
            hud = "Score ${st.score}    Best $best"
            msg = if (st.over) "Game over" else if (!st.started) "Get ready" else ""
            sub = if (st.over) "Press OK to try again" else if (!st.started) "Press OK to flap" else ""
            tick++
        }
    }

    GameSurface(
        onExit = onExit,
        onKeyDown = { k ->
            if (isOk(k) || k == Key.DirectionUp) {
                st.flap()
                true
            } else {
                false
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawRect(Color(0xFF4FC3F7))
            virtual {
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFF38A3E8), Color(0xFFBEE9FB)), startY = 0f, endY = st.groundY),
                    size = Size(VW, st.groundY),
                )
                // Pipes with caps.
                for (i in 0 until st.pipeCount) {
                    val x = st.pipeX[i]
                    val top = st.gapY[i] - st.gapH / 2f
                    val bottom = st.gapY[i] + st.gapH / 2f
                    val body = Color(0xFF43A047)
                    val cap = Color(0xFF2E7D32)
                    drawRect(body, Offset(x, 0f), Size(st.pipeW, top))
                    drawRoundRect(cap, Offset(x - 8f, top - 34f), Size(st.pipeW + 16f, 34f), CornerRadius(8f))
                    drawRect(body, Offset(x, bottom), Size(st.pipeW, st.groundY - bottom))
                    drawRoundRect(cap, Offset(x - 8f, bottom), Size(st.pipeW + 16f, 34f), CornerRadius(8f))
                }
                // Ground.
                drawRect(Color(0xFFDEB887), Offset(0f, st.groundY), Size(VW, VH - st.groundY))
                drawRect(Color(0xFF7CB342), Offset(0f, st.groundY), Size(VW, 16f))
                var gx = -st.scroll
                while (gx < VW) {
                    drawRect(Color(0xFFC9A26B), Offset(gx, st.groundY + 30f), Size(40f, 10f))
                    gx += 80f
                }
                // Bird.
                drawCircle(Color(0xFFFFD54F), st.birdR, Offset(st.birdX, st.birdY))
                drawCircle(Color.White, 8f, Offset(st.birdX + 9f, st.birdY - 6f))
                drawCircle(Color.Black, 4f, Offset(st.birdX + 11f, st.birdY - 6f))
                drawRoundRect(Color(0xFFFF7043), Offset(st.birdX + 14f, st.birdY + 2f), Size(18f, 10f), CornerRadius(5f))
                val wing = if (abs(st.vy) > 200f) -8f else 4f
                drawRoundRect(Color(0xFFFFB300), Offset(st.birdX - 16f, st.birdY + wing), Size(20f, 12f), CornerRadius(6f))
            }
        }
        GameHud("Flappy", hud)
        CenterMessage(msg, sub)
    }
}
