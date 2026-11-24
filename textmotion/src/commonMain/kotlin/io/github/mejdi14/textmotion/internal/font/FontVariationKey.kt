package io.github.mejdi14.textmotion.internal.font

import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.mejdi14.textmotion.VariableFont
import kotlin.math.roundToInt

/**
 * Allocation-free encoding of a variable-font axis position as a [Long] cache key.
 *
 * Four 12-bit fields: `wght` (integer units), `wdth` (tenths), `ital` (hundredths, 0..1 semantic
 * italic amount) and `opsz` (integer units). [UNSET] marks "use the font's default".
 */
internal object FontVariationKey {
    const val UNSET: Int = 0xFFF
    private const val BITS = 12
    private const val MASK = 0xFFFL

    fun pack(wght: Int, wdthTenths: Int, italHundredths: Int, opsz: Int): Long =
        (field(wght)) or (field(wdthTenths) shl BITS) or (field(italHundredths) shl (2 * BITS)) or (field(opsz) shl (3 * BITS))

    private fun field(v: Int): Long = (if (v == UNSET) UNSET else v.coerceIn(0, UNSET - 1)).toLong() and MASK

    private fun raw(key: Long, index: Int): Int = ((key ushr (index * BITS)) and MASK).toInt()

    /** `wght` value, or NaN when unset. */
    fun weight(key: Long): Float = raw(key, 0).let { if (it == UNSET) Float.NaN else it.toFloat() }

    /** `wdth` value, or NaN when unset. */
    fun width(key: Long): Float = raw(key, 1).let { if (it == UNSET) Float.NaN else it / 10f }

    /** Semantic italic amount 0..1, or NaN when unset. */
    fun italic(key: Long): Float = raw(key, 2).let { if (it == UNSET) Float.NaN else it / 100f }

    /** `opsz` value, or NaN when unset. */
    fun opticalSize(key: Long): Float = raw(key, 3).let { if (it == UNSET) Float.NaN else it.toFloat() }
}

/**
 * Snaps continuous axis values to a coarse grid so that an animation only ever touches a bounded
 * set of typefaces (each distinct key = one platform typeface + one measure per glyph).
 *
 * Pass `Float.NaN` for an axis to leave it at the font default.
 */
internal class VariationQuantizer(
    val weightStep: Float = 20f,
    val widthStep: Float = 4f,
    val italicStep: Float = 0.1f,
    val opticalSizeStep: Float = 4f,
) {
    init {
        require(weightStep >= 1f && widthStep >= 0.1f && italicStep >= 0.01f && opticalSizeStep >= 1f) { "Quantization steps too small" }
    }

    fun key(font: VariableFont, weight: Float, width: Float, italic: Float, opticalSize: Float = Float.NaN): Long =
        FontVariationKey.pack(
            wght = if (weight.isNaN()) FontVariationKey.UNSET else snap(font.weight?.clamp(weight) ?: weight.coerceIn(1f, 1000f), weightStep).roundToInt(),
            wdthTenths = if (width.isNaN()) FontVariationKey.UNSET else (snap(font.width?.clamp(width) ?: width.coerceIn(1f, 400f), widthStep) * 10f).roundToInt(),
            italHundredths = if (italic.isNaN()) FontVariationKey.UNSET else (snap(italic.coerceIn(0f, 1f), italicStep) * 100f).roundToInt(),
            opsz = if (opticalSize.isNaN() || font.opticalSize == null) FontVariationKey.UNSET else snap(font.opticalSize.clamp(opticalSize), opticalSizeStep).roundToInt(),
        )

    private fun snap(v: Float, step: Float): Float = (v / step).roundToInt() * step
}

/**
 * Emulation applied at draw time for whatever the platform typeface could not express.
 *
 * @property fontWeight weight requested from the text engine (synthetic bold when ≥ 600 and the
 *   font has no variation support).
 * @property scaleX horizontal glyph scale emulating `wdth` (1 = none).
 * @property lean horizontal shear factor emulating italic (positive leans right; 0 = none).
 */
internal data class SyntheticVariation(val fontWeight: FontWeight, val scaleX: Float, val lean: Float) {
    companion object {
        val None: SyntheticVariation = SyntheticVariation(FontWeight.Normal, 1f, 0f)
        /** Shear used for a fully synthetic italic (≈ 12°). */
        const val ITALIC_LEAN: Float = 0.21f
    }
}

/**
 * Platform variation settings for [key]. The semantic italic amount maps to `ital` when the font
 * has it, otherwise to `slnt` (`italic * slant.min`, e.g. −10° for Roboto Flex).
 */
internal fun VariableFont.settingsFor(key: Long): FontVariation.Settings {
    val list = ArrayList<FontVariation.Setting>(4)
    val w = FontVariationKey.weight(key)
    if (!w.isNaN() && weight != null) list += FontVariation.weight(weight.clamp(w).roundToInt().coerceIn(1, 1000))
    val wd = FontVariationKey.width(key)
    if (!wd.isNaN() && width != null) list += FontVariation.width(width.clamp(wd))
    val it = FontVariationKey.italic(key)
    if (!it.isNaN()) {
        when {
            italic != null -> list += FontVariation.italic(italic.clamp(it).coerceIn(0f, 1f))
            slant != null -> list += FontVariation.slant(slant.clamp(it * slant.min).coerceIn(-90f, 90f))
        }
    }
    val o = FontVariationKey.opticalSize(key)
    if (!o.isNaN() && opticalSize != null) list += FontVariation.Setting("opsz", opticalSize.clamp(o))
    return FontVariation.Settings(*list.toTypedArray())
}

/**
 * Draw-time emulation for [key]. When [variationsSupported] the typeface already carries every
 * axis the font has, so only missing axes are emulated.
 */
internal fun VariableFont.syntheticFor(key: Long, variationsSupported: Boolean): SyntheticVariation {
    val w = FontVariationKey.weight(key)
    val wd = FontVariationKey.width(key)
    val it = FontVariationKey.italic(key)
    val weightNative = variationsSupported && weight != null
    val widthNative = variationsSupported && width != null
    val italicNative = variationsSupported && (italic != null || slant != null)
    val fontWeight = if (weightNative || w.isNaN()) FontWeight.Normal else nearestFontWeight(w)
    val scaleX = if (widthNative || wd.isNaN()) 1f else syntheticWidthScale(wd)
    val lean = if (italicNative || it.isNaN()) 0f else it * SyntheticVariation.ITALIC_LEAN
    return if (fontWeight == FontWeight.Normal && scaleX == 1f && lean == 0f) SyntheticVariation.None
    else SyntheticVariation(fontWeight, scaleX, lean)
}

/** Nearest of the nine standard weights (100..900). */
internal fun nearestFontWeight(wght: Float): FontWeight =
    FontWeight(((wght / 100f).roundToInt() * 100).coerceIn(100, 900))

/** CSS `font-stretch` semantics: `wdth` is a percentage of the normal width. Clamped to 0.25..2. */
internal fun syntheticWidthScale(wdth: Float): Float = (wdth / 100f).coerceIn(0.25f, 2f)
