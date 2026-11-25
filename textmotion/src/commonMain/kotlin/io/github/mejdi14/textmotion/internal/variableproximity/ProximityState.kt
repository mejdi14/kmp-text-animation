package io.github.mejdi14.textmotion.internal.variableproximity

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import io.github.mejdi14.textmotion.ProximityFalloff
import io.github.mejdi14.textmotion.VariableFont
import io.github.mejdi14.textmotion.internal.font.VariableFontFamilies
import io.github.mejdi14.textmotion.internal.font.VariableGlyphCache
import io.github.mejdi14.textmotion.internal.font.VariationQuantizer
import kotlin.math.hypot

/** Number of level buckets between the `from` (0) and `to` (1) settings. */
internal const val LEVEL_BUCKETS: Int = 128

/**
 * Measure-phase products of one VariableProximity: glyph cache, level → axis-key table, frozen
 * lines and line metrics. Rebuilt only when text, style, font, settings, density or width change.
 */
@Stable
internal class ProximityGeometry {
    var cache: VariableGlyphCache? by mutableStateOf(null, neverEqualPolicy())
        private set
    /** Bumped whenever the frozen layout changes (wakes the settled loop, resets positions). */
    var version: Int by mutableIntStateOf(0)
        private set

    var keys: LongArray = LongArray(0); private set
    var text: ProximityText? = null; private set
    var lines: ProximityLines? = null; private set
    var rtl = false; private set
    var align: TextAlign = TextAlign.Start; private set
    var boxWidth = 0f; private set
    var lineHeight = 0f; private set
    var baselineY = 0f; private set
    var fontPx = 0f; private set
    /** Pixels per dp. */
    var density = 1f; private set
    /** Idle-wave anchors: resting letter centres relative to the text box. */
    var anchorX = FloatArray(0); private set
    var anchorY = FloatArray(0); private set
    var restTextWidth = 0f; private set

    private var lastMeasurer: TextMeasurer? = null
    private var lastFamilies: VariableFontFamilies? = null
    private var lastStyle: TextStyle? = null
    private var lastDensity = -1f
    private var lastFontScale = -1f
    private var lastDirection: LayoutDirection? = null
    private var lastAxes: ProximityAxes? = null
    private var lastFont: VariableFont? = null
    private var lastText: ProximityText? = null
    private var lastMaxWidth = -1f
    private var anchorsVersion = -1

    /** Updates the glyph cache and key table; returns the resting key. */
    fun updateFont(
        measurer: TextMeasurer,
        families: VariableFontFamilies,
        font: VariableFont,
        axes: ProximityAxes,
        quantizer: VariationQuantizer,
        style: TextStyle,
        density: Density,
        layoutDirection: LayoutDirection,
    ) {
        if (cache != null && measurer === lastMeasurer && families === lastFamilies && style == lastStyle &&
            density.density == lastDensity && density.fontScale == lastFontScale && layoutDirection == lastDirection &&
            axes === lastAxes && font == lastFont
        ) return
        lastMeasurer = measurer; lastFamilies = families; lastStyle = style; lastDensity = density.density
        lastFontScale = density.fontScale; lastDirection = layoutDirection; lastAxes = axes; lastFont = font
        val fontSize = if (style.fontSize.isSpecified) style.fontSize else DEFAULT_FONT_SIZE
        fontPx = with(density) { fontSize.toPx() }
        this.density = density.density
        val base = style.copy(fontSize = fontSize, lineHeight = TextUnit.Unspecified, letterSpacing = TextUnit.Unspecified)
        // CSS px ≈ dp; automatic optical sizing uses the font size in CSS px.
        keys = axes.keyTable(quantizer, font, autoOpticalSize = fontPx / density.density, buckets = LEVEL_BUCKETS)
        val c = VariableGlyphCache(measurer, families, base, density, layoutDirection, maxEntries = 8192)
        val ref = c["H", keys[0]]
        val naturalH = ref.layout.size.height.toFloat()
        val lh = style.lineHeight
        lineHeight = when {
            !lh.isSpecified -> naturalH
            lh.isSp -> with(density) { lh.toPx() }
            else -> lh.value * fontPx
        }
        baselineY = (lineHeight - naturalH) / 2f + ref.firstBaseline
        lastText = null
        cache = c
    }

