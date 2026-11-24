package io.github.mejdi14.textmotion

/** Unit a per-segment text animation operates on. */
public enum class TextGranularity {
    /** User-perceived characters (extended grapheme clusters: emoji sequences, combining marks…). */
    Character,

    /** Runs of non-whitespace characters. */
    Word,

    /** Visual lines after wrapping. */
    Line,
}

/**
 * How individual segments are rendered.
 *
 * Splitting shaped text into independently drawn pieces breaks contextual shaping in joining
 * scripts (Arabic, Syriac, N'Ko, Mongolian, Indic conjuncts…). TextMotion therefore supports two
 * strategies:
 */
public enum class SegmentRenderMode {
    /**
     * [Clip] when the text contains a script that needs shaping context, otherwise [Isolated].
     */
    Auto,

    /**
     * The whole paragraph is shaped once and each segment is drawn as a clipped window of it.
     * Shaping, kerning and ligatures are always preserved. Glyph ink that overhangs its advance
     * box (italic overhang, some diacritics) may be cut at segment boundaries.
     */
    Clip,

    /**
     * Each segment is measured on its own and positioned where it sits in the full layout.
     * No clipping artifacts, but cross-segment shaping (joining, kerning pairs) is lost.
     */
    Isolated,
}
