package io.github.mejdi14.textmotion.internal.textroll

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.mejdi14.textmotion.TextRollVariant
import io.github.mejdi14.textmotion.TextRollVariants
import io.github.mejdi14.textmotion.internal.SegmentBlurRenderer
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.TextSegment
import io.github.mejdi14.textmotion.internal.Transform3D

/** Reference enter-layer origin `50% 25%`. */
internal val EnterOrigin: TransformOrigin = TransformOrigin(0.5f, 0.25f)

/** Reference exit-layer origin `50% 100%`. */
internal val ExitOrigin: TransformOrigin = TransformOrigin(0.5f, 1f)

/**
 * Draws the two stacked copies of a TextRoll unit: the enter layer (pivot 50%/25% of the glyph
 * box) then the exit layer on top (pivot 50%/100%), each with a CSS-like 3D transform
 * ([Transform3D], `rotateX`/`rotateY` + perspective), backface culling, opacity and an optional
 * blur through the shared [SegmentBlurRenderer] (multi-pass fallback on Android < 12).
 */
internal class TextRollRenderer(graphicsContext: GraphicsContext) {
    private val blur = SegmentBlurRenderer(graphicsContext)
    private val transform = Transform3D()
    private val enterPose = RollPose()
    private val exitPose = RollPose()

    fun release() {
        blur.release()
    }

    /** @param enterProgress / [exitProgress] eased 0..1 progress of each layer. */
    fun DrawScope.drawUnit(
        layout: SegmentedTextLayout,
        segment: TextSegment,
        variants: TextRollVariants,
        enterProgress: Float,
        exitProgress: Float,
        perspectivePx: Float,
        color: Color,
        brush: Brush?,
    ) {
        enterPose.set(variants.enter.initial, variants.enter.animate, enterProgress, density)
        exitPose.set(variants.exit.initial, variants.exit.animate, exitProgress, density)
        drawLayer(layout, segment, enterPose, variants.enter.originOr(EnterOrigin), 2 * segment.index, perspectivePx, color, brush)
        drawLayer(layout, segment, exitPose, variants.exit.originOr(ExitOrigin), 2 * segment.index + 1, perspectivePx, color, brush)
    }

    private fun DrawScope.drawLayer(
        layout: SegmentedTextLayout,
        segment: TextSegment,
        pose: RollPose,
        origin: TransformOrigin,
        slot: Int,
        perspectivePx: Float,
        color: Color,
        brush: Brush?,
    ) {
        if (pose.isHidden) return
        val box = segment.bounds
        val identity = pose.isIdentity
        withTransform({
            if (!identity) {
                transform(
                    transform.set(
                        pivotX = box.left + origin.pivotFractionX * box.width,
                        pivotY = box.top + origin.pivotFractionY * box.height,
                        translationX = pose.translationX,
                        translationY = pose.translationY,
                        scaleX = pose.scale,
                        scaleY = pose.scale,
                        rotationX = pose.rotationX,
                        rotationY = pose.rotationY,
                        perspective = perspectivePx,
                    ),
                )
            }
        }) {
            with(blur) {
                drawBlurred(
                    layout = layout, segment = segment, slot = slot, sigmaPx = pose.blurPx, alpha = pose.opacity,
                    color = color, tintColor = Color.Unspecified, tint = 0f, brush = brush,
                )
            }
        }
    }

    private fun TextRollVariant.originOr(default: TransformOrigin): TransformOrigin = origin ?: default
}
