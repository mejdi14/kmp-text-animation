package io.github.mejdi14.textmotion.internal.font

import androidx.collection.MutableLongObjectMap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** One measured glyph (grapheme) at one quantized axis position. */
internal class VariableGlyph(val layout: TextLayoutResult, val synthetic: SyntheticVariation) {
    /** Advance width including synthetic horizontal scale. */
    val width: Float = layout.size.width * synthetic.scaleX
    val firstBaseline: Float = layout.firstBaseline
}

/**
 * Per-(grapheme, axis key) layout cache for variable-font effects. Each entry is measured once;
 * subsequent frames only look it up (no allocation besides the first miss). The whole cache is
 * reset when it exceeds [maxEntries] (a coarse but allocation-free LRU substitute).
 *
 * Build a new instance whenever the base style, density or font changes.
 */
internal class VariableGlyphCache(
    private val measurer: TextMeasurer,
    private val families: VariableFontFamilies,
    baseStyle: TextStyle,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
    private val maxEntries: Int = 4096,
) {
    private val baseStyle = baseStyle.copy(fontStyle = FontStyle.Normal)
    private val byGlyph = HashMap<String, MutableLongObjectMap<VariableGlyph>>()
    private var count = 0

    /** Number of measured entries (diagnostics/tests). */
    val size: Int get() = count

    operator fun get(glyph: String, key: Long): VariableGlyph {
        val perGlyph = byGlyph[glyph] ?: MutableLongObjectMap<VariableGlyph>().also { byGlyph[glyph] = it }
        perGlyph[key]?.let { return it }
        if (count >= maxEntries) { byGlyph.values.forEach { it.clear() }; count = 0 }
        val synthetic = families.synthetic(key)
        val family = families.family(key)
        val style = baseStyle.copy(
            fontFamily = family ?: baseStyle.fontFamily,
            fontWeight = synthetic.fontWeight,
            fontSynthesis = if (families.supportsVariations) FontSynthesis.None else FontSynthesis.Weight,
        )
        val layout = measurer.measure(
            text = AnnotatedString(glyph),
            style = style,
            overflow = TextOverflow.Visible,
            softWrap = false,
            maxLines = 1,
            density = density,
            layoutDirection = layoutDirection,
        )
        val entry = VariableGlyph(layout, synthetic)
        perGlyph[key] = entry
        count++
        return entry
    }
}
