package io.github.mejdi14.textmotion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.PointerTracker
import io.github.mejdi14.textmotion.internal.autoPointerPosition
import io.github.mejdi14.textmotion.internal.font.VariationQuantizer
import io.github.mejdi14.textmotion.internal.font.rememberVariableFontFamilies
import io.github.mejdi14.textmotion.internal.pressure.drawPressureGlyph
import io.github.mejdi14.textmotion.internal.rememberPointerTracker
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.trackPointer
import io.github.mejdi14.textmotion.internal.trackVisibility
import io.github.mejdi14.textmotion.internal.variableproximity.ProximityAxes
import io.github.mejdi14.textmotion.internal.variableproximity.ProximityGeometry
import io.github.mejdi14.textmotion.internal.variableproximity.ProximityMath
import io.github.mejdi14.textmotion.internal.variableproximity.ProximityState
import io.github.mejdi14.textmotion.internal.variableproximity.ProximityText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

/** How a letter's strength decays with its distance `d` to the pointer inside the radius `r`. */
public enum class ProximityFalloff {
    /** `1 - d/r`. */
    Linear,

    /** `(1 - d/r)²` — a tighter, sharper peak. */
    Exponential,

    /** `exp(-2(d/r)²)`, normalized to reach 0 at the radius — a soft bell. */
    Gaussian,
}

/**
 * The active area of one or more [VariableProximity] texts (the reference's `containerRef`).
 *
 * Attach it to a container with [Modifier.proximityArea]; the pointer is then tracked over the
 * whole container instead of only over the text, so letters near the text's edges react to a
 * pointer that is outside the text but within the radius. Outside the container the letters rest.
 */
@Stable
public class ProximityArea internal constructor() {
    internal val tracker: PointerTracker = PointerTracker()
    internal var coordinates: LayoutCoordinates? = null
}

/** Remembers a [ProximityArea] to share between a container and its [VariableProximity] texts. */
@Composable
public fun rememberProximityArea(): ProximityArea = remember { ProximityArea() }

/**
 * Makes this element the pointer-tracking area of [area] (hover on Desktop/Web, touch-drag on
 * Android/iOS). Events are never consumed, so the container keeps scrolling and clicking normally.
 */
public fun Modifier.proximityArea(area: ProximityArea): Modifier =
    this.trackPointer(area.tracker).onGloballyPositioned { area.coordinates = it }

/**
 * Text whose letters morph between two variable-font settings depending on how close the pointer
 * is (React Bits "Variable Proximity").
 *
 * Every letter (grapheme) keeps a level `0..1`: its target is the [falloff] of the distance from
 * the pointer to the letter's centre within [radius]; levels follow targets with exponential
 * [smoothing] (time constant `smoothing × 400` ms, frame-rate independent). A letter at level `s`
 * is drawn with every axis of [fromFontVariationSettings] interpolated toward
 * [toFontVariationSettings], optionally with a colour mix ([fromColor] → [toColor], Oklab), a
 * scale and a lift. Line breaks are computed once at the resting settings, so swelling letters
 * push their neighbours along the line but never reflow the paragraph.
 *
 * Typefaces are created per quantized axis position and cached; glyphs are measured once per
 * position and the animation runs in the draw phase. The loop sleeps when everything settled and
 * pauses while invisible or when [controller] is paused. Missing axes are emulated (synthetic bold,
 * width scale, skew), which is also the fallback on Android < 8.0 where variable fonts are not
 * honoured.
 *
 * @param text real, dynamic text (the reference's `label`). Words in cursive/complex scripts
 *   (Arabic, Indic…) react as whole words so their shaping is preserved.
 * @param style base style: colour, font size, line height and alignment are honoured; the font
 *   family is replaced by [font].
 * @param font variable font; Roboto Flex (the reference's font) is bundled.
 * @param fromFontVariationSettings CSS `font-variation-settings` at rest, e.g. `"'wght' 400, 'opsz' 9"`.
 *   Supported axes: `wght`, `wdth`, `opsz`, `ital`, `slnt`; other axes are ignored. Without `opsz`,
 *   automatic optical sizing (`opsz` = font size) applies.
 * @param toFontVariationSettings settings at full proximity; axes missing here keep their `from` value.
 * @param radius influence radius around each letter centre.
 * @param falloff distance → strength curve.
 * @param smoothing lag of the letters (0 = instant).
 * @param fromColor resting colour (defaults to the style colour).
 * @param toColor colour at full proximity (defaults to the style colour).
 * @param scale letter scale at full proximity (origin 50 % / 80 % of the letter box).
 * @param lift upward translation at full proximity, in em.
 * @param idleWave when no letter has been near the pointer for 500 ms, a soft wave (3.2 s period,
 *   70 % peak) travels across the letters.
 * @param area optional active area shared with a container ([Modifier.proximityArea]); by default
 *   the pointer is tracked over the text itself.
 * @param pointerInteraction where the pointer comes from (real pointer, auto wander, both, none).
 * @param controller optional pause/resume; restart resets every letter to rest.
 */
