package io.github.mejdi14.textmotion.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Draws one text segment with a Gaussian blur (CSS `blur()` sigma, in px). Shared by BlurText and
 * TrueFocus.
 *
 * - Android 12+ and every Skia target: each blurred segment is recorded into its own reusable
 *   [GraphicsLayer] with a `BlurEffect` (decal edges). Segments with sigma < 0.25 px are drawn directly.
 * - Android < 12 (no `RenderEffect`): multi-pass approximation on two rings of offsets whose radii
 *   follow the sigma; the per-pass alpha makes fully overlapped areas reach the requested opacity.
 */
internal class SegmentBlurRenderer(private val graphicsContext: GraphicsContext) {
    private val layers = ArrayList<GraphicsLayer>()
    private val layerRadius = ArrayList<Float>()

    /** Releases every graphics layer (call on dispose). */
    fun release() {
        layers.forEach { graphicsContext.releaseGraphicsLayer(it) }
        layers.clear(); layerRadius.clear()
    }

    private fun layer(slot: Int): GraphicsLayer {
        while (layers.size <= slot) { layers.add(graphicsContext.createGraphicsLayer()); layerRadius.add(-1f) }
        return layers[slot]
    }

    /** @param slot stable per-segment index used to reuse a graphics layer. */
    fun DrawScope.drawBlurred(
        layout: SegmentedTextLayout,
        segment: TextSegment,
        slot: Int,
        sigmaPx: Float,
        alpha: Float,
        color: Color,
        tintColor: Color,
        tint: Float,
        brush: Brush?,
    ) {
        if (sigmaPx < 0.25f) {
            drawTinted(layout, segment, alpha, color, tintColor, tint, brush)
            return
        }
        if (!PlatformCapabilities.supportsRenderEffect) {
            drawMultiPass(layout, segment, sigmaPx, alpha, color, tintColor, tint, brush)
            return
        }
        val pad = ceil(sigmaPx * 3f)
        val r = segment.clipRect.union(segment.bounds)
        val left = floor(r.left - pad); val top = floor(r.top - pad)
        val size = IntSize(ceil(r.right + pad - left).toInt().coerceAtLeast(1), ceil(r.bottom + pad - top).toInt().coerceAtLeast(1))
        val layer = layer(slot)
        val radius = blurRadiusForSigma(sigmaPx)
        if (layerRadius[slot] != radius) {
            layer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
            layerRadius[slot] = radius
        }
        layer.alpha = alpha
        layer.topLeft = IntOffset(left.toInt(), top.toInt())
        layer.record(this, layoutDirection, size) {
            translate(-left, -top) { drawTinted(layout, segment, 1f, color, tintColor, tint, brush) }
        }
        drawLayer(layer)
    }

    private fun DrawScope.drawMultiPass(
        layout: SegmentedTextLayout,
        segment: TextSegment,
        sigmaPx: Float,
        alpha: Float,
        color: Color,
        tintColor: Color,
        tint: Float,
        brush: Brush?,
    ) {
        val rings = if (sigmaPx < 2f) 1 else 2
        val passes = 1 + rings * RING_SAMPLES
        // Same-alpha passes composited with src-over reach `alpha` where all of them overlap.
        val passAlpha = 1f - (1f - alpha.coerceAtMost(0.999f)).pow(1f / passes)
        drawTinted(layout, segment, passAlpha, color, tintColor, tint, brush)
        for (ring in 1..rings) {
            val radius = sigmaPx * if (rings == 1) 1f else (if (ring == 1) 0.7f else 1.6f)
            val phase = if (ring == 2) PI.toFloat() / RING_SAMPLES else 0f
            for (k in 0 until RING_SAMPLES) {
                val a = phase + k * 2f * PI.toFloat() / RING_SAMPLES
                translate(cos(a) * radius, sin(a) * radius) {
                    drawTinted(layout, segment, passAlpha, color, tintColor, tint, brush)
                }
            }
        }
    }

    fun DrawScope.drawTinted(
        layout: SegmentedTextLayout,
        segment: TextSegment,
        alpha: Float,
        color: Color,
        tintColor: Color,
        tint: Float,
        brush: Brush?,
    ) {
        if (tint <= 0f || tintColor == Color.Unspecified) {
            drawSegment(layout, segment, color = color, alpha = alpha, brush = brush)
            return
        }
        if (brush == null) {
            drawSegment(layout, segment, color = androidx.compose.ui.graphics.lerp(color, tintColor, tint), alpha = alpha)
            return
        }
        // Gradient text: cross-fade between the brush and the tint colour.
        if (tint < 1f) drawSegment(layout, segment, alpha = alpha * (1f - tint), brush = brush)
        drawSegment(layout, segment, color = tintColor, alpha = alpha * tint)
    }

    private companion object {
        const val RING_SAMPLES = 8
    }
}

internal fun blurRadiusForSigma(sigma: Float): Float = if (sigma <= 0.5f) sigma.coerceAtLeast(0f) else (sigma - 0.5f) / 0.57735f

private fun Rect.union(o: Rect): Rect = Rect(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
