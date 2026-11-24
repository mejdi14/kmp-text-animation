package io.github.mejdi14.textmotion.internal

/**
 * Returns the extended grapheme cluster boundaries of [text] using the platform's Unicode
 * segmentation (ICU on Android/JVM, NSString composed sequences on iOS, Intl.Segmenter on Web),
 * or null when unavailable. The array contains 0 and text.length.
 */
internal expect fun platformGraphemeBoundaries(text: String): IntArray?

/** Grapheme cluster boundaries, including 0 and text.length. */
internal fun graphemeBoundaries(text: String): IntArray {
    if (text.isEmpty()) return intArrayOf(0)
    return platformGraphemeBoundaries(text) ?: fallbackGraphemeBoundaries(text)
}

/** Splits [text] into grapheme clusters. */
internal fun graphemes(text: String): List<String> {
    val b = graphemeBoundaries(text)
    return List(b.size - 1) { text.substring(b[it], b[it + 1]) }
}

/**
 * Portable approximation of UAX #29 used when the platform has no segmenter. Handles surrogate
 * pairs, combining marks, ZWJ emoji sequences, variation selectors, skin-tone modifiers, tag
 * sequences, keycaps, regional-indicator flags and CRLF.
 */
internal fun fallbackGraphemeBoundaries(text: String): IntArray {
    val out = ArrayList<Int>(text.length + 1)
    out.add(0)
    var i = 0
    var prevCp = -1
    var riCount = 0
    while (i < text.length) {
        val cp = codePointAt(text, i)
        val len = if (cp > 0xFFFF) 2 else 1
        if (i > 0) {
            val extend = isExtend(cp) || prevCp == 0x200D ||
                (prevCp == '\r'.code && cp == '\n'.code) ||
                (isRegionalIndicator(cp) && isRegionalIndicator(prevCp) && riCount % 2 == 1)
            if (!extend) out.add(i)
        }
        riCount = if (isRegionalIndicator(cp)) riCount + 1 else 0
        prevCp = cp
        i += len
    }
    out.add(text.length)
    return out.toIntArray()
}

internal fun codePointAt(s: String, i: Int): Int {
    val c = s[i]
    if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
        return ((c.code - 0xD800) shl 10) + (s[i + 1].code - 0xDC00) + 0x10000
    }
    return c.code
}

private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF

private fun isExtend(cp: Int): Boolean {
    if (cp == 0x200D || cp == 0x200C) return true // ZWJ / ZWNJ
    if (cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF) return true // variation selectors
    if (cp in 0x1F3FB..0x1F3FF) return true // emoji skin tones
    if (cp in 0xE0020..0xE007F) return true // tags
    if (cp == 0x20E3) return true // keycap
    if (cp <= 0xFFFF) {
        return when (cp.toChar().category) {
            CharCategory.NON_SPACING_MARK, CharCategory.ENCLOSING_MARK, CharCategory.COMBINING_SPACING_MARK -> true
            else -> false
        }
    }
    // Supplementary combining marks commonly used (e.g. musical symbols, Brahmi, Chakma).
    return cp in 0x1D165..0x1D169 || cp in 0x1D16D..0x1D172 || cp in 0x1D17B..0x1D182 ||
        cp in 0x11000..0x11002 || cp in 0x11100..0x11102 || cp in 0x11127..0x11134
}

/**
 * True when [text] contains a script whose glyphs depend on neighbouring characters
 * (cursive joining or conjunct formation). Splitting such text per character would break shaping.
 */
internal fun needsShapingContext(text: CharSequence): Boolean {
    for (ch in text) {
        val c = ch.code
        if (c in 0x0590..0x08FF || // Hebrew (niqqud placement), Arabic, Syriac, Thaana, N'Ko, Samaritan, Mandaic, Arabic ext
            c in 0x0900..0x0DFF || // Indic scripts
            c in 0x0F00..0x0FFF || // Tibetan
            c in 0x1000..0x109F || // Myanmar
            c in 0x1780..0x18AF || // Khmer, Mongolian
            c in 0xA8E0..0xA8FF || c in 0xA9E0..0xA9FF ||
            c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF // presentation forms
        ) return true
    }
    return false
}

/** True when [text] contains strong right-to-left characters. */
internal fun containsRtl(text: CharSequence): Boolean {
    for (ch in text) {
        val c = ch.code
        if (c in 0x0590..0x08FF || c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF) return true
    }
    return false
}
