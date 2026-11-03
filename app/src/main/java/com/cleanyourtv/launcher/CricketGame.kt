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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Cricket 3D: a behind-the-batter view drawn with real perspective projection.
 * Everything is positioned in metres (x sideways, z away from the camera) and projected to the screen,
 * so the ball genuinely grows as it comes at you. Press OK to swing; the timing decides the shot.
 */

private const val FOCAL = 760f
private const val CENTER_X = 640f
private const val HORIZON = 230f
private const val CAM_H = 2.6f
private const val STUMPS_Z = 6.0f
private const val CONTACT_Z = 7.2f
private const val RELEASE_Z = 25.5f
private const val END_Z = 5.0f
private const val P_CONTACT = (RELEASE_Z - CONTACT_Z) / (RELEASE_Z - END_Z)
private const val P_STUMPS = (RELEASE_Z - STUMPS_Z) / (RELEASE_Z - END_Z)

private fun px(x: Float, z: Float): Float = CENTER_X + x * FOCAL / z
private fun py(h: Float, z: Float): Float = HORIZON + (CAM_H - h) * FOCAL / z

private enum class Phase { Ready, Waiting, Bowling, Hit, Result, Over }

private fun runsLabel(runs: Int): String = when (runs) {
    6 -> "SIX!"
    4 -> "FOUR!"
    1 -> "1 run"
    0 -> "Dot ball"
    else -> "$runs runs"
}

private class CricketState {
    var phase = Phase.Ready
    var t = 0f
    var flight = 1.6f
    var bounceAt = 0.7f
    var x0 = 0f
    var xEnd = 0f

    var runs = 0
    var wickets = 0
    var balls = 0
    val maxBalls = 12
    val maxWickets = 2

    var leftHeld = false
    var rightHeld = false
    var upHeld = false

    var swung = false
    var swingT = 0f
    var willHit = false
    var hitStarted = false
    var stumpsChecked = false
    var contactAt = 0f
    var dirX = 0
    var shotRuns = 0
    var shotOut = false
    var shotLabel = ""
    var missLabel = "Dot ball"
    var missOut = false

    var hitT = 0f
    var hitDur = 1f
    var hitFromX = 0f
    var hitFromY = 0f
    var hitToX = 0f
    var hitToY = 0f
    var hitArc = 0f
    var hitR0 = 20f

    var label = ""
    var timer = 0f

    // Scratch values for the ball's screen position: x, y, radius, ground y.
    val ball = FloatArray(4)

    fun reset() {
        runs = 0
        wickets = 0
        balls = 0
        label = ""
        phase = Phase.Waiting
        timer = 0.8f
    }

    fun lineAt(p: Float): Float = x0 + (xEnd - x0) * p

    fun heightAt(p: Float): Float =
        if (p < bounceAt) 2.2f * (1f - p / bounceAt) else minOf(1.1f, (p - bounceAt) * 3.2f)

    /** Fills [ball] with the screen position of the delivery at progress [p] (0 = release, 1 = past the stumps). */
    fun project(p: Float) {
        val q = p.coerceIn(0f, 1f)
        val z = RELEASE_Z - (RELEASE_Z - END_Z) * q
        val k = FOCAL / z
        ball[0] = px(lineAt(q), z)
        ball[1] = py(heightAt(q), z)
        ball[2] = 0.22f * k
        ball[3] = py(0f, z)
    }

    fun press() {
        when (phase) {
            Phase.Ready -> {
                phase = Phase.Waiting
                timer = 0.8f
            }
            Phase.Over -> reset()
            Phase.Bowling -> if (!swung) swing()
            else -> {}
        }
    }

    private fun swing() {
        swung = true
        swingT = 0f
        dirX = (if (rightHeld) 1 else 0) - (if (leftHeld) 1 else 0)
        val loft = upHeld
        // The bat meets the ball 0.12 seconds after the button press.
        val error = (t + 0.12f) - flight * P_CONTACT
        val q = abs(error)
        if (q < 0.23f) {
            willHit = true
            contactAt = t + 0.12f
            when {
                q < 0.07f -> shotRuns = if (loft) 6 else 4
                q < 0.14f -> shotRuns = if (loft) 4 else 2
                else -> {
                    if (loft && Random.nextFloat() < 0.6f) {
                        shotOut = true
                        shotLabel = "CAUGHT!"
                    } else {
                        shotRuns = 1
                    }
                }
            }
            // Balls well outside off or leg are harder to hit cleanly.
            if (!shotOut && abs(lineAt(P_CONTACT)) > 0.55f) {
                shotRuns = when (shotRuns) {
                    6 -> 4
                    4 -> 2
                    2 -> 1
                    else -> shotRuns
                }
            }
            if (!shotOut) shotLabel = if (q >= 0.14f && shotRuns == 1) "Edged - 1 run" else runsLabel(shotRuns)
        } else {
            missLabel = "Missed!"
        }
    }

