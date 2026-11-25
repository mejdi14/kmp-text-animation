package io.github.mejdi14.textmotion.internal.fuzzy

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.mejdi14.textmotion.FuzzyDirection
import io.github.mejdi14.textmotion.internal.PlatformCapabilities
import io.github.mejdi14.textmotion.internal.RuntimeShaderEffect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Per-frame inputs of [FuzzRenderer.draw], all in px of the current density. */
internal class FuzzFrameParams {
    var amp: Float = 0f
    var step: Int = 1
    var seed: Int = 0
    var direction: FuzzyDirection = FuzzyDirection.Horizontal
    var split: Int = 0
    var jitter: Float = 0f
    var lightInk: Boolean = false
    var opacity: Float = 1f
}

/**
 * Renders FuzzyText.
 *
 * - **Shader path** (Android 13+, iOS, Desktop, Web): the crisp text is recorded once per layout
 *   into a [GraphicsLayer] covering the padded area; a runtime-shader render effect displaces it
 *   per pixel every fuzz frame (only uniforms change).
 * - **Canvas fallback** (Android < 13 or shader compile failure): the reference algorithm itself —
 *   the text is rasterised once into an [ImageBitmap] and every frame is re-blitted in `step`-px
 *   slices with random offsets (`drawImage` src/dst rects); colour split uses channel colour
 *   filters with additive blending, inverted for dark inks to obtain the multiply result.
 */
internal class FuzzRenderer(private val shader: RuntimeShaderEffect) {
    private val useShader: Boolean get() = shader.isSupported && PlatformCapabilities.supportsRuntimeShader

    // Shader path: what is recorded in the layer.
    private var recordedLayout: TextLayoutResult? = null
    private var recordedBrush: Brush? = null
    private var recordedSize: IntSize = IntSize.Zero
    private var recordedOrigin: IntOffset = IntOffset.Zero

    // Canvas fallback.
    private var source: ImageBitmap? = null
    private var sourceLayout: TextLayoutResult? = null
    private var sourceBrush: Brush? = null
    private var sourceInset: Int = -1
    private var scratch: ImageBitmap? = null
    private var scratchCanvas: Canvas? = null
    private val slicePaint = Paint().apply { filterQuality = FilterQuality.None }
    private val clearPaint = Paint().apply { blendMode = BlendMode.Clear }
    private val layerPaint = Paint()
    private val channelFilters = arrayOfNulls<ColorFilter>(6)
    private var invertFilter: ColorFilter? = null

    /**
     * Draws the fuzzed text. The receiver's origin is the text origin; [padH]/[padV] are the
     * reserved room around the text and [margin] extra vertical room for ink overhanging the line box.
     */
    fun DrawScope.draw(
        layer: GraphicsLayer,
        layout: TextLayoutResult,
        brush: Brush?,
        padH: Int,
        padV: Int,
        margin: Int,
        inset: Int,
        p: FuzzFrameParams,
    ) {
        if (useShader) drawWithShader(layer, layout, brush, padH, padV, margin, inset, p)
        else drawWithSlices(layout, brush, padH, padV, margin, inset, p)
    }

    private fun DrawScope.drawWithShader(
        layer: GraphicsLayer,
        layout: TextLayoutResult,
        brush: Brush?,
        padH: Int,
        padV: Int,
        margin: Int,
        inset: Int,
        p: FuzzFrameParams,
    ) {
        val size = IntSize(layout.size.width + 2 * padH, layout.size.height + 2 * (padV + margin))
        val origin = IntOffset(padH, padV + margin)
        if (layout !== recordedLayout || brush != recordedBrush || size != recordedSize || origin != recordedOrigin) {
            layer.record(this, layoutDirection, size) {
                translate(origin.x.toFloat(), origin.y.toFloat()) { drawTextLayout(layout, brush) }
            }
            recordedLayout = layout; recordedBrush = brush; recordedSize = size; recordedOrigin = origin
        }
        val splitOn = p.split >= 1
        if (p.amp >= 0.5f || splitOn) {
            shader.setFloat("origin", (origin.x - inset).toFloat(), origin.y.toFloat())
            shader.setFloat("fuzz", p.amp, p.step.toFloat(), (p.seed % FuzzMath.SEED_PERIOD).toFloat(), p.direction.ordinal.toFloat())
            shader.setFloat("chroma", p.split.toFloat(), p.jitter, if (p.lightInk) 1f else 0f, if (splitOn) 1f else 0f)
            // Uniforms are captured when the effect is created, so a new one is needed per fuzz frame.
            layer.renderEffect = shader.renderEffect("content")
        } else {
            layer.renderEffect = null
        }
        layer.alpha = p.opacity
        layer.topLeft = IntOffset(-origin.x, -origin.y)
        drawLayer(layer)
    }

