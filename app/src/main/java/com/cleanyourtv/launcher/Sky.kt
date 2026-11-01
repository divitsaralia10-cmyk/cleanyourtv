package com.cleanyourtv.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class SkyKey(val hour: Float, val top: Color, val bottom: Color)

private val SKY_KEYS = listOf(
    SkyKey(0f, Color(0xFF050818), Color(0xFF16215A)),
    SkyKey(5f, Color(0xFF0B1030), Color(0xFF2B3A80)),
    SkyKey(6.5f, Color(0xFF3A4C96), Color(0xFFF0A07A)),
    SkyKey(9f, Color(0xFF2F7FD8), Color(0xFFA9D6F5)),
    SkyKey(14f, Color(0xFF2B78D0), Color(0xFFB8E0F8)),
    SkyKey(17f, Color(0xFF3B6FB8), Color(0xFFF2B27A)),
    SkyKey(18.7f, Color(0xFF332C6B), Color(0xFFE8786E)),
    SkyKey(20f, Color(0xFF111640), Color(0xFF2A2F78)),
    SkyKey(24f, Color(0xFF050818), Color(0xFF16215A)),
)

private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float)

private val STARS: List<Star> = Random(7).let { rnd ->
    List(90) {
        Star(rnd.nextFloat(), rnd.nextFloat() * 0.62f, 1f + rnd.nextFloat() * 2.2f, rnd.nextFloat())
    }
}

/** 1 at night, 0 in daylight, ramping through dawn and dusk. */
fun nightFactor(hour: Float): Float = when {
    hour < 5f || hour >= 20f -> 1f
    hour < 6.5f -> 1f - (hour - 5f) / 1.5f
    hour < 18.7f -> 0f
    else -> ((hour - 18.7f) / 1.3f).coerceIn(0f, 1f)
}

private fun skyColors(hour: Float): Pair<Color, Color> {
    val h = hour.coerceIn(0f, 24f)
    val idx = SKY_KEYS.indexOfLast { it.hour <= h }.coerceIn(0, SKY_KEYS.size - 2)
    val a = SKY_KEYS[idx]
    val b = SKY_KEYS[idx + 1]
    val f = ((h - a.hour) / (b.hour - a.hour)).coerceIn(0f, 1f)
    return lerp(a.top, b.top, f) to lerp(a.bottom, b.bottom, f)
}

/**
 * Procedural sky that follows the time of day: gradient, stars at night,
 * a sun or moon on an arc, and three layers of drifting mountains.
 * [drift] is read only while drawing, so animating it does not recompose anything.
 */
@Composable
fun SkyScene(hour: Float, drift: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val d = drift()
        val twoPi = (2.0 * PI).toFloat()
        val (top, bottom) = skyColors(hour)

        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        val night = nightFactor(hour)
        if (night > 0f) {
            STARS.forEach { s ->
                val twinkle = 0.55f + 0.45f * sin(d * 40f + s.phase * twoPi)
                drawCircle(
                    color = Color.White.copy(alpha = (night * twinkle).coerceIn(0f, 1f)),
                    radius = s.r,
                    center = Offset(s.x * w, s.y * h),
                )
            }
        }

        // Sun by day, moon by night, moving along an arc.
        val isDay = hour >= 6f && hour <= 18.5f
        val p = if (isDay) (hour - 6f) / 12.5f else ((hour - 18.5f + 24f) % 24f) / 11.5f
        val center = Offset(w * (0.12f + 0.76f * p), h * (0.60f - 0.42f * sin(PI.toFloat() * p)))
        val radius = h * 0.055f
        val bodyColor = if (isDay) Color(0xFFFFE9A8) else Color(0xFFEFF3FF)
        val glowColor = if (isDay) Color(0xFFFFC96B) else Color(0xFF9DB4FF)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = 0.45f), Color.Transparent),
                center = center,
                radius = radius * 5f,
            ),
            radius = radius * 5f,
            center = center,
        )
        drawCircle(color = bodyColor, radius = radius, center = center)

        fun mountains(baseY: Float, amp: Float, freq: Float, phase: Float, color: Color) {
            val path = Path()
            path.moveTo(0f, h)
            val step = w / 90f
            var x = 0f
            while (x <= w + step) {
                val nx = x / w
                val y = baseY + amp * (
                    0.55f * sin(nx * freq * twoPi + phase) +
                        0.30f * sin(nx * freq * 5.1f + phase * 1.7f) +
                        0.15f * sin(nx * freq * 11f + phase * 0.6f)
                    )
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, color)
        }

        mountains(h * 0.68f, h * 0.07f, 0.9f, 0.4f + d * 0.5f, lerp(bottom, Color(0xFF1A2550), 0.50f))
        mountains(h * 0.76f, h * 0.06f, 1.3f, 2.1f + d * 0.4f, lerp(bottom, Color(0xFF101A44), 0.68f))
        mountains(h * 0.86f, h * 0.045f, 1.8f, 4.0f + d * 0.3f, lerp(bottom, Color(0xFF080F30), 0.85f))
    }
}
