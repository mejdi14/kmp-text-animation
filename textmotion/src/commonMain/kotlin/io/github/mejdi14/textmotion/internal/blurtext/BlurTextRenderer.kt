package io.github.mejdi14.textmotion.internal.blurtext

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
import io.github.mejdi14.textmotion.internal.SegmentBlurRenderer
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
    private val blur = SegmentBlurRenderer(graphicsContext)
    private val transform = Transform3D()
    private val skew = Matrix()

    /** Releases every graphics layer (call on dispose). */
    fun release() {
        blur.release()
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
            with(blur) { drawBlurred(layout, segment, slot, sigma, alpha, color, tintColor, tint, brush) }
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
            with(blur) { drawBlurred(layout, segment, slot, sigma, alpha, color, tintColor, tint, brush) }
        }
    }

}