    /** Freezes line breaks for [text] at [maxWidth]; returns the resting box width. */
    fun updateLines(text: ProximityText, maxWidth: Float, layoutDirection: LayoutDirection, align: TextAlign): Float {
        val c = cache ?: return 0f
        if (text === lastText && maxWidth == lastMaxWidth && this.align == align &&
            rtl == (text.baseRtl ?: (layoutDirection == LayoutDirection.Rtl))
        ) return lines?.maxRestWidth ?: 0f
        lastText = text; lastMaxWidth = maxWidth
        this.text = text
        this.align = align
        rtl = text.baseRtl ?: (layoutDirection == LayoutDirection.Rtl)
        val restKey = keys[0]
        val l = ProximityLines.build(text, { c[text.pieces[it], restKey].width }, maxWidth, rtl)
        lines = l
        version++
        return l.maxRestWidth
    }

    /** Sets the final box width and computes idle-wave anchors at rest. */
    fun setBox(width: Float) {
        val t = text ?: return
        val l = lines ?: return
        val c = cache ?: return
        if (anchorsVersion == version && width == boxWidth) return
        boxWidth = width
        anchorX = FloatArray(t.size); anchorY = FloatArray(t.size)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        for (line in 0 until l.count) {
            var x = ProximityLines.alignOffset(align, rtl, width, l.restWidth[line])
            for (k in l.lineStart[line] until l.lineStart[line + 1]) {
                val i = l.order[k]
                val w = c[t.pieces[i], keys[0]].width
                anchorX[i] = x + w / 2f
                anchorY[i] = line * lineHeight + lineHeight / 2f
                if (t.isLetter(i)) { if (x < minX) minX = x; if (x + w > maxX) maxX = x + w }
                x += w
            }
        }
        if (minX <= maxX) for (i in anchorX.indices) anchorX[i] -= minX
        restTextWidth = if (minX <= maxX) maxX - minX else 0f
        version++
        anchorsVersion = version
    }

    companion object {
        val DEFAULT_FONT_SIZE: TextUnit = TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)
    }
}

/**
 * Animation state of one VariableProximity. [step] runs once per frame outside composition; the
 * draw phase subscribes to [frame] only and reads the per-piece arrays.
 */
@Stable
internal class ProximityState {
    var frame: Int by mutableIntStateOf(0)
        private set

    var level = FloatArray(0); private set
    var target = FloatArray(0); private set
    /** Current left edge, width and glyph key of every piece. */
    var x = FloatArray(0); private set
    var w = FloatArray(0); private set
    var keys = LongArray(0); private set
    /** Current letter centres (including lift/scale), used for the next frame's distances. */
    var cx = FloatArray(0); private set
    var cy = FloatArray(0); private set

    var waveMix = 0f; private set
    var clockMs = 0f; private set
    private var lastNearMs = 0f
    private var laidOutVersion = -1

    /** Resets levels, wave and clock (controller restart). */
    fun reset() {
        level.fill(0f); target.fill(0f)
        waveMix = 0f; clockMs = 0f; lastNearMs = 0f
        laidOutVersion = -1
        frame++
    }

    private fun ensureCapacity(n: Int) {
        if (level.size == n) return
        level = FloatArray(n); target = FloatArray(n); x = FloatArray(n); w = FloatArray(n)
        keys = LongArray(n); cx = FloatArray(n); cy = FloatArray(n)
    }

    /** Lays the pieces out at their current levels when the frozen geometry changed. */
    fun ensureLayout(g: ProximityGeometry, scale: Float, liftEm: Float) {
        if (laidOutVersion == g.version && level.size == (g.text?.size ?: 0)) return
        layout(g, scale, liftEm)
    }

