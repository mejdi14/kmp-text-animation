package io.github.mejdi14.textmotion

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mejdi14.textmotion.internal.AnimatedTextLayout
import io.github.mejdi14.textmotion.internal.fuzzy.FUZZ_SHADER
import io.github.mejdi14.textmotion.internal.fuzzy.FuzzEngine
import io.github.mejdi14.textmotion.internal.fuzzy.FuzzFrameParams
import io.github.mejdi14.textmotion.internal.fuzzy.FuzzMath
import io.github.mejdi14.textmotion.internal.fuzzy.FuzzRenderer
import io.github.mejdi14.textmotion.internal.fuzzy.FuzzSettings
import io.github.mejdi14.textmotion.internal.rememberPointerTracker
import io.github.mejdi14.textmotion.internal.rememberRuntimeShader
import io.github.mejdi14.textmotion.internal.rememberVisibilityState
import io.github.mejdi14.textmotion.internal.resolve
import io.github.mejdi14.textmotion.internal.trackPointer
import io.github.mejdi14.textmotion.internal.trackVisibility
import io.github.mejdi14.textmotion.internal.withDefaultColor
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Axis along which [FuzzyText] displaces its slices. */
public enum class FuzzyDirection {
    /** Horizontal bands shifted left/right (reference default). */
    Horizontal,

    /** Vertical columns shifted up/down. */
    Vertical,

    /** Bands shifted horizontally, then columns shifted vertically at half amplitude. */
    Both,
}

/** What raises [FuzzyText] from its base intensity. */
public enum class FuzzyTrigger {
    /** Hovering (or touching) the text raises the fuzz to `hoverIntensity` (reference default). */
    Hover,

    /** A click/tap fires a short burst (fast attack, exponential decay) with coarser slices. */
    Click,

    /** Always fuzz at `hoverIntensity`. */
    Always,
}

/** Default values of [FuzzyText] (React Bits reference defaults). */
public object FuzzyTextDefaults {
    public const val BaseIntensity: Float = 0.18f
    public const val HoverIntensity: Float = 0.5f
    /** Maximum slice shift at intensity 1 (reference `fuzzRange = 30` px). */
    public val FuzzRange: Dp = 30.dp
    /** Slice thickness (reference `sliceSize = 1` px). */
    public val SliceSize: Dp = 1.dp
    public const val Fps: Int = 60
    public const val GlitchIntervalMillis: Int = 2000
    public const val GlitchDurationMillis: Int = 200
    public const val IntroDurationMillis: Int = 1400
    /** Weight used when the style specifies none (reference `fontWeight = 900`). */
    public val FontWeight: FontWeight = androidx.compose.ui.text.font.FontWeight.Black
}

