package com.cleanyourtv.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Games draw in a fixed 1280 x 720 world that is scaled to fit the screen. */
const val VW = 1280f
const val VH = 720f

fun isOk(k: Key): Boolean = k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter

/** Runs [step] once per display frame with the elapsed seconds (capped so a stutter cannot teleport things). */
suspend fun frameLoop(step: (Float) -> Unit) {
    var last = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
        last = now
        step(dt)
    }
}

/** Scales and centres drawing so that coordinates 0..VW and 0..VH always fit the screen. */
fun DrawScope.virtual(block: DrawScope.() -> Unit) {
    val s = minOf(size.width / VW, size.height / VH)
    translate((size.width - VW * s) / 2f, (size.height - VH * s) / 2f) {
        scale(s, s, Offset.Zero) { block() }
    }
}

/** Full-screen focusable area that receives remote keys. Back calls [onExit]. */
@Composable
fun GameSurface(
    onExit: () -> Unit,
    onKeyDown: (Key) -> Boolean = { false },
    onKeyUp: (Key) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
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
                when (e.type) {
                    KeyEventType.KeyDown -> onKeyDown(e.key)
                    KeyEventType.KeyUp -> {
                        onKeyUp(e.key)
                        false
                    }
                    else -> false
                }
            }
            .focusable(),
        content = content,
    )
}

@Composable
fun BoxScope.GameHud(title: String, info: String) {
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            info,
            style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 22.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
fun BoxScope.CenterMessage(text: String, sub: String = "") {
    if (text.isBlank() && sub.isBlank()) return
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        if (text.isNotBlank()) {
            BasicText(
                text,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        if (sub.isNotBlank()) {
            BasicText(
                sub,
                style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 24.sp, textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
fun BoxScope.BottomHint(text: String) {
    BasicText(
        text,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp),
    )
}
