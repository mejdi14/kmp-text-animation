package io.github.mejdi14.textmotion.internal.font

import androidx.collection.MutableLongObjectMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import io.github.mejdi14.textmotion.VariableFont
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment

/**
 * Platform factory turning one variable font file into [FontFamily] instances pinned at given axis
 * positions. Implementations parse the file once and clone/instantiate per position:
 * - skiko (iOS/JVM/JS/Wasm): `SkTypeface.makeClone(variations)` wrapped in a Compose `Typeface`.
 * - Android: a cached copy of the file + `Font(file, variationSettings)` (applied on API 26+).
 */
internal expect class PlatformVariableTypeface {
    /** Whether axis positions are honoured natively (false on Android < 26). */
    val supportsVariations: Boolean

    /** A family whose single font is pinned at [settings]. [alias] is unique per position. */
    fun family(settings: FontVariation.Settings, alias: String): FontFamily
}

/** Loads the platform typeface from raw font [bytes] (null while bytes are loading). */
@Composable
internal expect fun rememberPlatformVariableTypeface(bytes: ByteArray?, id: String): PlatformVariableTypeface?

/**
 * Bounded cache of [FontFamily] instances per quantized axis key ([FontVariationKey]).
 *
 * Until the font bytes are loaded (or when loading fails) [isLoaded] is false and [family] returns
 * [fallback]; callers then render with [syntheticFor] emulation only.
 */
internal class VariableFontFamilies(
    val font: VariableFont,
    private val typeface: PlatformVariableTypeface?,
    private val fallback: FontFamily?,
    private val maxEntries: Int = 192,
) {
    private val families = MutableLongObjectMap<FontFamily>()

    val isLoaded: Boolean get() = typeface != null

    /** True when axis values reach the glyphs natively. */
    val supportsVariations: Boolean get() = typeface?.supportsVariations == true

    /** Family for [key]; created once per key, the cache is reset when it exceeds [maxEntries]. */
    fun family(key: Long): FontFamily? {
        val tf = typeface ?: return fallback
        if (!tf.supportsVariations) return families.getOrPut(BASE_KEY) { tf.family(FontVariation.Settings(), "${font.name}-base") }
        families[key]?.let { return it }
        if (families.size >= maxEntries) families.clear()
        val created = tf.family(font.settingsFor(key), "${font.name}-${key.toString(16)}")
        families[key] = created
        return created
    }

    /** Draw-time emulation for axes not covered natively. */
    fun synthetic(key: Long): SyntheticVariation = font.syntheticFor(key, supportsVariations)

    private companion object { const val BASE_KEY = -1L }
}

/**
 * Loads [font] asynchronously and remembers its [VariableFontFamilies]. Returns a not-yet-loaded
 * instance (fallback family + synthetic axes) until the bytes are available.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberVariableFontFamilies(font: VariableFont, fallback: FontFamily? = null): VariableFontFamilies {
    val environment = rememberResourceEnvironment()
    val bytes by produceState<ByteArray?>(null, font, environment) {
        value = try { getFontResourceBytes(environment, font.resource) } catch (e: Throwable) {
            println("TextMotion: could not load variable font ${font.name}: ${e.message}")
            null
        }
    }
    val typeface = rememberPlatformVariableTypeface(bytes, font.name)
    return remember(font, typeface, fallback) { VariableFontFamilies(font, typeface, fallback) }
}
