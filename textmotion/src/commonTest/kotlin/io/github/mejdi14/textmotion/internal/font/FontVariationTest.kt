package io.github.mejdi14.textmotion.internal.font

import androidx.compose.ui.text.font.FontWeight
import io.github.mejdi14.textmotion.VariableFont
import io.github.mejdi14.textmotion.VariationAxisRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FontVariationTest {
    // Same axes as Roboto Flex, without touching the generated resource accessor.
    private val flex = VariableFont(
        name = "test-flex", resource = dummyResource(),
        weight = VariationAxisRange(100f, 400f, 1000f), width = VariationAxisRange(25f, 100f, 151f),
        slant = VariationAxisRange(-10f, 0f, 0f), opticalSize = VariationAxisRange(8f, 14f, 144f),
    )
    private val italOnly = VariableFont(name = "test-ital", resource = dummyResource(), italic = VariationAxisRange(0f, 0f, 1f))
    private val static = VariableFont(name = "test-static", resource = dummyResource(), weight = null)

    @Test fun packRoundTrips() {
        val k = FontVariationKey.pack(740, 1235, 37, 96)
        assertEquals(740f, FontVariationKey.weight(k))
        assertEquals(123.5f, FontVariationKey.width(k))
        assertEquals(0.37f, FontVariationKey.italic(k))
        assertEquals(96f, FontVariationKey.opticalSize(k))
    }

    @Test fun unsetFieldsAreNaN() {
        val k = FontVariationKey.pack(FontVariationKey.UNSET, FontVariationKey.UNSET, FontVariationKey.UNSET, FontVariationKey.UNSET)
        assertTrue(FontVariationKey.weight(k).isNaN())
        assertTrue(FontVariationKey.width(k).isNaN())
        assertTrue(FontVariationKey.italic(k).isNaN())
        assertTrue(FontVariationKey.opticalSize(k).isNaN())
    }

    @Test fun quantizerSnapsAndClampsToAxes() {
        val q = VariationQuantizer(weightStep = 20f, widthStep = 4f, italicStep = 0.1f)
        val k = q.key(flex, weight = 509f, width = 205f, italic = 0.44f)
        assertEquals(500f, FontVariationKey.weight(k))
        assertEquals(152f, FontVariationKey.width(k)) // 151 clamp → snapped to the 4-grid
        assertEquals(0.4f, FontVariationKey.italic(k), 1e-4f)
        val low = q.key(flex, weight = 0f, width = 5f, italic = 0f)
        assertEquals(100f, FontVariationKey.weight(low))
        assertEquals(24f, FontVariationKey.width(low))
    }

    @Test fun nearbyValuesShareAKey() {
        val q = VariationQuantizer()
        assertEquals(q.key(flex, 401f, 100f, 0.5f), q.key(flex, 405f, 101f, 0.52f))
        assertNotEquals(q.key(flex, 400f, 100f, 0.5f), q.key(flex, 440f, 100f, 0.5f))
    }

    @Test fun pressurePathTouchesBoundedKeySet() {
        val q = VariationQuantizer()
        val keys = HashSet<Long>()
        for (i in 0..1000) {
            val p = i / 1000f
            keys += q.key(flex, 100f + 900f * p, 5f + 200f * p, p)
        }
        assertTrue(keys.size in 20..120, "keys=${keys.size}")
    }

    @Test fun italicMapsToSlantWhenNoItalAxis() {
        val s = flex.settingsFor(FontVariationKey.pack(700, 1000, 100, FontVariationKey.UNSET)).settings
        assertEquals(listOf("wght", "wdth", "slnt"), s.map { it.axisName })
        assertEquals(-10f, s.last().toVariationValue(null))
        val i = italOnly.settingsFor(FontVariationKey.pack(400, FontVariationKey.UNSET, 50, FontVariationKey.UNSET)).settings
        assertEquals(listOf("wght", "ital"), i.map { it.axisName })
        assertEquals(0.5f, i.last().toVariationValue(null))
    }

    @Test fun syntheticOnlyForMissingAxes() {
        val k = FontVariationKey.pack(800, 500, 100, FontVariationKey.UNSET)
        assertEquals(SyntheticVariation.None, flex.syntheticFor(k, variationsSupported = true))
        val noVar = flex.syntheticFor(k, variationsSupported = false)
        assertEquals(FontWeight.W800, noVar.fontWeight)
        assertEquals(0.5f, noVar.scaleX)
        assertEquals(SyntheticVariation.ITALIC_LEAN, noVar.lean)
        val st = static.syntheticFor(k, variationsSupported = true)
        assertEquals(FontWeight.W800, st.fontWeight)
        assertEquals(0.5f, st.scaleX)
        assertEquals(SyntheticVariation.ITALIC_LEAN, st.lean)
    }

    @Test fun syntheticHelpers() {
        assertEquals(FontWeight.W100, nearestFontWeight(20f))
        assertEquals(FontWeight.W900, nearestFontWeight(1000f))
        assertEquals(FontWeight.W500, nearestFontWeight(460f))
        assertEquals(0.25f, syntheticWidthScale(5f))
        assertEquals(2f, syntheticWidthScale(205f) )
    }
}

@OptIn(org.jetbrains.compose.resources.InternalResourceApi::class)
internal fun dummyResource(): org.jetbrains.compose.resources.FontResource =
    org.jetbrains.compose.resources.FontResource("test-font", emptySet())