    fun startBall() {
        phase = Phase.Bowling
        t = 0f
        swung = false
        swingT = 0f
        willHit = false
        hitStarted = false
        stumpsChecked = false
        shotRuns = 0
        shotOut = false
        shotLabel = ""
        missLabel = "Dot ball"
        missOut = false
        flight = (1.95f - 0.045f * balls + Random.nextFloat() * 0.25f).coerceAtLeast(1.3f)
        bounceAt = 0.6f + Random.nextFloat() * 0.2f
        x0 = (Random.nextFloat() - 0.5f) * 0.3f
        xEnd = if (Random.nextFloat() < 0.45f) (Random.nextFloat() - 0.5f) * 0.5f else (Random.nextFloat() - 0.5f) * 1.5f
        label = ""
    }

    private fun checkStumps() {
        if (abs(lineAt(P_STUMPS)) < 0.2f && heightAt(P_STUMPS) < 0.75f) {
            missOut = true
            missLabel = "BOWLED!"
        }
    }

    private fun beginHit() {
        hitStarted = true
        phase = Phase.Hit
        hitT = 0f
        val p = (contactAt / flight).coerceIn(0f, 1f)
        project(p)
        hitFromX = ball[0]
        hitFromY = ball[1]
        hitR0 = ball[2]
        val side = if (dirX == 0) (Random.nextFloat() - 0.5f) * 0.3f else dirX.toFloat()
        when {
            shotOut -> {
                hitToX = CENTER_X + side * 220f
                hitToY = HORIZON + 30f
                hitArc = -260f
                hitDur = 1.2f
            }
            shotRuns >= 6 -> {
                hitToX = CENTER_X + side * 330f
                hitToY = 50f
                hitArc = -110f
                hitDur = 1.4f
            }
            shotRuns >= 4 -> {
                hitToX = CENTER_X + side * 620f
                hitToY = HORIZON + 90f
                hitArc = -30f
                hitDur = 0.9f
            }
            else -> {
                hitToX = CENTER_X + side * 260f
                hitToY = HORIZON + 150f
                hitArc = -60f
                hitDur = 0.8f
            }
        }
    }

    private fun finish(gained: Int, out: Boolean, text: String) {
        balls++
        if (out) wickets++ else runs += gained
        label = text
        timer = 1.1f
        phase = Phase.Result
    }

    fun update(dt: Float) {
        if (swung) swingT += dt
        when (phase) {
            Phase.Waiting -> {
                timer -= dt
                if (timer <= 0f) startBall()
            }
            Phase.Bowling -> {
                t += dt
                if (willHit && !hitStarted && t >= contactAt) {
                    beginHit()
                } else if (!willHit && !stumpsChecked && t >= flight * P_STUMPS) {
                    stumpsChecked = true
                    checkStumps()
                }
                if (phase == Phase.Bowling && t >= flight + 0.3f) {
                    finish(0, missOut, if (swung && !missOut) "Missed!" else missLabel)
                }
            }
            Phase.Hit -> {
                hitT += dt
                if (hitT >= hitDur) finish(shotRuns, shotOut, shotLabel)
            }
            Phase.Result -> {
                timer -= dt
                if (timer <= 0f) {
                    label = ""
                    if (balls >= maxBalls || wickets >= maxWickets) {
                        phase = Phase.Over
                    } else {
                        phase = Phase.Waiting
                        timer = 0.7f
                    }
                }
            }
            else -> {}
        }
    }
}

// Static scenery, generated once.
private val BAND_Z: List<Float> = run {
    val list = ArrayList<Float>()
    var z = 2.5f
    while (z < 400f) {
        list.add(z)
        z *= 1.22f
    }
    list
}

