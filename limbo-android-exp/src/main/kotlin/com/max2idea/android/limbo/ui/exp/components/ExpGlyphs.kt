package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tiny hand-drawn glyph set.
 *
 * The experimental module deliberately does not pull in
 * `androidx.compose.material:material-icons-*` nor the `miuix-icons` artifact:
 * the spec only needs a monitor, a plus, a sliders and a checkmark, and drawing
 * them here keeps the dependency graph at Miuix + Compose foundation only.
 *
 * Every glyph draws into a Canvas of the requested [size] and scales its
 * geometry fractionally, so the same function works at 16dp and at 26dp.
 */

@Composable
fun MonitorGlyph(
    color: Color,
    size: Dp = 22.dp,
    stroke: Dp = 1.8.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val line = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.14f),
            size = Size(w * 0.80f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.10f),
            style = line,
        )
        drawLine(
            color,
            Offset(w * 0.50f, h * 0.70f),
            Offset(w * 0.50f, h * 0.84f),
            stroke.toPx(),
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(w * 0.30f, h * 0.86f),
            Offset(w * 0.70f, h * 0.86f),
            stroke.toPx(),
            StrokeCap.Round,
        )
    }
}

@Composable
fun PlusGlyph(
    color: Color,
    size: Dp = 22.dp,
    stroke: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val line = stroke.toPx()
        drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), line, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), line, StrokeCap.Round)
    }
}

@Composable
fun SlidersGlyph(
    color: Color,
    size: Dp = 22.dp,
    stroke: Dp = 1.8.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val line = stroke.toPx()
        val rows = listOf(0.26f, 0.50f, 0.74f)
        val knobs = listOf(0.34f, 0.66f, 0.46f)
        rows.forEachIndexed { index, y ->
            drawLine(
                color,
                Offset(w * 0.14f, h * y),
                Offset(w * 0.86f, h * y),
                line,
                StrokeCap.Round,
            )
            drawCircle(color, radius = line * 1.5f, center = Offset(w * knobs[index], h * y))
        }
    }
}

@Composable
fun CheckGlyph(
    color: Color,
    size: Dp = 26.dp,
    stroke: Dp = 2.4.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.72f)
            lineTo(w * 0.79f, h * 0.30f)
        }
        drawPath(path, color, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun StopGlyph(
    color: Color,
    size: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.30f, h * 0.30f),
            size = Size(w * 0.40f, h * 0.40f),
            cornerRadius = CornerRadius(w * 0.06f),
        )
    }
}

@Composable
fun ChevronGlyph(
    color: Color,
    size: Dp = 16.dp,
    stroke: Dp = 1.8.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.38f, h * 0.24f)
            lineTo(w * 0.64f, h * 0.50f)
            lineTo(w * 0.38f, h * 0.76f)
        }
        drawPath(path, color, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round))
    }
}
