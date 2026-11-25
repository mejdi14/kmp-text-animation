package io.github.mejdi14.textmotion.internal.gradienttext

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.mejdi14.textmotion.GradientTextVariant
import io.github.mejdi14.textmotion.internal.PlatformCapabilities
import io.github.mejdi14.textmotion.internal.auroratext.AuroraMath
import io.github.mejdi14.textmotion.internal.blurRadiusForSigma
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Immutable description of the gradient (everything that is not animated per frame). */
internal data class GradientSpec(
    /** Normalised palette (at least two colours). */
    val colors: List<Color>,
    val variant: GradientTextVariant,
    val angleDegrees: Float,
    val scale: Float,
    val yoyo: Boolean,
)

/** Per-frame animated inputs. Owned by the renderer and overwritten each frame (no allocation). */
internal class GradientFrame {
    var cycle = 0f
    var pull = 0f
    var px = 0.5f
    var py = 0.5f
}

/**
 * Draws GradientText: a gradient confined to the glyphs (mask layer + `SrcAtop`), the optional
 * gradient pill border and the optional blurred glow copy. Brushes are cached and only
 * translated / rotated / scaled per frame, never rebuilt.
 */
internal class GradientTextRenderer(private val graphicsContext: GraphicsContext) {
    val frame = GradientFrame()