private class CrowdDot(val x: Float, val y: Float, val color: Color)

private val CROWD: List<CrowdDot> = run {
    val rnd = Random(11)
    val palette = listOf(Color(0xFFE53935), Color(0xFFFFEB3B), Color(0xFF42A5F5), Color(0xFFFFFFFF), Color(0xFF66BB6A))
    List(260) { CrowdDot(rnd.nextFloat() * VW, HORIZON - 42f + rnd.nextFloat() * 40f, palette[rnd.nextInt(palette.size)]) }
}

private fun DrawScope.drawCricket(st: CricketState) {
    // Fill the whole canvas first, so no edge shows when the screen is not exactly 16:9.
    drawRect(Color(0xFF2E7D32))
    virtual {
        // Sky and crowd.
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF173A73), Color(0xFF7FB2E6)), startY = 0f, endY = HORIZON),
            topLeft = Offset(0f, 0f),
            size = Size(VW, HORIZON),
        )
        drawRect(Color(0xFF263042), topLeft = Offset(0f, HORIZON - 46f), size = Size(VW, 46f))
        for (d in CROWD) drawCircle(d.color.copy(alpha = 0.85f), 3f, Offset(d.x, d.y))

        // Mown stripes receding into the distance.
        for (i in 0 until BAND_Z.size - 1) {
            val near = py(0f, BAND_Z[i])
            val far = py(0f, BAND_Z[i + 1])
            val color = if (i % 2 == 0) Color(0xFF2F8A36) else Color(0xFF3A9B42)
            drawRect(color, topLeft = Offset(0f, far), size = Size(VW, near - far + 1f))
        }

        // The pitch.
        val pitch = Path().apply {
            moveTo(px(-1.52f, 2.5f), py(0f, 2.5f))
            lineTo(px(-1.52f, 27f), py(0f, 27f))
            lineTo(px(1.52f, 27f), py(0f, 27f))
            lineTo(px(1.52f, 2.5f), py(0f, 2.5f))
            close()
        }
        drawPath(pitch, Color(0xFFC9A96B))
        // Creases.
        val creaseColor = Color.White.copy(alpha = 0.9f)
        drawLine(creaseColor, Offset(px(-1.32f, 7.22f), py(0f, 7.22f)), Offset(px(1.32f, 7.22f), py(0f, 7.22f)), 4f)
        drawLine(creaseColor, Offset(px(-1.32f, 24.9f), py(0f, 24.9f)), Offset(px(1.32f, 24.9f), py(0f, 24.9f)), 2f)

        // Bowler's end stumps and the bowler.
        for (s in -1..1) {
            val sx = px(s * 0.114f, 26.1f)
            drawRect(Color(0xFFF5F0E1), Offset(sx - 1.2f, py(0.71f, 26.1f)), Size(2.4f, py(0f, 26.1f) - py(0.71f, 26.1f)))
        }
        val bowlerX = px(0.3f, 26.6f)
        val bowlerBase = py(0f, 26.6f)
        drawRect(Color.White, Offset(bowlerX - 5f, bowlerBase - 27f), Size(10f, 27f))
        drawCircle(Color(0xFFE0A47A), 5f, Offset(bowlerX, bowlerBase - 32f))

        // Contact zone marker on the pitch.
        drawCircle(
            Color.White.copy(alpha = 0.22f),
            radius = 0.45f * FOCAL / CONTACT_Z,
            center = Offset(px(0f, CONTACT_Z), py(0f, CONTACT_Z)),
            style = Stroke(width = 3f),
        )

        // The batter, standing in front of the stumps.
        val k = FOCAL / CONTACT_Z
        val bx = px(-0.5f, CONTACT_Z)
        val ground = py(0f, CONTACT_Z)
        drawRoundRect(Color.White, Offset(bx - 25f, ground - 58f), Size(20f, 58f), CornerRadius(6f))
        drawRoundRect(Color.White, Offset(bx + 5f, ground - 58f), Size(20f, 58f), CornerRadius(6f))
        drawRoundRect(Color(0xFF1E40AF), Offset(bx - 27f, ground - 142f), Size(54f, 88f), CornerRadius(12f))
        drawCircle(Color(0xFF1E3A8A), 21f, Offset(bx, ground - 160f))
        drawCircle(Color(0xFFE0A47A), 12f, Offset(bx + 6f, ground - 156f))

        // Bat: held ready, then swung through when OK is pressed.
        val hand = Offset(bx + 14f, ground - 100f)
        val angle = if (st.swung) {
            val s = (st.swingT / 0.30f).coerceIn(0f, 1f)
            val e = s * s * (3f - 2f * s)
            2.2f + (-0.9f - 2.2f) * e
        } else {
            2.2f
        }
        val batEnd = Offset(hand.x + cos(angle) * (k * 1.15f), hand.y + sin(angle) * (k * 1.15f))
        drawLine(Color(0xFFD9A066), hand, batEnd, 17f, StrokeCap.Round)
        drawLine(Color(0xFF6B4423), hand, Offset(hand.x + cos(angle) * 28f, hand.y + sin(angle) * 28f), 12f, StrokeCap.Round)

        // The stumps we are protecting (nearer to the camera than the batter).
        val stumpK = FOCAL / STUMPS_Z
        val stumpBase = py(0f, STUMPS_Z)
        for (s in -1..1) {
            drawRect(
                Color(0xFFF5F0E1),
                Offset(px(s * 0.114f, STUMPS_Z) - 3f, stumpBase - 0.71f * stumpK),
                Size(6f, 0.71f * stumpK),
            )
        }

        // The ball.
        when (st.phase) {
            Phase.Bowling -> {
                st.project(st.t / st.flight)
                drawBall(st.ball[0], st.ball[1], st.ball[2], st.ball[3])
            }
            Phase.Hit -> {
                val u = (st.hitT / st.hitDur).coerceIn(0f, 1f)
                val x = st.hitFromX + (st.hitToX - st.hitFromX) * u
                val y = st.hitFromY + (st.hitToY - st.hitFromY) * u + st.hitArc * 4f * u * (1f - u)
                val r = st.hitR0 + (5f - st.hitR0) * u
                drawBall(x, y, r, y)
            }
            else -> {}
        }
    }
}

