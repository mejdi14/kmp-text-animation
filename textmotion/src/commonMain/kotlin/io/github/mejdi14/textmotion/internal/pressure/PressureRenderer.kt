package io.github.mejdi14.textmotion.internal.pressure

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import io.github.mejdi14.textmotion.VariableFont
import io.github.mejdi14.textmotion.internal.font.FontVariationKey
import io.github.mejdi14.textmotion.internal.font.SyntheticVariation
import io.github.mejdi14.textmotion.internal.font.VariableFontFamilies
import io.github.mejdi14.textmotion.internal.font.VariableGlyph
import io.github.mejdi14.textmotion.internal.font.VariableGlyphCache
import io.github.mejdi14.textmotion.internal.font.VariationQuantizer

/** Quantized axis key for a pressure value, honouring which axes are enabled. */
internal fun pressureKey(
    quantizer: VariationQuantizer,
    font: VariableFont,
    pressure: Float,
    width: Boolean,
    weight: Boolean,
    italic: Boolean,
): Long = quantizer.key(
    font,
    weight = PressureMath.weight(pressure, weight),
    width = PressureMath.width(pressure, width),
    italic = PressureMath.italic(pressure, italic),
)

/**
 * Measured geometry shared between TextPressure's measure and draw phases. The glyph cache is
 * rebuilt only when the font size, style, font or density change (never per frame).
 */
@Stable
internal class TextPressureGeometry {
    var cache: VariableGlyphCache? by mutableStateOf(null, neverEqualPolicy())
        private set
    /** Bumped when the measured size changes (wakes the settled animation loop). */
    var version: Int by mutableIntStateOf(0)
        private set

    var width = 0f; private set
    var height = 0f; private set
    /** Height of the title box (line-height 1 = the font size), before [scaleY]. */
    var titleHeight = 0f; private set
    var scaleY = 1f; private set
    var gapPx = 0f; private set
    /** Baseline y inside the title box (half-leading centred, like CSS `line-height: 1`). */
    var baselineY = 0f; private set

    private var lastMeasurer: TextMeasurer? = null
    private var lastFamilies: VariableFontFamilies? = null
    private var lastStyle: TextStyle? = null
    private var lastFontPx = -1f
    private var lastDensity = -1f
    private var lastFontScale = -1f
    private var lastDirection: LayoutDirection? = null

    fun update(
        measurer: TextMeasurer,
        families: VariableFontFamilies,
        style: TextStyle,
        fontSizePx: Float,
        density: Density,
        layoutDirection: LayoutDirection,
    ) {
        if (cache != null && measurer === lastMeasurer && families === lastFamilies && style == lastStyle &&
            fontSizePx == lastFontPx && density.density == lastDensity && density.fontScale == lastFontScale &&
            layoutDirection == lastDirection
        ) return
        lastMeasurer = measurer; lastFamilies = families; lastStyle = style; lastFontPx = fontSizePx
        lastDensity = density.density; lastFontScale = density.fontScale; lastDirection = layoutDirection
        val fontSize = with(density) { fontSizePx.toSp() }
        val base = style.copy(fontSize = fontSize, lineHeight = TextUnit.Unspecified, letterSpacing = TextUnit.Unspecified)
        val c = VariableGlyphCache(measurer, families, base, density, layoutDirection)
        val ref = c["H", FontVariationKey.pack(400, 1000, 0, FontVariationKey.UNSET)]
        baselineY = (fontSizePx - ref.layout.size.height) / 2f + ref.firstBaseline
        cache = c
    }

    fun setSize(width: Float, height: Float, titleHeight: Float, scaleY: Float, gapPx: Float) {
        if (width != this.width || height != this.height || titleHeight != this.titleHeight) version++
        this.width = width; this.height = height; this.titleHeight = titleHeight
        this.scaleY = scaleY; this.gapPx = gapPx
    }
}

/** Draws one glyph at the origin, applying synthetic width/slant emulation when needed. */
internal fun DrawScope.drawPressureGlyph(glyph: VariableGlyph, color: Color, alpha: Float, stroke: Stroke?) {
    val s = glyph.synthetic
    if (s === SyntheticVariation.None) {
        drawText(glyph.layout, color = color, alpha = alpha, drawStyle = stroke ?: Fill)
        return
    }
    val canvas = drawContext.canvas
    val baseline = glyph.firstBaseline
    canvas.save()
    canvas.translate(0f, baseline)
    if (s.lean != 0f) canvas.skew(-s.lean, 0f)
    if (s.scaleX != 1f) canvas.scale(s.scaleX, 1f)
    canvas.translate(0f, -baseline)
    drawText(glyph.layout, color = color, alpha = alpha, drawStyle = stroke ?: Fill)
    canvas.restore()
}