    // ------------------------------------------------------------------ canvas fallback

    private fun DrawScope.ensureSource(layout: TextLayoutResult, brush: Brush?, margin: Int, inset: Int): ImageBitmap {
        val existing = source
        if (existing != null && sourceLayout === layout && sourceBrush == brush && sourceInset == inset &&
            existing.height == layout.size.height + 2 * margin
        ) return existing
        val w = max(1, layout.size.width + 2 * inset)
        val h = max(1, layout.size.height + 2 * margin)
        val bmp = ImageBitmap(w, h)
        CanvasDrawScope().draw(this, layoutDirection, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
            translate(inset.toFloat(), margin.toFloat()) { drawTextLayout(layout, brush) }
        }
        source = bmp; sourceLayout = layout; sourceBrush = brush; sourceInset = inset
        return bmp
    }

    private fun DrawScope.drawWithSlices(
        layout: TextLayoutResult,
        brush: Brush?,
        padH: Int,
        padV: Int,
        margin: Int,
        inset: Int,
        p: FuzzFrameParams,
    ) {
        val src = ensureSource(layout, brush, margin, inset)
        val left = -inset
        val top = -margin
        val splitOn = p.split >= 1
        val isolate = splitOn || p.opacity < 1f
        if (isolate) {
            layerPaint.alpha = p.opacity
            layerPaint.colorFilter = if (splitOn && !p.lightInk) invert() else null
            val bounds = Rect(
                -padH.toFloat(), -(padV + margin).toFloat(),
                (layout.size.width + padH).toFloat(), (layout.size.height + padV + margin).toFloat(),
            )
            drawIntoCanvas { it.saveLayer(bounds, layerPaint) }
        }
        if (!splitOn) {
            slices(src, left, top, 0, 0, 0f, null, BlendMode.SrcOver, padH, p)
        } else {
            // Light ink: (R,0,0)+(0,G,0)+(0,0,B) added. Dark ink: inverted channels added, then the
            // layer is inverted back, which equals the reference multiply of (R,1,1)(1,G,1)(1,1,B).
            slices(src, left, top, -p.split, FuzzMath.SALT_WOBBLE_0, p.jitter, channel(0, p.lightInk), BlendMode.Plus, padH, p)
            slices(src, left, top, 0, 0, 0f, channel(1, p.lightInk), BlendMode.Plus, padH, p)
            slices(src, left, top, p.split, FuzzMath.SALT_WOBBLE_2, p.jitter, channel(2, p.lightInk), BlendMode.Plus, padH, p)
        }
        if (isolate) drawIntoCanvas { it.restore() }
    }

