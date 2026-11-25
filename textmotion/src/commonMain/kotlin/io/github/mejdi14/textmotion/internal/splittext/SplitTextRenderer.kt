package io.github.mejdi14.textmotion.internal.splittext

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.mejdi14.textmotion.internal.SegmentBlurRenderer
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.TextSegment
import io.github.mejdi14.textmotion.internal.Transform3D
import kotlin.math.PI
import kotlin.math.tan

/**
 * Draws one SplitText unit (character, word or line) with its animated channel values: opacity,
 * translation, scale, 3D rotation with perspective, skew, optional blur (through the shared
 * [SegmentBlurRenderer], which has the Android < 12 multi-pass fallback) and the accent tint.
 */
internal class SplitTextRenderer(graphicsContext: GraphicsContext) {
    private val blur = SegmentBlurRenderer(graphicsContext)
    private val transform = Transform3D()
    private val skew = Matrix()

    /** Releases every graphics layer (call on dispose). */
    fun release() {
        blur.release()
    }

    /**
     * @param v channel values (see `CH_*`) in px/degrees.
     * @param extraY additional vertical offset (hover spring), px.
     * @param mask clip the unit to its own line box (reference `mask`).
     */
    fun DrawScope.drawUnit(
        layout: SegmentedTextLayout,
        segment: TextSegment,
        slot: Int,
        v: FloatArray,
        spec: SplitTextSpec,
        extraY: Float,
        mask: Boolean,
        color: Color,
        accentColor: Color,
        accent: Float,
        brush: Brush?,
    ) {
        val alpha = v[CH_OPACITY].coerceIn(0f, 1f)
        if (alpha <= 0.002f) return
        val sigma = v[CH_BLUR].coerceAtLeast(0f)
        val ty = v[CH_Y] + extraY
        val identity = v[CH_X] == 0f && ty == 0f && v[CH_SCALE] == 1f && v[CH_ROT_X] == 0f &&
            v[CH_ROT_Y] == 0f && v[CH_ROT_Z] == 0f && v[CH_SKEW_X] == 0f
        val box = segment.bounds
        val py = box.top + spec.originY * box.height
        withTransform({
            if (mask) {
                val c = segment.clipRect
                val l = layout.layout
                clipRect(c.left, l.getLineTop(segment.lineIndex), c.right, l.getLineBottom(segment.lineIndex))
            }
            if (!identity) {
                val m = transform.set(
                    pivotX = box.left + spec.originX * box.width, pivotY = py,
                    translationX = v[CH_X], translationY = ty,
                    scaleX = v[CH_SCALE], scaleY = v[CH_SCALE],
                    rotationX = v[CH_ROT_X], rotationY = v[CH_ROT_Y], rotationZ = v[CH_ROT_Z],
                    perspective = spec.perspectivePx,
                )
                if (v[CH_SKEW_X] != 0f) {
                    val t = tan(v[CH_SKEW_X] * PI.toFloat() / 180f)
                    skew.reset()
                    skew.values[Matrix.SkewX] = t
                    skew.values[Matrix.TranslateX] = -t * py
                    m.timesAssign(skew)
                }
                transform(m)
            }
        }) {
            with(blur) { drawBlurred(layout, segment, slot, sigma, alpha, color, accentColor, accent, brush) }
        }
    }
}