private fun DrawScope.drawBall(x: Float, y: Float, r: Float, groundY: Float) {
    // Shadow on the ground makes the height easy to read.
    drawOval(Color.Black.copy(alpha = 0.28f), Offset(x - r * 1.5f, groundY - r * 0.35f), Size(r * 3f, r * 0.7f))
    drawCircle(Color(0xFFD7263D), r, Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.6f), r * 0.28f, Offset(x - r * 0.3f, y - r * 0.3f))
}

@Composable
fun CricketGame(onExit: () -> Unit) {
    val context = LocalContext.current
    val st = remember { CricketState() }
    var tick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(Scores.get(context, "cricket")) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        frameLoop { dt ->
            st.update(dt)
            if (st.phase == Phase.Over && !submitted) {
                submitted = true
                best = Scores.submit(context, "cricket", st.runs)
            }
            if (st.phase != Phase.Over) submitted = false
            hud = "Runs ${st.runs}/${st.wickets}    Balls ${st.balls}/${st.maxBalls}    Best $best"
            when (st.phase) {
                Phase.Ready -> {
                    msg = "Cricket 3D"
                    sub = "Press OK to start"
                }
                Phase.Over -> {
                    msg = "Innings over: ${st.runs}/${st.wickets}"
                    sub = "Press OK to play again"
                }
                else -> {
                    msg = st.label
                    sub = ""
                }
            }
            tick++
        }
    }

    GameSurface(
        onExit = onExit,
        onKeyDown = { k ->
            when {
                k == Key.DirectionLeft -> {
                    st.leftHeld = true
                    true
                }
                k == Key.DirectionRight -> {
                    st.rightHeld = true
                    true
                }
                k == Key.DirectionUp -> {
                    st.upHeld = true
                    true
                }
                isOk(k) -> {
                    st.press()
                    true
                }
                else -> false
            }
        },
        onKeyUp = { k ->
            when (k) {
                Key.DirectionLeft -> st.leftHeld = false
                Key.DirectionRight -> st.rightHeld = false
                Key.DirectionUp -> st.upHeld = false
                else -> {}
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawCricket(st)
        }
        GameHud("Cricket 3D", hud)
        CenterMessage(msg, sub)
        BottomHint("Hold Left or Right to aim, Up to loft, then press OK to swing as the ball reaches you")
    }
}
