package io.github.mejdi14.textmotion.internal.blurtext

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.mejdi14.textmotion.internal.PlatformCapabilities
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.TextSegment
import io.github.mejdi14.textmotion.internal.Transform3D
import io.github.mejdi14.textmotion.internal.drawSegment
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Draws BlurText pieces with a per-piece Gaussian blur.
 *
 * - Android 12+ and every Skia target: each blurred piece is recorded into its own reusable
 *   [GraphicsLayer] with a `BlurEffect` (decal edges, sigma matching CSS `blur()`), then drawn
 *   through the piece transform. Rested pieces (blur ≈ 0) are drawn directly, without a layer.
 * - Android < 12 (no `RenderEffect`): multi-pass approximation — the piece is drawn several times
 *   on two rings of offsets whose radii follow the blur sigma; the per-pass alpha is chosen so fully
 *   overlapped areas reach the requested opacity and edges fade out like a blur.
 */
internal class BlurPieceRenderer(private val graphicsContext: GraphicsContext) {
    private val layers = ArrayList<GraphicsLayer>()
    private val layerRadius = ArrayList<Float>()
    private val transform = Transform3D()
    private val skew = Matrix()

    /** Releases every graphics layer (call on dispose). */
    fun release() {
        layers.forEach { graphicsContext.releaseGraphicsLayer(it) }
        layers.clear(); layerRadius.clear()
    }

    private fun layer(slot: Int): GraphicsLayer {
        while (layers.size <= slot) { layers.add(graphicsContext.createGraphicsLayer()); layerRadius.add(-1f) }
        return layers[slot]
    }

    /**
     * Draws [segment] with the animated channel values [v] (dp units, see `CH_*`).
     *
     * @param slot stable per-piece index used to reuse a graphics layer.
     */
    fun DrawScope.drawPiece(
        layout: SegmentedTextLayout,
        segment: TextSegment,
        slot: Int,
        v: FloatArray,
        extraSigmaPx: Float,
        alphaMultiplier: Float,
        spec: BlurTextSpec,
        color: Color,
        tintColor: Color,
        tint: Float,
        brush: Brush?,
    ) {
        val alpha = v[CH_OPACITY] * alphaMultiplier
        if (alpha <= 0.002f) return
        val pieceSigma = v[CH_BLUR] * density
        val sigma = if (extraSigmaPx > 0f) kotlin.math.sqrt(pieceSigma * pieceSigma + extraSigmaPx * extraSigmaPx) else pieceSigma
        val identity = v[CH_X] == 0f && v[CH_Y] == 0f && v[CH_SCALE] == 1f && v[CH_ROT_X] == 0f &&
            v[CH_ROT_Y] == 0f && v[CH_SKEW_X] == 0f
        if (identity) {
            drawBlurred(layout, segment, slot, sigma, alpha, color, tintColor, tint, brush)
            return
        }
        val box = segment.bounds
        val px = box.left + spec.originX * box.width
        val py = box.top + spec.originY * box.height
        val m = transform.set(
            pivotX = px, pivotY = py,
            translationX = v[CH_X] * density, translationY = v[CH_Y] * density,
            scaleX = v[CH_SCALE], scaleY = v[CH_SCALE],
            rotationX = v[CH_ROT_X], rotationY = v[CH_ROT_Y],
            perspective = spec.perspective * density,
        )
        if (v[CH_SKEW_X] != 0f) {
            val t = tan(v[CH_SKEW_X] * PI.toFloat() / 180f)
            skew.reset()
            skew.values[Matrix.SkewX] = t
            skew.values[Matrix.TranslateX] = -t * py
            m.timesAssign(skew)
        }
        withTransform({ transform(m) }) {
            drawBlurred(layout, segment, slot, sigma, alpha, color, tintColor, tint, brush)
        }
    }

    private fun DrawScope.drawBlurred(
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

    private fun DrawScope.drawTinted(
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

private fun Rect.union(o: Rect): Rect = Rect(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
