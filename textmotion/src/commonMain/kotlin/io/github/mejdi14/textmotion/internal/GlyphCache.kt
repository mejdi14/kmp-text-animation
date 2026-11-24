package io.github.mejdi14.textmotion.internal

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import kotlin.random.Random

/**
 * Lazily measured single-glyph layouts sharing the style/density of [input]. Used by effects
 * that swap characters every frame (scramble/decrypt) so they never re-measure the paragraph:
 * random glyphs are drawn at the fixed slot positions of the final text.
 */
internal class GlyphCache(private val measurer: TextMeasurer, private val input: TextLayoutInput) {
    private val cache = HashMap<String, TextLayoutResult>()

    operator fun get(glyph: String): TextLayoutResult = cache.getOrPut(glyph) {
        measurer.measure(
            text = AnnotatedString(glyph),
            style = input.style,
            overflow = TextOverflow.Visible,
            softWrap = false,
            maxLines = 1,
            density = input.density,
            layoutDirection = input.layoutDirection,
            fontFamilyResolver = input.fontFamilyResolver,
        )
    }

    /** Measures every grapheme of [chars] up-front (call outside the draw loop). */
    fun prewarm(chars: String) { graphemes(chars).forEach { get(it) } }
}

/** Picks a random grapheme from [pool] (pre-split with [graphemes]). */
internal fun List<String>.randomGlyph(random: Random): String = if (isEmpty()) "" else this[random.nextInt(size)]