@Composable
public fun VariableProximity(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    font: VariableFont = VariableFont.RobotoFlex,
    fromFontVariationSettings: String = "'wght' 400, 'opsz' 9",
    toFontVariationSettings: String = "'wght' 1000, 'opsz' 40",
    radius: Dp = 50.dp,
    falloff: ProximityFalloff = ProximityFalloff.Linear,
    smoothing: Float = 0.2f,
    fromColor: Color = Color.Unspecified,
    toColor: Color = Color.Unspecified,
    scale: Float = 1f,
    lift: Float = 0f,
    idleWave: Boolean = false,
    area: ProximityArea? = null,
    pointerInteraction: PointerInteraction = PointerInteraction.Pointer,
    controller: TextAnimationController? = null,
) {
    val proximityText = remember(text) { ProximityText(text) }
    val semanticsText = remember(text) { AnnotatedString(text) }
    val axes = remember(fromFontVariationSettings, toFontVariationSettings) {
        ProximityAxes(fromFontVariationSettings, toFontVariationSettings)
    }
    val families = rememberVariableFontFamilies(font, fallback = style.fontFamily)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val quantizer = remember { VariationQuantizer(weightStep = 10f, widthStep = 2f, italicStep = 0.05f, opticalSizeStep = 2f) }
    val ownTracker = rememberPointerTracker()
    val visibility = rememberVisibilityState()
    val geometry = remember { ProximityGeometry() }
    val state = remember { ProximityState() }
    val ownCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val baseColor = style.color.takeOrElse { Color.Black }

    val currentRadius by rememberUpdatedState(radius)
    val currentFalloff by rememberUpdatedState(falloff)
    val currentSmoothing by rememberUpdatedState(smoothing)
    val currentIdleWave by rememberUpdatedState(idleWave)
    val currentInteraction by rememberUpdatedState(pointerInteraction)
    val currentScale by rememberUpdatedState(scale)
    val currentLift by rememberUpdatedState(lift)

    LaunchedEffect(controller?.restartCount ?: 0) { if ((controller?.restartCount ?: 0) > 0) state.reset() }

    // Warm the glyph cache along the from → to path (one typeface per frame) so pointer sweeps
    // never create typefaces or measure glyphs mid-frame.
    LaunchedEffect(proximityText) {
        snapshotFlow { geometry.cache }.collectLatest { cache ->
            if (cache == null) return@collectLatest
            val table = geometry.keys
            val letters = proximityText.pieces.filterIndexed { i, _ -> proximityText.isLetter(i) }.distinct()
            var lastKey = Long.MIN_VALUE
            for (key in table) {
                if (key == lastKey) continue
                lastKey = key
                for (g in letters) cache[g, key]
                withFrameNanos { }
            }
        }
    }

    val playing = controller?.isPlaying ?: true
    LaunchedEffect(playing, visibility.isVisible, area) {
        if (!playing || !visibility.isVisible) return@LaunchedEffect
        val tracker = area?.tracker ?: ownTracker
        var last = -1L
        while (true) {
            val busy = withFrameNanos { now ->
                val dt = if (last < 0L) 16f else (now - last) / 1_000_000f
                last = now
                if (geometry.text == null || geometry.cache == null) return@withFrameNanos false
                val interaction = currentInteraction
                val pointer = resolvePointer(interaction, tracker, area, ownCoordinates[0], state.clockMs, geometry)
                val radiusPx = currentRadius.value * geometry.density
                val minSpanPx = ProximityMath.WAVE_MIN_SPAN_DP * geometry.density
                val moving = state.step(
                    dtMs = dt, pointer = pointer, g = geometry, radiusPx = radiusPx, falloff = currentFalloff,
                    smoothing = currentSmoothing, idleWave = currentIdleWave, minWaveSpanPx = minSpanPx,
                    scale = currentScale, liftEm = currentLift,
                )
                moving || interaction is PointerInteraction.Auto || interaction is PointerInteraction.PointerWithAutoFallback
            }
            if (!busy) {
                // Settled: sleep until the pointer moves or the layout changes.
                snapshotFlow { Triple(tracker.position, tracker.isActive, geometry.version) }.drop(1).first()
                last = -1L
            }
        }
    }

    Layout(
        modifier = modifier
            .semantics { this.text = semanticsText }
            .trackVisibility(visibility)
            .trackPointer(ownTracker, enabled = area == null && pointerInteraction != PointerInteraction.None)
            .onGloballyPositioned { ownCoordinates[0] = it }
            .drawBehind {
                @Suppress("UNUSED_VARIABLE") val tick = state.frame
                val t = geometry.text ?: return@drawBehind
                val lines = geometry.lines ?: return@drawBehind
                val cache = geometry.cache ?: return@drawBehind
                state.ensureLayout(geometry, scale, lift)
                if (state.level.size != t.size) return@drawBehind
                val lh = geometry.lineHeight
                val colored = fromColor.isSpecified || toColor.isSpecified
                val restColor = fromColor.takeOrElse { baseColor }
                val nearColor = toColor.takeOrElse { baseColor }
                val transformed = scale != 1f || lift != 0f
                for (line in 0 until lines.count) {
                    val top = line * lh
                    for (k in lines.lineStart[line] until lines.lineStart[line + 1]) {
                        val i = lines.order[k]
                        if (!t.isLetter(i)) continue
                        val level = state.level[i]
                        val glyph = cache[t.pieces[i], state.keys[i]]
                        val color = if (colored) (if (level <= 0f) restColor else lerp(restColor, nearColor, level)) else baseColor
                        val x = state.x[i]
                        val dy = top + geometry.baselineY - glyph.firstBaseline
                        if (transformed && level > 0f) {
                            val s = ProximityMath.scaleAt(scale, level)
                            val pivot = Offset(x + state.w[i] / 2f, top + lh * ProximityMath.ORIGIN_Y)
                            withTransform({
                                translate(0f, -lift * level * geometry.fontPx)
                                scale(s, s, pivot)
                            }) { translate(x, dy) { drawPressureGlyph(glyph, color, 1f, null) } }
                        } else {
                            translate(x, dy) { drawPressureGlyph(glyph, color, 1f, null) }
                        }
                    }
                }
            },
    ) { _, constraints ->
        geometry.updateFont(
            measurer = measurer, families = families, font = font, axes = axes, quantizer = quantizer,
            style = style, density = this, layoutDirection = layoutDirection,
        )
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
        val restW = geometry.updateLines(proximityText, maxW, layoutDirection, style.textAlign)
        val w = ceil(restW).toInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        geometry.setBox(w.toFloat())
        val lineCount = geometry.lines?.count ?: 0
        val h = ceil(lineCount * geometry.lineHeight).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(w, h) {}
    }
}