/**
 * Text that looks like an unstable analogue signal: the rendered text is cut into thin horizontal
 * (or vertical) slices and every frame each slice is shifted by a random whole-pixel amount
 * (React Bits "Fuzzy Text"). The fuzz rests at [baseIntensity] and rises to [hoverIntensity] while
 * the pointer is over the text.
 *
 * The amplitude is `intensity × fuzzRange` and offsets are uniform in `[-amp/2, amp/2]`,
 * re-randomised every frame at [fps]. Extra room is reserved around the text so shifted slices
 * are not clipped (`fuzzRange + 25 dp` left and right; above and below as well for
 * [FuzzyDirection.Vertical]/[FuzzyDirection.Both]).
 *
 * Rendering: a runtime shader displaces the crisp text per pixel (Android 13+, iOS, Desktop, Web);
 * Android < 13 uses the reference canvas technique (the text raster re-blitted slice by slice).
 * The effect pauses while not visible or while [controller] is paused (the last frame stays).
 *
 * @param text real, dynamic text (multi-line, RTL and emoji supported).
 * @param style font, size, colour, letter spacing, alignment. Without a font weight the reference
 *   weight 900 ([FuzzyTextDefaults.FontWeight]) is used. A `style.brush` fills the text.
 * @param baseIntensity resting fuzz intensity (0 = still text).
 * @param hoverIntensity intensity while hovered, with [FuzzyTrigger.Always], and the burst peak scale.
 * @param fuzzRange maximum slice shift at intensity 1.
 * @param direction displacement axis.
 * @param sliceSize slice thickness (at least 1 dp).
 * @param fps fuzz frames per second (new random offsets per frame).
 * @param enableHover whether hovering raises the intensity ([FuzzyTrigger.Hover] only).
 * @param trigger what raises the intensity: hover, click bursts or always.
 * @param transitionDurationMillis time to cover 95 % of an intensity change (0 = instant). The
 *   reference counts this in frames: `n` frames ≈ `n × 1000 / fps` ms.
 * @param clickEffect a click/tap flashes full intensity (1) for 150 ms (not with [FuzzyTrigger.Click]).
 * @param glitchMode every [glitchIntervalMillis] the intensity jumps to 1 for [glitchDurationMillis].
 * @param gradient at least two colours: horizontal linear gradient across the text (overrides the colour).
 * @param colorSplit chromatic aberration strength (0 = off); RGB channels are drawn apart by
 *   `colorSplit × (2 + intensity × fuzzRange × 0.18)` dp with per-slice wobble. Light inks combine
 *   additively, dark inks multiplicatively.
 * @param intro play an intro: intensity starts at 2 with coarse slices and settles over
 *   [introDurationMillis] while the text fades in. Replayed by `controller.restart()`.
 * @param pointerInteraction where the hover position comes from: real pointer (hover on
 *   desktop/web, touch on mobile), an automatic wandering pointer, or none. Events are never consumed.
 * @param speed time scale for every timing (fps, transitions, glitch, burst, intro).
 * @param controller optional pause/resume/restart control (`seekTo` freezes the current frame).
 */
