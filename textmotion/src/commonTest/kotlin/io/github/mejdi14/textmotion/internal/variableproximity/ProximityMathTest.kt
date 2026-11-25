package io.github.mejdi14.textmotion.internal.variableproximity

import androidx.compose.ui.text.style.TextAlign
import io.github.mejdi14.textmotion.ProximityFalloff
import io.github.mejdi14.textmotion.VariableFont
import io.github.mejdi14.textmotion.VariationAxisRange
import io.github.mejdi14.textmotion.internal.font.FontVariationKey
import io.github.mejdi14.textmotion.internal.font.VariationQuantizer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProximityMathTest {
    private fun near(expected: Float, actual: Float, eps: Float = 1e-4f) =
        assertTrue(abs(expected - actual) <= eps, "expected $expected, got $actual")

    @Test
    fun parsesCssSettings() {
        assertEquals(listOf("wght" to 400f, "opsz" to 9f), ProximityMath.parseSettings("'wght' 400, 'opsz' 9"))
        assertEquals(listOf("wdth" to 75.5f), ProximityMath.parseSettings("\"wdth\" 75.5"))
        assertEquals(emptyList(), ProximityMath.parseSettings("normal"))
        assertEquals(emptyList(), ProximityMath.parseSettings(""))
        // Malformed entries are skipped, repeated tags keep the last value.
        assertEquals(listOf("opsz" to 2f, "wght" to 700f), ProximityMath.parseSettings("'wght' 400, 'bad, 'opsz' 2, wght 9, 'wght' 700"))
    }

    @Test
    fun axesInterpolateFromToward() {
        val axes = ProximityAxes("'wght' 400, 'opsz' 9, 'wdth' 100", "'wght' 1000, 'opsz' 40, 'GRAD' 50")
        near(400f, axes.valueAt("wght", 0f))
        near(700f, axes.valueAt("wght", 0.5f))
        near(40f, axes.valueAt("opsz", 1f))
        near(100f, axes.valueAt("wdth", 1f)) // missing in `to` → stays
        assertTrue(axes.valueAt("GRAD", 1f).isNaN()) // only in `to` → ignored
        assertEquals(emptyList(), axes.unsupportedTags)
        assertEquals(listOf("XTRA"), ProximityAxes("'XTRA' 400", "'XTRA' 500").unsupportedTags)
    }

    @Test
    fun falloffCurves() {
        near(1f, ProximityMath.strength(0f, 50f, ProximityFalloff.Linear))
        near(0.5f, ProximityMath.strength(25f, 50f, ProximityFalloff.Linear))
        near(0.25f, ProximityMath.strength(25f, 50f, ProximityFalloff.Exponential))
        near(1f, ProximityMath.strength(0f, 50f, ProximityFalloff.Gaussian))
        val e2 = exp(-2f)
        near((exp(-0.5f) - e2) / (1f - e2), ProximityMath.strength(25f, 50f, ProximityFalloff.Gaussian))
        for (f in ProximityFalloff.entries) {
            assertEquals(0f, ProximityMath.strength(50f, 50f, f))
            assertEquals(0f, ProximityMath.strength(80f, 50f, f))
            assertEquals(0f, ProximityMath.strength(0f, 0f, f))
        }
    }

    @Test
    fun smoothingIsFrameRateIndependentAndSnaps() {
        assertEquals(1f, ProximityMath.ease(16f, 0f))
        // Two 8 ms steps equal one 16 ms step.
        var a = 0f
        repeat(2) { a = ProximityMath.approach(a, 1f, ProximityMath.ease(8f, 0.2f)) }
        val b = ProximityMath.approach(0f, 1f, ProximityMath.ease(16f, 0.2f))
        near(b, a, 1e-5f)
        // Frame time is clamped to 64 ms.
        near(ProximityMath.ease(64f, 0.2f), ProximityMath.ease(500f, 0.2f))
        assertEquals(1f, ProximityMath.approach(0.999f, 1f, 0.1f))
    }

    @Test
    fun idleWave() {
        near(0.7f, ProximityMath.wave(0f, 0f, 220f, 0f))
        near(0f, ProximityMath.wave(110f, 0f, 220f, 0f))
        // Travels left to right: after a quarter period the crest sits a quarter span further.
        near(0.7f, ProximityMath.wave(55f, 0f, 220f, ProximityMath.WAVE_PERIOD / 4f), 1e-3f)
        near(220f, ProximityMath.waveSpan(100f, 220f))
        near(850f, ProximityMath.waveSpan(1000f, 220f))
    }

    @Test
    fun keysFollowLevelAndSlant() {
        val font = VariableFont(
            name = "test", resource = io.github.mejdi14.textmotion.internal.font.dummyResource(),
            weight = VariationAxisRange(100f, 400f, 1000f), slant = VariationAxisRange(-10f, 0f, 0f),
            opticalSize = VariationAxisRange(8f, 14f, 144f),
        )
        val q = VariationQuantizer(weightStep = 10f, widthStep = 2f, italicStep = 0.05f, opticalSizeStep = 2f)
        val axes = ProximityAxes("'wght' 400, 'slnt' 0", "'wght' 1000, 'slnt' -10")
        val table = axes.keyTable(q, font, autoOpticalSize = 32f, buckets = 128)
        assertEquals(400f, FontVariationKey.weight(table[0]))
        assertEquals(1000f, FontVariationKey.weight(table[128]))
        near(1f, FontVariationKey.italic(table[128]))
        assertEquals(32f, FontVariationKey.opticalSize(table[0])) // automatic optical sizing
        near(0.5f, ProximityAxes.slantToItalic(-5f, font))
        assertEquals(0, levelBucket(-1f, 128))
        assertEquals(64, levelBucket(0.5f, 128))
        assertEquals(128, levelBucket(2f, 128))
    }

    @Test
    fun scaleAndAlignment() {
        near(1f, ProximityMath.scaleAt(1.5f, 0f))
        near(1.25f, ProximityMath.scaleAt(1.5f, 0.5f))
        near(0f, ProximityLines.alignOffset(TextAlign.Start, false, 100f, 60f))
        near(40f, ProximityLines.alignOffset(TextAlign.Start, true, 100f, 60f))
        near(20f, ProximityLines.alignOffset(TextAlign.Center, false, 100f, 60f))
        near(40f, ProximityLines.alignOffset(TextAlign.End, false, 100f, 60f))
        near(-10f, ProximityLines.alignOffset(TextAlign.Center, false, 100f, 120f)) // grows symmetrically
    }

    @Test
    fun splitsWordsSpacesAndShapedScripts() {
        val t = ProximityText("Hi  yo\nمرحبا")
        assertEquals(listOf("H", "i", " ", " ", "y", "o", "\n", "مرحبا"), t.pieces)
        assertTrue(t.isLetter(0) && !t.isLetter(2) && !t.isLetter(6) && t.isLetter(7))
        assertEquals(3, t.wordStart.size)
        assertTrue(t.wordRtl[2] && !t.wordRtl[0])
        assertEquals(false, t.baseRtl)
        assertEquals(true, ProximityText("مرحبا hi").baseRtl)
        assertEquals(null, ProximityText("123 !").baseRtl)
    }

    @Test
    fun wrapsAtWordsAndFreezesLines() {
        val t = ProximityText("aa bb cc")
        val lines = ProximityLines.build(t, { 10f }, maxWidth = 50f, rtl = false)
        // "aa bb" = 50 fits, "cc" wraps; the space at the break is dropped.
        assertEquals(2, lines.count)
        assertEquals(listOf(0, 1, 2, 3, 4), lines.order.slice(lines.lineStart[0] until lines.lineStart[1]))
        assertEquals(listOf(6, 7), lines.order.slice(lines.lineStart[1] until lines.lineStart[2]))
        near(50f, lines.restWidth[0])
        near(50f, lines.maxRestWidth)
        // Unbounded: one line; explicit newline forces a break; a long word overflows alone.
        assertEquals(1, ProximityLines.build(t, { 10f }, Float.POSITIVE_INFINITY, false).count)
        assertEquals(2, ProximityLines.build(ProximityText("a\nb"), { 10f }, Float.POSITIVE_INFINITY, false).count)
        assertEquals(2, ProximityLines.build(ProximityText("abcdefgh ij"), { 10f }, 30f, false).count)
    }

    @Test
    fun mirrorsRightToLeftLines() {
        val t = ProximityText("مرحبا ab")
        val lines = ProximityLines.build(t, { 10f }, Float.POSITIVE_INFINITY, rtl = true)
        // Visual order: "ab" (letters kept in reading order), space, Arabic word.
        assertEquals(listOf(2, 3, 1, 0), lines.order.toList())
    }
}
