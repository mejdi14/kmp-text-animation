package io.github.mejdi14.textmotion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.github.mejdi14.textmotion.internal.font.VariationQuantizer
import io.github.mejdi14.textmotion.internal.font.rememberVariableFontFamilies
import io.github.mejdi14.textmotion.internal.graphemes
import io.github.mejdi14.textmotion.internal.pressure.PressureMath
import io.github.mejdi14.textmotion.internal.pressure.PressureState
import io.github.mejdi14.textmotion.internal.pressure.TextPressureGeometry
import io.github.mejdi14.textmotion.internal.pressure.drawPressureGlyph
import io.github.mejdi14.textmotion.internal.pressure.pressureKey
import io.github.mejdi14.textmotion.internal.rememberPointerTracker
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.resolve
import io.github.mejdi14.textmotion.internal.trackPointer
import io.github.mejdi14.textmotion.internal.trackVisibility
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlin.math.hypot
import kotlin.math.max

/** Ambient animation TextPressure plays while no pointer engages it. */
public enum class TextPressureIdle {
    /** Rest at the last pointer position. */
    None,

    /** A soft crest of pressure travels across the letters (3.2 s period). */
    Wave,

    /** The letters breathe from the centre outwards (2.8 s period, 42 % depth). */
    Pulse,
}

/**
 * Variable-font text whose letters swell — wider, heavier, slanted — as the pointer approaches
 * (React Bits "Text Pressure").
 *
 * Each grapheme is its own piece. Per frame the smoothed cursor's distance to each piece's centre
 * gives a linear pressure `1 - d / (titleWidth * radius)` that drives the `wdth` (5→205),
 * `wght` (100→1000) and italic (0→1) axes, optionally the opacity and a colour mix. Pieces fill
 * the width (`flex`, space-between) and the font size is `max(width / (chars / 2), minFontSize)`.
 * Typefaces are created per quantized axis position and cached, glyphs are measured once per
 * position, and all animation happens in the draw phase.
 *
 * Interaction: hover on Desktop/Web, touch-drag on Android/iOS (events are never consumed). The
 * initial pointer is the centre; when the pointer leaves, the effect rests where it was unless an
 * [idle] animation is set. Clicks/taps emit pressure ripples ([clickRipple]).
 *
 * @param text real, dynamic text (one piece per grapheme; reference default "Compressa").
 * @param style base style. Its colour is the text colour unless [textColor] is set; the font
 *   family is replaced by [font] and the size is computed from the width.
 * @param font variable font; Roboto Flex is bundled. Missing axes are emulated.
 * @param width animate the `wdth` axis.
 * @param weight animate the `wght` axis.
 * @param italic animate italic (`ital`, or `slnt` for fonts like Roboto Flex).
 * @param alpha letter opacity follows the pressure.
 * @param flex spread the letters across the full width (space-between); otherwise centred.
 * @param stroke draw an outline ([strokeColor], [strokeWidth]) behind each letter.
 * @param scale stretch the glyphs vertically to fill a bounded height.
 * @param radius influence radius as a fraction of the title width.
 * @param smoothing cursor lag (time constant `1000 * smoothing²` ms; 0 = instant).
 * @param idle ambient animation when the pointer is away.
 * @param idleSpeed speed multiplier of the idle animation.
 * @param clickRipple clicks/taps send a ring of pressure across the letters.
 * @param textColor letter colour; defaults to the style colour.
 * @param pressureColor when specified, letters blend towards it (Oklab) as pressure rises.
 * @param strokeColor outline colour when [stroke] is on.
 * @param strokeWidth outline width when [stroke] is on.
 * @param minFontSize lower bound of the computed font size.
 * @param uppercase render the text uppercase (the reference does).
 * @param pointerInteraction where the pointer comes from (real pointer, auto wander, both, none).
 * @param controller optional pause/resume; restart resets the cursor and ripples.
 */