@Composable
public fun FuzzyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextMotionStyle.current,
    baseIntensity: Float = FuzzyTextDefaults.BaseIntensity,
    hoverIntensity: Float = FuzzyTextDefaults.HoverIntensity,
    fuzzRange: Dp = FuzzyTextDefaults.FuzzRange,
    direction: FuzzyDirection = FuzzyDirection.Horizontal,
    sliceSize: Dp = FuzzyTextDefaults.SliceSize,
    fps: Int = FuzzyTextDefaults.Fps,
    enableHover: Boolean = true,
    trigger: FuzzyTrigger = FuzzyTrigger.Hover,
    transitionDurationMillis: Int = 0,
    clickEffect: Boolean = false,
    glitchMode: Boolean = false,
    glitchIntervalMillis: Int = FuzzyTextDefaults.GlitchIntervalMillis,
    glitchDurationMillis: Int = FuzzyTextDefaults.GlitchDurationMillis,
    gradient: List<Color>? = null,
    colorSplit: Float = 0f,
    intro: Boolean = false,
    introDurationMillis: Int = FuzzyTextDefaults.IntroDurationMillis,
    pointerInteraction: PointerInteraction = PointerInteraction.Pointer,
    speed: Float = 1f,
    controller: TextAnimationController? = null,
) {
    val annotated = remember(text) { AnnotatedString(text) }
    val resolvedStyle = remember(style) {
        val weighted = if (style.fontWeight == null) style.copy(fontWeight = FuzzyTextDefaults.FontWeight) else style
        weighted.withDefaultColor()
    }
    val stops = gradient?.takeIf { it.size >= 2 }
    val lightInk = remember(stops, resolvedStyle.color) { FuzzMath.isLightInk(stops ?: listOf(resolvedStyle.color)) }
    val pads = remember(fuzzRange, colorSplit, direction) { FuzzMath.padding(fuzzRange.value, colorSplit, direction) }
    val padH = pads[0].dp
    val padV = pads[1].dp

    val visibility = rememberVisibilityState()
    val tracker = rememberPointerTracker()
    val engine = remember { FuzzEngine() }
    val tick = remember { mutableIntStateOf(0) }
    val hoverBox = remember { HoverBox() }
    val settings = FuzzSettings(
        baseIntensity = baseIntensity.coerceAtLeast(0f),
        hoverIntensity = hoverIntensity.coerceAtLeast(0f),
        enableHover = enableHover,
        trigger = trigger,
        fps = fps.coerceAtLeast(1),
        transitionMillis = transitionDurationMillis.toFloat(),
        clickEffect = clickEffect,
        glitchMode = glitchMode,
        glitchIntervalMillis = glitchIntervalMillis.toFloat(),
        glitchDurationMillis = glitchDurationMillis.toFloat(),
        intro = intro,
        introDurationMillis = introDurationMillis.toFloat(),
    )
    val currentSettings by rememberUpdatedState(settings)
    val currentInteraction by rememberUpdatedState(pointerInteraction)
    val currentSpeed by rememberUpdatedState(speed)

    val restarts = controller?.restartCount ?: 0
    LaunchedEffect(restarts) {
        if (restarts > 0) { engine.reset(); tick.intValue++ }
    }
    val playing = controller?.isPlaying ?: true
    LaunchedEffect(playing, visibility.isVisible) {
        if (!playing || !visibility.isVisible) return@LaunchedEffect
        var last = -1L
        var lastPress = tracker.pressCount
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0L) 0f else (now - last) / 1_000_000f
                last = now
                engine.clock += dt * currentSpeed * (controller?.speed ?: 1f)
                val s = currentSettings
                if (tracker.pressCount != lastPress) {
                    lastPress = tracker.pressCount
                    engine.press(engine.clock, s)
                }
                val pos = currentInteraction.resolve(tracker, engine.clock, hoverBox.size)
                val hovering = pos != Offset.Unspecified && hoverBox.rect.contains(pos)
                if (engine.frame(engine.clock, hovering, s)) tick.intValue++
            }
        }
    }

    val shader = rememberRuntimeShader(FUZZ_SHADER)
    val renderer = remember(shader) { FuzzRenderer(shader) }
    val layer = rememberGraphicsLayer()
    val params = remember { FuzzFrameParams() }
    val needsPointer = pointerInteraction != PointerInteraction.None &&
        ((enableHover && trigger == FuzzyTrigger.Hover) || clickEffect || trigger == FuzzyTrigger.Click)

    AnimatedTextLayout(
        text = annotated,
        style = resolvedStyle,
        modifier = modifier
            .trackVisibility(visibility)
            .trackPointer(tracker, enabled = needsPointer)
            .padding(horizontal = padH, vertical = padV),
    ) { l ->
        @Suppress("UNUSED_VARIABLE") val frame = tick.intValue
        val layout = l.layout
        val padHPx = padH.roundToPx()
        val padVPx = padV.roundToPx()
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        var textLeft = w
        var textRight = 0f
        for (i in 0 until layout.lineCount) {
            textLeft = min(textLeft, layout.getLineLeft(i))
            textRight = max(textRight, layout.getLineRight(i))
        }
        if (textRight < textLeft) { textLeft = 0f; textRight = w }
        hoverBox.size = Size(w + 2 * padHPx, h + 2 * padVPx)
        hoverBox.rect = Rect(padHPx + textLeft, padVPx.toFloat(), padHPx + textRight, padVPx + h)

        val inset = FuzzMath.INSET.dp.roundToPx()
        val brush: Brush? = stops?.let { Brush.horizontalGradient(it, startX = textLeft - inset, endX = textRight + inset) }
        val lineHeight = if (layout.lineCount > 0) layout.getLineBottom(0) - layout.getLineTop(0) else h
        val margin = ceil(lineHeight * 0.25f).toInt()
        val fuzzPx = fuzzRange.toPx()
        val intensity = engine.intensity

        params.amp = intensity * fuzzPx
        params.step = FuzzMath.sliceStep(sliceSize.toPx(), 1.dp.toPx(), engine.coarse)
        params.seed = engine.seed
        params.direction = direction
        params.split = FuzzMath.splitDistance(colorSplit, intensity, fuzzRange.value, density)
        params.jitter = colorSplit.coerceAtLeast(0f) * params.amp * 0.2f
        params.lightInk = lightInk
        params.opacity = if (engine.introPending(currentSettings)) 0f else engine.opacity
        with(renderer) { draw(layer, layout, brush, padHPx, padVPx, margin, inset, params) }
    }
}

/** Hover hit-box (text bounds in the composable's coordinates) shared by draw and frame loop. */
private class HoverBox {
    var rect: Rect = Rect.Zero
    var size: Size = Size.Zero
}