    /** One reference `fuzz(image, shift, jitter, step, amp)` pass. */
    private fun DrawScope.slices(
        src: ImageBitmap,
        left: Int,
        top: Int,
        shift: Int,
        wobbleSalt: Int,
        jitter: Float,
        filter: ColorFilter?,
        blend: BlendMode,
        padH: Int,
        p: FuzzFrameParams,
    ) {
        val sw = src.width
        val sh = src.height
        val vertical = p.direction == FuzzyDirection.Vertical
        val sx = if (vertical) 0 else shift
        val sy = if (vertical) shift else 0
        val step = p.step
        val seed = p.seed
        if (p.amp < 0.5f) {
            drawImage(src, dstOffset = IntOffset(left + sx, top + sy), colorFilter = filter, blendMode = blend, filterQuality = FilterQuality.None)
            return
        }
        if (vertical) {
            var x = 0
            var j = 0
            while (x < sw) {
                val w = min(step, sw - x)
                val dy = FuzzMath.offset(j, seed, FuzzMath.SALT_COLS, p.amp) + FuzzMath.offset(j, seed, wobbleSalt, jitter)
                drawImage(
                    src, IntOffset(x, 0), IntSize(w, sh), IntOffset(left + x, top + dy + sy), IntSize(w, sh),
                    colorFilter = filter, blendMode = blend, filterQuality = FilterQuality.None,
                )
                x += step; j++
            }
            return
        }
        if (p.direction == FuzzyDirection.Horizontal) {
            var y = 0
            var k = 0
            while (y < sh) {
                val h = min(step, sh - y)
                val dx = FuzzMath.offset(k, seed, FuzzMath.SALT_ROWS, p.amp) + FuzzMath.offset(k, seed, wobbleSalt, jitter)
                drawImage(
                    src, IntOffset(0, y), IntSize(sw, h), IntOffset(left + dx + sx, top + y), IntSize(sw, h),
                    colorFilter = filter, blendMode = blend, filterQuality = FilterQuality.None,
                )
                y += step; k++
            }
            return
        }
        // Both: rows into a scratch bitmap, then columns of the scratch shifted vertically (amp / 2).
        val scratchLeft = left - padH
        val scratchW = sw + 2 * padH
        val canvas = ensureScratch(scratchW, sh)
        val bmp = scratch!!
        canvas.drawRect(0f, 0f, scratchW.toFloat(), sh.toFloat(), clearPaint)
        slicePaint.colorFilter = filter
        var y = 0
        var k = 0
        while (y < sh) {
            val h = min(step, sh - y)
            val dx = FuzzMath.offset(k, seed, FuzzMath.SALT_ROWS, p.amp) + FuzzMath.offset(k, seed, wobbleSalt, jitter)
            canvas.drawImageRect(src, IntOffset(0, y), IntSize(sw, h), IntOffset(padH + dx + sx, y), IntSize(sw, h), slicePaint)
            y += step; k++
        }
        val reach = ceil(p.amp / 2f + abs(sx) + jitter / 2f).toInt() + 1
        var x = max(0, ((padH - reach) / step) * step)
        val to = min(scratchW, padH + sw + reach)
        while (x < to) {
            val w = min(step, to - x)
            val dy = FuzzMath.offset((scratchLeft + x).floorDiv(step), seed, FuzzMath.SALT_COLS, p.amp * 0.5f)
            drawImage(
                bmp, IntOffset(x, 0), IntSize(w, sh), IntOffset(scratchLeft + x, top + dy), IntSize(w, sh),
                blendMode = blend, filterQuality = FilterQuality.None,
            )
            x += step
        }
    }

    private fun ensureScratch(w: Int, h: Int): Canvas {
        val existing = scratch
        val canvas = scratchCanvas
        if (existing != null && canvas != null && existing.width == w && existing.height == h) return canvas
        val bmp = ImageBitmap(max(1, w), max(1, h))
        scratch = bmp
        return Canvas(bmp).also { scratchCanvas = it }
    }

    /** Isolates channel [c]: kept as is for light inks, inverted (255 - v) for dark inks; other channels 0. */
    private fun channel(c: Int, light: Boolean): ColorFilter {
        val slot = c + if (light) 0 else 3
        channelFilters[slot]?.let { return it }
        val m = FloatArray(20)
        m[c * 5 + c] = if (light) 1f else -1f
        if (!light) m[c * 5 + 4] = 255f
        m[18] = 1f
        return ColorFilter.colorMatrix(ColorMatrix(m)).also { channelFilters[slot] = it }
    }

    private fun invert(): ColorFilter = invertFilter ?: ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    ).also { invertFilter = it }
}

private fun DrawScope.drawTextLayout(layout: TextLayoutResult, brush: Brush?) {
    if (brush != null) drawText(layout, brush = brush) else drawText(layout)
}