@Composable
public fun TextPressure(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    font: VariableFont = VariableFont.RobotoFlex,
    width: Boolean = true,
    weight: Boolean = true,
    italic: Boolean = true,
    alpha: Boolean = false,
    flex: Boolean = true,
    stroke: Boolean = false,
    scale: Boolean = false,
    radius: Float = 0.5f,
    smoothing: Float = 0.5f,
    idle: TextPressureIdle = TextPressureIdle.None,
    idleSpeed: Float = 1f,
    clickRipple: Boolean = true,
    textColor: Color = Color.Unspecified,
    pressureColor: Color = Color.Unspecified,
    strokeColor: Color = Color(0xFFFF0000),
    strokeWidth: Dp = 3.dp,
    minFontSize: TextUnit = 24.sp,
    uppercase: Boolean = true,
    pointerInteraction: PointerInteraction = PointerInteraction.Pointer,
    controller: TextAnimationController? = null,
) {
    val pieces = remember(text, uppercase) { graphemes(if (uppercase) text.uppercase() else text) }
    val semanticsText = remember(text) { AnnotatedString(text) }
    val families = rememberVariableFontFamilies(font, fallback = style.fontFamily)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val quantizer = remember { VariationQuantizer() }
    val tracker = rememberPointerTracker()
    val visibility = rememberVisibilityState()
    val state = remember { PressureState() }
    val geometry = remember { TextPressureGeometry() }
    val color = textColor.takeOrElse { style.color.takeOrElse { Color.Black } }

    val currentSmoothing by rememberUpdatedState(smoothing)
    val currentIdleSpeed by rememberUpdatedState(idleSpeed)
    val currentInteraction by rememberUpdatedState(pointerInteraction)
    val currentRipple by rememberUpdatedState(clickRipple)

    LaunchedEffect(controller?.restartCount ?: 0) { if ((controller?.restartCount ?: 0) > 0) state.reset() }

    // Warm the glyph cache along the pressure path, a few typefaces per frame, so pointer sweeps
    // never create typefaces or measure glyphs mid-frame.
    LaunchedEffect(pieces, width, weight, italic, font) {
        snapshotFlow { geometry.cache }.collectLatest { cache ->
            if (cache == null) return@collectLatest
            val unique = pieces.distinct()
            var lastKey = Long.MIN_VALUE
            for (step in 0..PREWARM_STEPS) {
                val key = pressureKey(quantizer, font, step / PREWARM_STEPS.toFloat(), width, weight, italic)
                if (key == lastKey) continue
                lastKey = key
                for (g in unique) cache[g, key]
                withFrameNanos { }
            }
        }
    }

    val playing = controller?.isPlaying ?: true
    LaunchedEffect(playing, visibility.isVisible, idle) {
        if (!playing || !visibility.isVisible) return@LaunchedEffect
        var last = -1L
        var lastPress = tracker.pressCount
        while (true) {
            val moving = withFrameNanos { now ->
                val dt = if (last < 0L) 16f else (now - last) / 1_000_000f
                last = now
                val size = Size(geometry.width, geometry.height)
                if (size.width <= 0f) return@withFrameNanos true
                state.ensureCursor(size.width / 2f, geometry.titleHeight / 2f)
                val interaction = currentInteraction
                val target = interaction.resolve(tracker, state.autoClockMs, Size(size.width, geometry.titleHeight))
                if (target != Offset.Unspecified) { state.cursorX = target.x; state.cursorY = target.y }
                val inside = when (interaction) {
                    PointerInteraction.None -> false
                    PointerInteraction.Pointer -> tracker.isActive
                    else -> true
                }
                if (tracker.pressCount != lastPress) {
                    lastPress = tracker.pressCount
                    if (currentRipple && tracker.position != Offset.Unspecified) state.addRipple(tracker.position.x, tracker.position.y)
                }
                val present = if (idle == TextPressureIdle.None || inside) 1f else 0f
                val busy = state.step(dt, currentSmoothing, present, idle != TextPressureIdle.None, currentIdleSpeed)
                busy || interaction is PointerInteraction.Auto || interaction is PointerInteraction.PointerWithAutoFallback ||
                    (idle != TextPressureIdle.None && present == 0f)
            }
            if (!moving) {
                // Settled: sleep until the pointer moves, a press happens or the layout changes.
                snapshotFlow { Triple(tracker.position, tracker.isActive, tracker.pressCount) to geometry.version }.drop(1).first()
                last = -1L
            }
        }
    }

    Layout(
        modifier = modifier
            .semantics { this.text = semanticsText }
            .trackVisibility(visibility)
            .trackPointer(tracker, enabled = pointerInteraction != PointerInteraction.None || clickRipple)
            .drawBehind {
                @Suppress("UNUSED_VARIABLE") val tick = state.frame
                val cache = geometry.cache ?: return@drawBehind
                val n = pieces.size
                if (n == 0) return@drawBehind
                val w = geometry.width
                val titleH = geometry.titleHeight
                val scaleY = geometry.scaleY
                state.ensureCapacity(n, w)
                state.ensureCursor(w / 2f, titleH / 2f)
                val maxDist = max(w * radius, 1f)
                val spread = PressureMath.idleSpread(radius)
                val idling = idle != TextPressureIdle.None && state.isIdling
                val cy = titleH * scaleY / 2f
                val ripples = clickRipple && state.rippleCount > 0
                for (i in 0 until n) {
                    val cx = state.x[i] + state.w[i] / 2f
                    var p = PressureMath.cursorPressure(hypot(state.mouseX - cx, state.mouseY - cy), maxDist)
                    if (idling) {
                        val pos = if (w > 0f) cx / w else 0.5f
                        val rest = if (idle == TextPressureIdle.Wave) PressureMath.wave(pos, state.idleClock, spread)
                        else PressureMath.pulse(pos, state.idleClock)
                        p = PressureMath.engaged(rest, p, state.engage)
                    }
                    if (ripples) p = PressureMath.screen(p, state.ripplePressure(cx, cy, w))
                    state.pressure[i] = p
                    val key = pressureKey(quantizer, font, p, width, weight, italic)
                    state.keys[i] = key
                    state.newW[i] = cache[pieces[i], key].width
                }
                PressureMath.layoutRow(state.newW, n, w, flex, geometry.gapPx, state.x)
                state.newW.copyInto(state.w)
                val strokePx = strokeWidth.toPx()
                val strokeStyle = if (stroke) Stroke(width = strokePx) else null
                scale(1f, scaleY, pivot = Offset(w / 2f, 0f)) {
                    for (i in 0 until n) {
                        val glyph = cache[pieces[i], state.keys[i]]
                        val p = state.pressure[i]
                        val a = if (alpha) (kotlin.math.round(p * 100f) / 100f) else 1f
                        if (a <= 0f) continue
                        val fill = if (pressureColor.isSpecified) lerp(color, pressureColor, PressureMath.tint(p)) else color
                        translate(state.x[i], geometry.baselineY - glyph.firstBaseline) {
                            if (strokeStyle != null) drawPressureGlyph(glyph, strokeColor, a, strokeStyle)
                            drawPressureGlyph(glyph, fill, a, null)
                        }
                    }
                }
            },
    ) { _, constraints ->
        val minPx = if (minFontSize.isSpecified) minFontSize.toPx() else 24.sp.toPx()
        val n = pieces.size
        val containerW = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
        var fontPx = PressureMath.fontSize(containerW, n, minPx)
        if (!constraints.hasBoundedWidth) fontPx = minPx
        geometry.update(
            measurer = measurer, families = families, style = style, fontSizePx = fontPx,
            density = this, layoutDirection = layoutDirection,
        )
        val cache = geometry.cache!!
        val ls = style.letterSpacing
        val gap = when {
            flex || !ls.isSpecified -> 0f
            ls.isSp -> ls.toPx()
            else -> ls.value * fontPx
        }
        val restKey = pressureKey(quantizer, font, 0f, width, weight, italic)
        val layoutW = if (constraints.hasBoundedWidth) constraints.maxWidth else {
            var sum = 0f
            for (g in pieces) sum += cache[g, restKey].width
            (sum + gap * (n - 1).coerceAtLeast(0)).toInt()
        }.coerceIn(constraints.minWidth, constraints.maxWidth)
        val titleH = fontPx
        val layoutH = if (scale && constraints.hasBoundedHeight) constraints.maxHeight
        else kotlin.math.ceil(titleH).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        geometry.setSize(
            width = layoutW.toFloat(), height = layoutH.toFloat(), titleHeight = titleH,
            scaleY = if (scale && constraints.hasBoundedHeight && titleH > 0f) layoutH / titleH else 1f,
            gapPx = gap,
        )
        layout(layoutW, layoutH) {}
    }
}

private const val PREWARM_STEPS = 100