/**
 * Effective pointer in the text's local coordinates, or [Offset.Unspecified] when the letters
 * should rest. A real pointer tracked by a [ProximityArea] is converted from the area's space.
 */
private fun resolvePointer(
    interaction: PointerInteraction,
    tracker: PointerTracker,
    area: ProximityArea?,
    own: LayoutCoordinates?,
    clockMs: Float,
    geometry: ProximityGeometry,
): Offset {
    val real = when (interaction) {
        PointerInteraction.None, is PointerInteraction.Auto -> false
        PointerInteraction.Pointer -> tracker.isActive
        is PointerInteraction.PointerWithAutoFallback ->
            tracker.isActive || (tracker.position != Offset.Unspecified && tracker.idleMillis < interaction.idleMillis)
    }
    if (real) {
        val p = tracker.position
        if (p == Offset.Unspecified) return Offset.Unspecified
        if (area == null) return p
        val ac = area.coordinates
        if (own == null || ac == null || !own.isAttached || !ac.isAttached) return Offset.Unspecified
        return own.localPositionOf(ac, p)
    }
    val size = Size(geometry.boxWidth, (geometry.lines?.count ?: 0) * geometry.lineHeight)
    return when (interaction) {
        is PointerInteraction.Auto -> autoPointerPosition(clockMs, interaction.periodMillis, size)
        is PointerInteraction.PointerWithAutoFallback -> autoPointerPosition(clockMs, interaction.periodMillis, size)
        else -> Offset.Unspecified
    }
}