    /**
     * Advances one frame: targets from the [pointer] (local px, [Offset.Unspecified] when none),
     * idle wave, smoothing, then relayout. Returns whether another frame is needed.
     */
    fun step(
        dtMs: Float,
        pointer: Offset,
        g: ProximityGeometry,
        radiusPx: Float,
        falloff: ProximityFalloff,
        smoothing: Float,
        idleWave: Boolean,
        minWaveSpanPx: Float,
        scale: Float,
        liftEm: Float,
    ): Boolean {
        val t = g.text ?: return false
        ensureLayout(g, scale, liftEm)
        val n = t.size
        val dt = dtMs.coerceIn(0f, ProximityMath.MAX_FRAME_MS)
        clockMs += dt
        var anyNear = false
        for (i in 0 until n) {
            if (!t.isLetter(i) || pointer == Offset.Unspecified) { target[i] = 0f; continue }
            val d = hypot(pointer.x - cx[i], pointer.y - cy[i])
            if (d < radiusPx) anyNear = true
            target[i] = ProximityMath.strength(d, radiusPx, falloff)
        }
        var waveBusy = false
        if (idleWave) {
            if (anyNear) lastNearMs = clockMs
            val idle = !anyNear && clockMs - lastNearMs > ProximityMath.WAVE_IDLE_DELAY_MS
            val goal = if (idle) 1f else 0f
            val next = waveMix + (goal - waveMix) * ProximityMath.waveMixEase(dt)
            waveMix = if (kotlin.math.abs(goal - next) < ProximityMath.SNAP) goal else next
            if (waveMix > 0f) {
                val span = ProximityMath.waveSpan(g.restTextWidth, minWaveSpanPx)
                val time = clockMs / 1000f
                val ax = g.anchorX; val ay = g.anchorY
                if (ax.size == n) for (i in 0 until n) {
                    if (!t.isLetter(i)) continue
                    val wv = waveMix * ProximityMath.wave(ax[i], ay[i], span, time)
                    if (wv > target[i]) target[i] = wv
                }
            }
            waveBusy = waveMix > 0f || !anyNear
        } else waveMix = 0f
        val ease = ProximityMath.ease(dt, smoothing)
        var settling = false
        for (i in 0 until n) {
            val next = ProximityMath.approach(level[i], target[i], ease)
            level[i] = next
            if (next != target[i]) settling = true
        }
        layout(g, scale, liftEm)
        frame++
        return settling || waveBusy
    }

    private fun layout(g: ProximityGeometry, scale: Float, liftEm: Float) {
        val t = g.text ?: return
        val l = g.lines ?: return
        val c = g.cache ?: return
        ensureCapacity(t.size)
        val table = g.keys
        val lh = g.lineHeight
        for (line in 0 until l.count) {
            var lineW = 0f
            for (k in l.lineStart[line] until l.lineStart[line + 1]) {
                val i = l.order[k]
                val key = if (t.isLetter(i)) table[levelBucket(level[i], LEVEL_BUCKETS)] else table[0]
                keys[i] = key
                val width = c[t.pieces[i], key].width
                w[i] = width
                lineW += width
            }
            var px = ProximityLines.alignOffset(g.align, g.rtl, g.boxWidth, lineW)
            val top = line * lh
            for (k in l.lineStart[line] until l.lineStart[line + 1]) {
                val i = l.order[k]
                x[i] = px
                cx[i] = px + w[i] / 2f
                val lv = level[i]
                val s = ProximityMath.scaleAt(scale, lv)
                // Box centre after translateY(-lift·level em) and scale about (50%, 80%).
                cy[i] = top + lh * ProximityMath.ORIGIN_Y - lh * (ProximityMath.ORIGIN_Y - 0.5f) * s - liftEm * lv * g.fontPx
                px += w[i]
            }
        }
        laidOutVersion = g.version
    }
}