    private val layerPaint = Paint()
    private val saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(GLOW_SATURATION) })
    private var glowLayer: GraphicsLayer? = null
    private var glowRadius = -1f

    // Brush caches (rebuilt only when their inputs change).
    private var linearColors: List<Color>? = null
    private var linearAngle = Float.NaN
    private var linearScale = Float.NaN
    private var linearW = -1f
    private var linearH = -1f
    private var linearBrush: Brush? = null
    private var baseColors: List<Color>? = null
    private var baseAngle = Float.NaN
    private var baseW = -1f
    private var baseH = -1f
    private var baseBrush: Brush? = null
    private var radialColors: List<Color>? = null
    private var radialBrushes: List<Brush> = emptyList()
    private var sweepColors: List<Color>? = null
    private var sweepScale = Float.NaN
    private var sweepBrush: Brush? = null
    private var lineLength = 0f
    private var dirX = 1f
    private var dirY = 0f

    /** Releases the glow graphics layer (call on dispose). */
    fun release() {
        glowLayer?.let { graphicsContext.releaseGraphicsLayer(it) }
        glowLayer = null
    }

    /**
     * Fills the [bounds] (box frame) with the gradient wherever [drawMask] painted opaque pixels.
     * [alpha] and [filter] apply to the composited result.
     */
    fun DrawScope.maskedFill(
        spec: GradientSpec,
        box: Size,
        bounds: Rect,
        alpha: Float = 1f,
        filter: ColorFilter? = null,
        drawMask: DrawScope.() -> Unit,
    ) {
        layerPaint.alpha = alpha
        layerPaint.colorFilter = filter
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, layerPaint)
            drawMask()
            fillGradient(spec, box, bounds, BlendMode.SrcAtop)
            canvas.restore()
        }
        layerPaint.colorFilter = null
    }

    /** Draws the gradient over [bounds] with [blend]; geometry is in the box frame. */
    fun DrawScope.fillGradient(spec: GradientSpec, box: Size, bounds: Rect, blend: BlendMode) {
        when (spec.variant) {
            GradientTextVariant.Linear -> fillLinear(spec, box, bounds, blend)
            GradientTextVariant.Flow -> fillFlow(spec, box, bounds, blend)
            GradientTextVariant.Conic -> fillConic(spec, box, bounds, blend)
        }
    }

    // ------------------------------------------------------------------ linear

    private fun DrawScope.fillLinear(spec: GradientSpec, box: Size, bounds: Rect, blend: BlendMode) {
        val brush = linearBrush(spec, box)
        val offsetPercent = GradientTextMath.linearOffsetPercent(frame.cycle, spec.scale, spec.yoyo) +
            GradientTextMath.pointerOffsetPercent(frame.px, spec.scale, frame.pull)
        // Pattern coordinate 0 must sit at -offset along the gradient line.
        val shift = -offsetPercent / 100f * lineLength
        val tx = dirX * shift
        val ty = dirY * shift
        translate(tx, ty) {
            drawRect(brush, topLeft = Offset(bounds.left - tx, bounds.top - ty), size = bounds.size, blendMode = blend)
        }
    }

    private fun linearBrush(spec: GradientSpec, box: Size): Brush {
        if (linearBrush == null || spec.colors != linearColors || spec.angleDegrees != linearAngle ||
            spec.scale != linearScale || box.width != linearW || box.height != linearH
        ) {
            linearColors = spec.colors; linearAngle = spec.angleDegrees; linearScale = spec.scale
            linearW = box.width; linearH = box.height
            val (start, end) = AuroraMath.cssGradientLine(spec.angleDegrees, box.width, box.height)
            lineLength = hypot(end.x - start.x, end.y - start.y).coerceAtLeast(1f)
            val rad = spec.angleDegrees * (PI.toFloat() / 180f)
            dirX = sin(rad)
            dirY = -cos(rad)
            val period = GradientTextMath.periodPercent(spec.scale) / 100f * lineLength
            val repeatEnd = Offset(start.x + dirX * period, start.y + dirY * period)
            linearBrush = Brush.linearGradient(
                GradientTextMath.loopColors(spec.colors), start = start, end = repeatEnd, tileMode = TileMode.Repeated,
            )
        }
        return linearBrush!!
    }

    // ------------------------------------------------------------------ flow

    private fun DrawScope.fillFlow(spec: GradientSpec, box: Size, bounds: Rect, blend: BlendMode) {
        if (baseBrush == null || spec.colors != baseColors || spec.angleDegrees != baseAngle ||
            box.width != baseW || box.height != baseH
        ) {
            baseColors = spec.colors; baseAngle = spec.angleDegrees; baseW = box.width; baseH = box.height
            val (start, end) = AuroraMath.cssGradientLine(spec.angleDegrees, box.width, box.height)
            baseBrush = Brush.linearGradient(spec.colors, start = start, end = end, tileMode = TileMode.Clamp)
        }
        drawRect(baseBrush!!, topLeft = bounds.topLeft, size = bounds.size, blendMode = blend)
        if (radialColors != spec.colors) {
            radialColors = spec.colors
            // Unit-radius brush; each blob scales it into its ellipse.
            radialBrushes = spec.colors.map { c ->
                Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = Offset.Zero, radius = 1f, tileMode = TileMode.Clamp)
            }
        }
        for (i in spec.colors.indices) {
            val b = GradientTextMath.flowBlob(i, frame.cycle, spec.scale, frame.pull, frame.px, frame.py)
            translate(b.xPercent / 100f * box.width, b.yPercent / 100f * box.height) {
                scale(b.radiusXPercent / 100f * box.width, b.radiusYPercent / 100f * box.height, pivot = Offset.Zero) {
                    drawRect(radialBrushes[i], topLeft = Offset(-1f, -1f), size = Size(2f, 2f), blendMode = blend)
                }
            }
        }
    }

    // ------------------------------------------------------------------ conic

    private fun DrawScope.fillConic(spec: GradientSpec, box: Size, bounds: Rect, blend: BlendMode) {
        if (sweepBrush == null || spec.colors != sweepColors || spec.scale != sweepScale) {
            sweepColors = spec.colors; sweepScale = spec.scale
            val stops = GradientTextMath.conicStops(GradientTextMath.normalizeColors(spec.colors), spec.scale)
            sweepBrush = Brush.sweepGradient(*stops.toTypedArray(), center = Offset.Zero)
        }
        val cx = GradientTextMath.conicCenterXPercent(frame.px, frame.pull) / 100f * box.width
        val cy = GradientTextMath.conicCenterYPercent(frame.py, frame.pull) / 100f * box.height
        val reach = max(
            max(hypot(bounds.left - cx, bounds.top - cy), hypot(bounds.right - cx, bounds.top - cy)),
            max(hypot(bounds.left - cx, bounds.bottom - cy), hypot(bounds.right - cx, bounds.bottom - cy)),
        ) + 1f
        // Compose sweeps start at 3 o'clock, CSS conic gradients at 12 o'clock.
        val from = GradientTextMath.conicFromDegrees(frame.cycle, spec.yoyo) - 90f
        translate(cx, cy) {
            rotate(from, pivot = Offset.Zero) {
                drawRect(sweepBrush!!, topLeft = Offset(-reach, -reach), size = Size(2f * reach, 2f * reach), blendMode = blend)
            }
        }
    }

    // ------------------------------------------------------------------ border

    /** Gradient pill ring of [width] px inside the box. */
    fun DrawScope.drawRing(spec: GradientSpec, box: Size, bounds: Rect, width: Float) {
        if (width <= 0f || box.width <= width || box.height <= width) return
        val radius = (minOf(box.width, box.height) - width) / 2f
        maskedFill(spec, box, bounds) {
            drawRoundRect(
                Color.Black,
                topLeft = Offset(width / 2f, width / 2f),
                size = Size(box.width - width, box.height - width),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width),
            )
        }
    }

    // ------------------------------------------------------------------ glow

    /**
     * Blurred, saturated copy of the filled content behind it (CSS `filter: blur(0.3em)
     * saturate(1.4)`, opacity [glow]). [sigmaPx] is the CSS blur standard deviation.
     * Where `RenderEffect` is unavailable (Android < 12) a ring of offset passes approximates it.
     */
    fun DrawScope.drawGlow(
        spec: GradientSpec,
        box: Size,
        bounds: Rect,
        glow: Float,
        sigmaPx: Float,
        drawMask: DrawScope.() -> Unit,
    ) {
        if (glow <= 0f || sigmaPx < 0.25f) return
        if (!PlatformCapabilities.supportsRenderEffect) {
            drawGlowMultiPass(spec, box, bounds, glow, sigmaPx, drawMask)
            return
        }
        val pad = ceil(sigmaPx * 3f)
        val left = floor(bounds.left - pad)
        val top = floor(bounds.top - pad)
        val size = IntSize(ceil(bounds.right + pad - left).toInt().coerceAtLeast(1), ceil(bounds.bottom + pad - top).toInt().coerceAtLeast(1))
        val layer = glowLayer ?: graphicsContext.createGraphicsLayer().also { glowLayer = it }
        val radius = blurRadiusForSigma(sigmaPx)
        if (radius != glowRadius) {
            layer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
            glowRadius = radius
        }
        layer.colorFilter = saturate
        layer.alpha = glow.coerceIn(0f, 1f)
        layer.topLeft = IntOffset(left.toInt(), top.toInt())
        layer.record(this, layoutDirection, size) {
            translate(-left, -top) { maskedFill(spec, box, bounds, drawMask = drawMask) }
        }
        drawLayer(layer)
    }

    private fun DrawScope.drawGlowMultiPass(
        spec: GradientSpec,
        box: Size,
        bounds: Rect,
        glow: Float,
        sigmaPx: Float,
        drawMask: DrawScope.() -> Unit,
    ) {
        val rings = if (sigmaPx < 2f) 1 else 2
        val passes = 1 + rings * RING_SAMPLES
        val target = glow.coerceIn(0f, 0.999f)
        val passAlpha = 1f - (1f - target).pow(1f / passes)
        val wide = Rect(bounds.left - sigmaPx * 2f, bounds.top - sigmaPx * 2f, bounds.right + sigmaPx * 2f, bounds.bottom + sigmaPx * 2f)
        maskedFill(spec, box, wide, passAlpha, saturate, drawMask)
        for (ring in 1..rings) {
            val radius = sigmaPx * if (rings == 1) 1f else (if (ring == 1) 0.7f else 1.6f)
            val phase = if (ring == 2) PI.toFloat() / RING_SAMPLES else 0f
            for (k in 0 until RING_SAMPLES) {
                val a = phase + k * 2f * PI.toFloat() / RING_SAMPLES
                translate(cos(a) * radius, sin(a) * radius) {
                    maskedFill(spec, box, wide, passAlpha, saturate, drawMask)
                }
            }
        }
    }

    private companion object {
        const val GLOW_SATURATION = 1.4f
        const val RING_SAMPLES = 8
    }
}
