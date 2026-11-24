package io.github.mejdi14.textmotion.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import io.github.mejdi14.textmotion.SegmentRenderMode
import io.github.mejdi14.textmotion.TextGranularity
import kotlin.math.max
import kotlin.math.min

/**
 * One drawable (non-whitespace) piece of a paragraph.
 *
 * All geometry is in the coordinate space of [SegmentedTextLayout.layout].
 */
internal class TextSegment(
    /** Position among drawable segments (use for staggering). */
    val index: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val lineIndex: Int,
    /** Index of the word this segment belongs to (equal to [index] for word granularity). */
    val wordIndex: Int,
    /** Ink-independent box: union of the glyph advance boxes. */
    val bounds: Rect,
    /** Region used when drawing in [SegmentRenderMode.Clip] mode. */
    val clipRect: Rect,
    val baseline: Float,
    /** Standalone layout used in [SegmentRenderMode.Isolated] mode. */
    val isolated: TextLayoutResult?,
    /** Where [isolated] must be drawn so that it lands on [bounds]. */
    val isolatedOffset: Offset,
) {
    val center: Offset get() = bounds.center
}

/**
 * A paragraph measured once plus its segmentation. Built outside of the draw loop and reused
 * for every frame until text, style or constraints change.
 */
internal class SegmentedTextLayout(
    val layout: TextLayoutResult,
    val granularity: TextGranularity?,
    val renderMode: SegmentRenderMode,
    val segments: List<TextSegment>,
) {
    /** Mode requested by the caller (may be [SegmentRenderMode.Auto]); [renderMode] is the resolved one. */
    var requestedMode: SegmentRenderMode = renderMode
    val text: AnnotatedString get() = layout.layoutInput.text
    val wordCount: Int = if (segments.isEmpty()) 0 else segments.last().wordIndex + 1
    val lineCount: Int get() = layout.lineCount

    companion object {
        fun build(
            layout: TextLayoutResult,
            granularity: TextGranularity?,
            requestedMode: SegmentRenderMode,
            measurer: TextMeasurer,
        ): SegmentedTextLayout {
            val text = layout.layoutInput.text
            val mode = when {
                granularity == null || granularity == TextGranularity.Line -> SegmentRenderMode.Clip
                requestedMode == SegmentRenderMode.Auto ->
                    if (needsShapingContext(text.text)) SegmentRenderMode.Clip else SegmentRenderMode.Isolated
                else -> requestedMode
            }
            val ranges = when (granularity) {
                null -> emptyList()
                TextGranularity.Line -> lineRanges(layout)
                TextGranularity.Word -> wordRanges(text.text, layout)
                TextGranularity.Character -> charRanges(text.text, layout)
            }
            val input = layout.layoutInput
            val fs = input.style.fontSize
            val fontSizePx = if (fs.isSp) with(input.density) { fs.toPx() } else 16f * input.density.density
            val segments = buildSegments(layout, ranges, mode, measurer, granularity, fontSizePx)
            return SegmentedTextLayout(layout, granularity, mode, segments)
        }

        private class Range(val start: Int, val end: Int, val word: Int)

        private fun lineRanges(layout: TextLayoutResult): List<Range> {
            val text = layout.layoutInput.text.text
            val out = ArrayList<Range>()
            for (l in 0 until layout.lineCount) {
                val s = layout.getLineStart(l)
                val e = layout.getLineEnd(l, visibleEnd = true)
                if (e > s && text.substring(s, e).isNotBlank()) out.add(Range(s, e, out.size))
            }
            return out
        }

        private fun wordRanges(text: String, layout: TextLayoutResult): List<Range> {
            val out = ArrayList<Range>()
            val b = graphemeBoundaries(text)
            var word = 0
            var runStart = -1
            for (k in 0 until b.size - 1) {
                val s = b[k]; val e = b[k + 1]
                val blank = text.substring(s, e).isBlank()
                if (!blank && runStart < 0) runStart = s
                if (blank && runStart >= 0) {
                    splitByLine(layout, runStart, s, word, out); word++; runStart = -1
                }
            }
            if (runStart >= 0) splitByLine(layout, runStart, text.length, word, out)
            return out
        }

        private fun splitByLine(layout: TextLayoutResult, s: Int, e: Int, word: Int, out: MutableList<Range>) {
            val l1 = layout.getLineForOffset(s)
            val l2 = layout.getLineForOffset(e - 1)
            if (l1 == l2) { out.add(Range(s, e, word)); return }
            var start = s
            for (l in l1..l2) {
                val end = min(e, layout.getLineEnd(l))
                if (end > start) out.add(Range(start, end, word))
                start = end
            }
        }

        private fun charRanges(text: String, layout: TextLayoutResult): List<Range> {
            val out = ArrayList<Range>()
            val b = graphemeBoundaries(text)
            var word = 0
            var inWord = false
            for (k in 0 until b.size - 1) {
                val s = b[k]; val e = b[k + 1]
                if (text.substring(s, e).isBlank()) {
                    if (inWord) { word++; inWord = false }
                } else {
                    inWord = true
                    out.add(Range(s, e, word))
                }
            }
            return out
        }

        private fun buildSegments(
            layout: TextLayoutResult,
            ranges: List<Range>,
            mode: SegmentRenderMode,
            measurer: TextMeasurer,
            granularity: TextGranularity?,
            fontSizePx: Float,
        ): List<TextSegment> {
            if (ranges.isEmpty()) return emptyList()
            val text = layout.layoutInput.text
            val lastLine = layout.lineCount - 1
            val boxes = ranges.map { r -> rangeBounds(layout, r, granularity) }
            // Horizontal clip extents: grow each segment toward its neighbours on the same line up to the
            // midpoint of the gap, so word/line clips include overhanging ink without touching neighbours.
            val clipLeft = FloatArray(ranges.size) { boxes[it].left }
            val clipRight = FloatArray(ranges.size) { boxes[it].right }
            val byLine = ranges.indices.groupBy { layout.getLineForOffset(ranges[it].start) }
            for ((line, idxs) in byLine) {
                val sorted = idxs.sortedBy { boxes[it].left }
                val lineLeft = min(layout.getLineLeft(line), boxes[sorted.first()].left)
                val lineRight = max(layout.getLineRight(line), boxes[sorted.last()].right)
                for ((k, i) in sorted.withIndex()) {
                    clipLeft[i] = if (k == 0) lineLeft - fontSizePx else (boxes[sorted[k - 1]].right + boxes[i].left) / 2f
                    clipRight[i] = if (k == sorted.size - 1) lineRight + fontSizePx else (boxes[i].right + boxes[sorted[k + 1]].left) / 2f
                }
            }
            return ranges.mapIndexed { i, r ->
                val line = layout.getLineForOffset(r.start)
                val box = boxes[i]
                val top = if (line == 0) layout.getLineTop(line) - fontSizePx else layout.getLineTop(line)
                val bottom = if (line == lastLine) layout.getLineBottom(line) + fontSizePx else layout.getLineBottom(line)
                val baseline = layout.getLineBaseline(line)
                var isolated: TextLayoutResult? = null
                var offset = Offset.Zero
                if (mode == SegmentRenderMode.Isolated) {
                    val sub = text.subSequence(r.start, r.end)
                    val res = measurer.measure(
                        text = sub,
                        style = layout.layoutInput.style,
                        overflow = TextOverflow.Visible,
                        softWrap = false,
                        maxLines = 1,
                        density = layout.layoutInput.density,
                        layoutDirection = layout.layoutInput.layoutDirection,
                        fontFamilyResolver = layout.layoutInput.fontFamilyResolver,
                    )
                    val subBox = rangeBounds(res, Range(0, sub.length, 0), granularity)
                    // Keep the segment's original ink position: horizontally align the advance boxes, vertically the baselines.
                    offset = Offset(box.left - subBox.left, baseline - res.firstBaseline)
                    isolated = res
                }
                TextSegment(
                    index = i,
                    start = r.start,
                    end = r.end,
                    text = text.text.substring(r.start, r.end),
                    lineIndex = line,
                    wordIndex = if (granularity == TextGranularity.Word) i else r.word,
                    bounds = box,
                    clipRect = Rect(clipLeft[i], top, clipRight[i], bottom),
                    baseline = baseline,
                    isolated = isolated,
                    isolatedOffset = offset,
                )
            }
        }

        private fun rangeBounds(layout: TextLayoutResult, r: Range, granularity: TextGranularity?): Rect {
            if (granularity == TextGranularity.Line) {
                val l = layout.getLineForOffset(r.start)
                return Rect(layout.getLineLeft(l), layout.getLineTop(l), layout.getLineRight(l), layout.getLineBottom(l))
            }
            var left = Float.POSITIVE_INFINITY; var top = Float.POSITIVE_INFINITY
            var right = Float.NEGATIVE_INFINITY; var bottom = Float.NEGATIVE_INFINITY
            var i = r.start
            val text = layout.layoutInput.text.text
            while (i < r.end) {
                val b = layout.getBoundingBox(i)
                if (b.width > 0f || b.height > 0f) {
                    left = min(left, b.left); right = max(right, b.right)
                    top = min(top, b.top); bottom = max(bottom, b.bottom)
                }
                i += if (text[i].isHighSurrogate()) 2 else 1
            }
            if (left == Float.POSITIVE_INFINITY) {
                val l = layout.getLineForOffset(r.start)
                val x = layout.getHorizontalPosition(r.start, true)
                return Rect(x, layout.getLineTop(l), x, layout.getLineBottom(l))
            }
            return Rect(left, top, right, bottom)
        }
    }
}

/**
 * Draws a single segment at its natural position. Wrap the call in `withTransform`/`translate`
 * to animate it. When [brush] is set the segment is drawn through the full paragraph (clip
 * strategy) so that the brush spans the whole text consistently.
 */
internal fun DrawScope.drawSegment(
    layout: SegmentedTextLayout,
    segment: TextSegment,
    color: Color = Color.Unspecified,
    alpha: Float = Float.NaN,
    brush: Brush? = null,
    shadow: Shadow? = null,
    drawStyle: DrawStyle? = null,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    val iso = segment.isolated
    if (brush == null && iso != null) {
        translate(segment.isolatedOffset.x, segment.isolatedOffset.y) {
            drawText(iso, color = color, alpha = alpha, shadow = shadow, drawStyle = drawStyle, blendMode = blendMode)
        }
        return
    }
    val c = segment.clipRect
    clipRect(c.left, c.top, c.right, c.bottom) {
        if (brush != null) {
            drawText(layout.layout, brush = brush, alpha = alpha, shadow = shadow, drawStyle = drawStyle ?: Fill, blendMode = blendMode)
        } else {
            drawText(layout.layout, color = color, alpha = alpha, shadow = shadow, drawStyle = drawStyle, blendMode = blendMode)
        }
    }
}
