package io.github.mejdi14.textmotion.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrain
import io.github.mejdi14.textmotion.SegmentRenderMode
import io.github.mejdi14.textmotion.TextGranularity
import kotlin.math.ceil
import kotlin.math.roundToInt

@Stable
internal class LayoutHolder {
    var value: SegmentedTextLayout? by mutableStateOf(null, neverEqualPolicy())
    /** Offset of the text origin inside the composable (equals the draw padding in px). */
    var originPx: Float = 0f
}

/**
 * Base building block of every TextMotion effect: measures [text] once per input change (never
 * per frame), sizes itself like `BasicText`, exposes accessibility text semantics, and calls
 * [onDraw] in the draw phase with a cached [SegmentedTextLayout].
 *
 * Animated values must be read inside [onDraw] so a new frame only invalidates drawing.
 *
 * @param drawPadding extra room reserved around the text for effects whose ink extends past the
 *   glyph boxes (blur, glitch offsets, scale). The text origin is shifted by this amount.
 * @param holder optional externally-owned holder, so modifiers placed before the layout (pointer
 *   tracking, etc.) can read the current geometry.
 * @param onLayoutChanged called during measurement each time a new [SegmentedTextLayout] is built
 *   (text, style, constraints or segmentation changed). Never called per frame. Effects that
 *   animate between layouts (e.g. TextMorph) diff the old and new geometry here.
 */
@Composable
internal fun AnimatedTextLayout(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    granularity: TextGranularity? = null,
    renderMode: SegmentRenderMode = SegmentRenderMode.Auto,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    drawPadding: Dp = 0.dp,
    holder: LayoutHolder = remember { LayoutHolder() },
    onLayoutChanged: ((SegmentedTextLayout) -> Unit)? = null,
    onDraw: DrawScope.(SegmentedTextLayout) -> Unit,
) {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val resolvedStyle = remember(style) { style.withDefaultColor() }
    Layout(
        modifier = modifier
            .semantics { this.text = text }
            .drawBehind {
                val l = holder.value ?: return@drawBehind
                val o = holder.originPx
                translate(o, o) { onDraw(l) }
            },
    ) { _, constraints ->
        val pad = drawPadding.roundToPx()
        val inner = Constraints(
            minWidth = (constraints.minWidth - 2 * pad).coerceAtLeast(0),
            maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - 2 * pad).coerceAtLeast(0) else Constraints.Infinity,
            minHeight = 0,
            maxHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight - 2 * pad).coerceAtLeast(0) else Constraints.Infinity,
        )
        val result = measurer.measure(
            text = text,
            style = resolvedStyle,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            constraints = inner,
            layoutDirection = layoutDirection,
            density = this,
        )
        val previous = holder.value
        if (previous == null || previous.layout.layoutInput != result.layoutInput || previous.layout.size != result.size ||
            previous.granularity != granularity || previous.requestedMode != renderMode
        ) {
            val built = SegmentedTextLayout.build(result, granularity, renderMode, measurer).also { it.requestedMode = renderMode }
            holder.value = built
            onLayoutChanged?.invoke(built)
        }
        holder.originPx = pad.toFloat()
        val w = result.size.width + 2 * pad
        val h = ceil(result.size.height.toFloat()).roundToInt() + 2 * pad
        val size = constraints.constrain(androidx.compose.ui.unit.IntSize(w, h))
        layout(size.width, size.height) {}
    }
}

/** Text colour falls back to black when neither the style nor the caller specify one. */
internal fun TextStyle.withDefaultColor(): TextStyle =
    if (color == Color.Unspecified && brush == null) copy(color = Color.Black) else this
