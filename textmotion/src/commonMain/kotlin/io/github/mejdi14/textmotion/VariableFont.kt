package io.github.mejdi14.textmotion

import androidx.compose.runtime.Immutable
import io.github.mejdi14.textmotion.resources.Res
import io.github.mejdi14.textmotion.resources.roboto_flex
import org.jetbrains.compose.resources.FontResource

/**
 * Range of one variable-font axis (OpenType `fvar` min/default/max).
 */
@Immutable
public data class VariationAxisRange(val min: Float, val default: Float, val max: Float) {
    init {
        require(min <= default && default <= max) { "Invalid axis range: $min ≤ $default ≤ $max" }
    }

    /** Clamps [value] into the axis range. */
    public fun clamp(value: Float): Float = value.coerceIn(min, max)
}

/**
 * A variable font used by the variable-font effects (TextPressure, VariableProximity).
 *
 * TextMotion creates one platform typeface per *quantized* axis position, so animating
 * weight/width/slant never creates a font per frame. Axes the font lacks are emulated:
 * a missing `ital` axis maps to `slnt` when present, otherwise to a synthetic skew; on platforms
 * without variation support (Android < 8.0) weight, width and slant are synthesized
 * (`FontWeight`, horizontal scale, skew).
 *
 * @param name unique, stable identifier (used for typeface aliases and cache files).
 * @param resource the font file (`.ttf`/`.otf` variable font) as a Compose resource.
 * @param weight `wght` axis range, or null when the font has no weight axis.
 * @param width `wdth` axis range, or null.
 * @param italic `ital` axis range, or null.
 * @param slant `slnt` axis range (degrees, usually negative), or null.
 * @param opticalSize `opsz` axis range, or null.
 */
@Immutable
public class VariableFont(
    public val name: String,
    public val resource: FontResource,
    public val weight: VariationAxisRange? = VariationAxisRange(100f, 400f, 900f),
    public val width: VariationAxisRange? = null,
    public val italic: VariationAxisRange? = null,
    public val slant: VariationAxisRange? = null,
    public val opticalSize: VariationAxisRange? = null,
) {
    override fun equals(other: Any?): Boolean = other is VariableFont && other.name == name &&
        other.resource == resource && other.weight == weight && other.width == width &&
        other.italic == italic && other.slant == slant && other.opticalSize == opticalSize

    override fun hashCode(): Int = name.hashCode() * 31 + resource.hashCode()

    override fun toString(): String = "VariableFont($name)"

    public companion object {
        /**
         * Roboto Flex (Google Fonts, SIL Open Font License 1.1), bundled with TextMotion.
         * Axes: `wght` 100–1000, `wdth` 25–151, `slnt` −10–0, `opsz` 8–144 (no `ital` axis:
         * italic is rendered with `slnt`). License: `composeResources/files/licenses/RobotoFlex-OFL.txt`.
         */
        public val RobotoFlex: VariableFont by lazy {
            VariableFont(
                name = "textmotion-roboto-flex",
                resource = Res.font.roboto_flex,
                weight = VariationAxisRange(100f, 400f, 1000f),
                width = VariationAxisRange(25f, 100f, 151f),
                italic = null,
                slant = VariationAxisRange(-10f, 0f, 0f),
                opticalSize = VariationAxisRange(8f, 14f, 144f),
            )
        }
    }
}
