package io.github.mejdi14.textmotion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.GlyphCache
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.drawSegment
import io.github.mejdi14.textmotion.internal.graphemeBoundaries
import io.github.mejdi14.textmotion.internal.graphemes
import io.github.mejdi14.textmotion.internal.isActive
import io.github.mejdi14.textmotion.internal.minFraction
import io.github.mejdi14.textmotion.internal.rememberTimeline
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.resolveProgress
import io.github.mejdi14.textmotion.internal.restartKey
import io.github.mejdi14.textmotion.internal.textscramble.ScrambleMath
import io.github.mejdi14.textmotion.internal.trackVisibility
import kotlin.random.Random

/**
 * Random glyphs shuffle in place and resolve left-to-right into the final text
 * (Motion Primitives "Text Scramble").
 *
 * Every [tickMillis] each unresolved grapheme is replaced by a fresh random glyph from
 * [characterSet]; grapheme `i` resolves once `tick / steps * count > i`, where
 * `steps = durationMillis / tickMillis`. Spaces never scramble. The component is always laid out
 * with the final text, so its size never jitters; random glyphs are drawn centred in the slot of
 * the glyph they replace. Changing [text] does not restart the run (as in the reference); use
 * [controller] or [AnimationTrigger.OnVisible] (`once = false`) to replay.
 *
 * @param text real, dynamic text (graphemes, emoji and RTL are handled per grapheme cluster).
 * @param style text style.
 * @param durationMillis scramble duration (reference: 0.8 s).
 * @param tickMillis time between random-glyph swaps (reference `speed`: 0.04 s).
 * @param characterSet pool of random glyphs (split into grapheme clusters); reference: letters and digits.
 * @param seed fixes the random sequence for reproducible output; random per composition when null.
 * @param trigger when the run starts (reference: on mount).
 * @param speed playback speed multiplier on top of [durationMillis]/[tickMillis].
 * @param controller optional play/pause/restart/seek control.
 * @param onFinished called when the scramble completes (reference `onScrambleComplete`).
 */
@Composable
public fun TextScramble(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    durationMillis: Int = 800,
    tickMillis: Int = 40,
    characterSet: String = TextCharsets.Alphanumeric,
    seed: Int? = null,
    trigger: AnimationTrigger = AnimationTrigger.OnAppear,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
    onFinished: (() -> Unit)? = null,
) {
    val visibility = rememberVisibilityState()
    val tick = tickMillis.toFloat().coerceAtLeast(1f)
    val steps = ScrambleMath.steps(durationMillis.toFloat(), tick)
    val total = ScrambleMath.totalMillis(durationMillis.toFloat(), tick)
    val timeline = rememberTimeline(
        controller,
        active = if (trigger == AnimationTrigger.Manual) (controller?.restartCount ?: 0) > 0 else trigger.isActive(visibility),
        speed = speed,
        totalDurationMillis = total,
        restartKey = trigger.restartKey(visibility),
        onFinished = onFinished,
    )
    val pool = remember(characterSet) { graphemes(characterSet).distinct() }
    val seedValue = remember(seed) { seed ?: Random.nextInt() }
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val annotated = remember(text) { AnnotatedString(text) }
    val holder = remember { Cache() }

    AnimatedTextLayout(
        text = annotated,
        style = style,
        modifier = modifier.trackVisibility(visibility, trigger.minFraction()),
        granularity = TextGranularity.Character,
    ) { l ->
        val cache = holder.cacheFor(l, measurer, pool)
        val clock = timeline.elapsedMillis
        val started = clock > 0f || controller?.seekProgress != null || timeline.finished
        val elapsed = controller?.seekProgress?.let { it * total } ?: clock
        val k = if (started) ScrambleMath.tickAt(elapsed, tick) else Int.MAX_VALUE
        if (ScrambleMath.isFinished(k, steps)) {
            drawText(l.layout)
            return@AnimatedTextLayout
        }
        val slotCount = cache.slotCount
        for (seg in l.segments) {
            val slot = cache.slotOf(seg.index)
            if (ScrambleMath.isRevealed(slot, k, steps, slotCount) || pool.isEmpty()) {
                drawSegment(l, seg)
            } else {
                val glyph = pool[ScrambleMath.glyphIndex(seedValue, k, slot, pool.size)]
                val g = cache.glyphs[glyph]
                translate(
                    seg.bounds.center.x - g.size.width / 2f,
                    seg.baseline - g.firstBaseline,
                ) { drawText(g) }
            }
        }
    }
}

/** Per-layout caches: slot (grapheme index incl. spaces) of every segment and the glyph cache. */
private class Cache {
    private var layout: SegmentedTextLayout? = null
    lateinit var glyphs: GlyphCache
    private var slots = IntArray(0)
    var slotCount = 0

    fun cacheFor(l: SegmentedTextLayout, measurer: androidx.compose.ui.text.TextMeasurer, pool: List<String>): Cache {
        if (layout !== l) {
            layout = l
            val text = l.text.text
            val bounds = graphemeBoundaries(text)
            slotCount = bounds.size - 1
            slots = IntArray(l.segments.size) { i ->
                val start = l.segments[i].start
                var slot = 0
                while (slot + 1 < slotCount && bounds[slot + 1] <= start) slot++
                slot
            }
            glyphs = GlyphCache(measurer, l.layout.layoutInput).also { g -> pool.forEach { g[it] } }
        }
        return this
    }

    fun slotOf(segmentIndex: Int): Int = slots[segmentIndex]
}
