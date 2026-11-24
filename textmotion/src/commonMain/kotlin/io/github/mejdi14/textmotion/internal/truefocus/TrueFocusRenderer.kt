package io.github.mejdi14.textmotion.internal.truefocus

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.mejdi14.textmotion.TrueFocusFrameStyle
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout

/** Per-word layout rectangles (layout space), rebuilt only when the layout instance changes. */
internal class WordGeometry {
    private var layout: SegmentedTextLayout? = null
    var rects: List<Rect> = emptyList(); private set
    var hitRects: List<Rect> = emptyList(); private set

    fun get(l: SegmentedTextLayout, owners: IntArray, wordCount: Int): List<Rect> {
        if (layout === l && rects.size == wordCount) return rects
        val lefts = FloatArray(wordCount) { Float.POSITIVE_INFINITY }
        val rights = FloatArray(wordCount) { Float.NEGATIVE_INFINITY }
        val tops = FloatArray(wordCount) { Float.POSITIVE_INFINITY }
        val bottoms = FloatArray(wordCount) { Float.NEGATIVE_INFINITY }
        for (s in l.segments) {
            val w = owners.getOrNull(s.wordIndex) ?: continue
            if (w !in 0 until wordCount) continue
            lefts[w] = minOf(lefts[w], s.bounds.left)
            rights[w] = maxOf(rights[w], s.bounds.right)
            tops[w] = minOf(tops[w], l.layout.getLineTop(s.lineIndex))
            bottoms[w] = maxOf(bottoms[w], l.layout.getLineBottom(s.lineIndex))
        }
        rects = List(wordCount) { w ->
            if (lefts[w].isInfinite()) Rect.Zero else Rect(lefts[w], tops[w], rights[w], bottoms[w])
        }
        hitRects = rects.map { r -> Rect(r.left - r.height * 0.4f, r.top, r.right + r.height * 0.4f, r.bottom) }
        layout = l
        return rects
    }
}

/**
 * Draws the focus frame for [style] in [frame] (already animated, px). [alpha] fades the whole
 * frame (first appearance). A soft glow (the reference's `drop-shadow(0 0 4px glow)`) is built
 * from a few widening translucent strokes so it needs no RenderEffect and works on every platform.
 */
internal fun DrawScope.drawFocusFrame(
    style: TrueFocusFrameStyle,
    frame: Rect,
    cornerSize: Float,
    gap: Float,
    borderWidth: Float,
    borderColor: Color,
    highlightColor: Color,
    glowColor: Color,
    alpha: Float,
    corners: FloatArray,
) {
    if (alpha <= 0.002f) return
    val glow = glowColor.alpha > 0f
    val glowSpread = 4f * density
    when (style) {
        TrueFocusFrameStyle.Brackets -> {
            bracketCorners(frame, cornerSize, gap, corners)
            val path = cornerPath(cornerSize, borderWidth, 3f * density)
            for (c in 0 until 4) {
                val x = corners[c * 2]; val y = corners[c * 2 + 1]
                val sx = if (c % 2 == 1) -1f else 1f
                val sy = if (c >= 2) -1f else 1f
                withTransform({
                    translate(x, y)
                    scale(sx, sy, Offset(cornerSize / 2f, cornerSize / 2f))
                }) {
                    if (glow) for (i in GLOW_STEPS downTo 1) {
                        drawPath(path, glowColor.copy(alpha = glowColor.alpha * alpha * 0.22f), style = Stroke(borderWidth + glowSpread * i / GLOW_STEPS * 2f, cap = StrokeCap.Butt, join = StrokeJoin.Round))
                    }
                    drawPath(path, borderColor.copy(alpha = borderColor.alpha * alpha), style = Stroke(borderWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round))
                }
            }
        }
        TrueFocusFrameStyle.Box -> {
            val r = styleRect(style, frame, gap, borderWidth)
            val half = borderWidth / 2f
            val topLeft = Offset(r.left + half, r.top + half)
            val size = Size((r.width - borderWidth).coerceAtLeast(0f), (r.height - borderWidth).coerceAtLeast(0f))
            val radius = CornerRadius(6f * density)
            if (glow) for (i in GLOW_STEPS downTo 1) {
                drawRoundRect(glowColor.copy(alpha = glowColor.alpha * alpha * 0.22f), topLeft, size, radius, Stroke(borderWidth + glowSpread * i / GLOW_STEPS * 2f))
            }
            drawRoundRect(borderColor.copy(alpha = borderColor.alpha * alpha), topLeft, size, radius, Stroke(borderWidth))
        }
        TrueFocusFrameStyle.Underline -> {
            val r = styleRect(style, frame, gap, borderWidth)
            val radius = CornerRadius(borderWidth / 2f)
            if (glow) for (i in GLOW_STEPS downTo 1) {
                val g = glowSpread * i / GLOW_STEPS
                drawRoundRect(glowColor.copy(alpha = glowColor.alpha * alpha * 0.22f), Offset(r.left - g, r.top - g), Size(r.width + 2 * g, r.height + 2 * g), CornerRadius(radius.x + g))
            }
            drawRoundRect(borderColor.copy(alpha = borderColor.alpha * alpha), r.topLeft, r.size, radius)
        }
        TrueFocusFrameStyle.Highlight -> {
            val r = styleRect(style, frame, gap, borderWidth)
            val fill = (if (highlightColor == Color.Unspecified) borderColor else highlightColor)
            drawRoundRect(fill.copy(alpha = fill.alpha * 0.3f * alpha), r.topLeft, r.size, CornerRadius(6f * density))
        }
    }
}

/** Top-left corner bracket inside a [size] × [size] box: two inside borders with a rounded outer corner. */
private fun cornerPath(size: Float, borderWidth: Float, radius: Float): Path {
    val h = borderWidth / 2f
    val r = radius.coerceAtMost(size / 2f - h).coerceAtLeast(0f)
    return Path().apply {
        moveTo(h, size)
        lineTo(h, h + r)
        quadraticTo(h, h, h + r, h)
        lineTo(size, h)
    }
}

private const val GLOW_STEPS = 3
