package com.cleanyourtv.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Lightweight "frosted glass" look: translucent gradient fill plus a bright edge highlight.
 * (No real backdrop blur, which keeps it smooth on TV hardware.)
 */
fun Modifier.glass(shape: Shape, highlighted: Boolean = false): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (highlighted) 0.34f else 0.20f),
                Color.White.copy(alpha = if (highlighted) 0.18f else 0.07f),
            )
        )
    )
    .border(
        width = if (highlighted) 2.dp else 1.dp,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (highlighted) 0.95f else 0.50f),
                Color.White.copy(alpha = 0.06f),
            )
        ),
        shape = shape,
    )
