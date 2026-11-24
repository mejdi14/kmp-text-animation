package io.github.mejdi14.textmotion.internal.truefocus

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.mejdi14.textmotion.TrueFocusEasing
import io.github.mejdi14.textmotion.TrueFocusFrameStyle
import io.github.mejdi14.textmotion.internal.clamp01
import io.github.mejdi14.textmotion.internal.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Reference `sentence.split(separator)` with blank words dropped. */
internal fun splitFocusWords(sentence: String, separator: String): List<String> =
    (if (separator.isEmpty()) listOf(sentence) else sentence.split(separator)).filter { it.isNotBlank() }

/** Auto-advance period: `animationDuration + pauseBetweenAnimations`. */
internal fun focusIntervalMillis(durationMillis: Int, pauseMillis: Int): Float =
    (durationMillis.coerceAtLeast(0) + pauseMillis.coerceAtLeast(0)).coerceAtLeast(1).toFloat()

/**
 * Next focused word. Sequential: `(i+1) % n`. Random: uniform over the *other* words
 * (`next = floor(r*(n-1)); next >= current ? next+1 : next`), [rand] in `[0,1)`.
 */
internal fun nextFocusIndex(current: Int, count: Int, random: Boolean, rand: Float): Int {
    if (count <= 1) return 0
    if (!random) return (current + 1) % count
    val next = (rand * (count - 1)).toInt().coerceIn(0, count - 2)
    return if (next >= current) next + 1 else next
}

/** Timer that decides when the auto mode moves the focus. Times are timeline milliseconds. */
internal class FocusCycle(private val intervalMillis: Float, private val random: Boolean, seed: Int = 0) {
    private val rng = Random(seed)
    private var lastAdvance = 0f

    /** Restarts the countdown (call when the focus was moved by other means, e.g. hover). */
    fun reset(now: Float) { lastAdvance = now }

    /** Returns the new index when the interval elapsed, or -1. */
    fun tick(now: Float, current: Int, count: Int): Int {
        if (count < 2 || now - lastAdvance < intervalMillis) return -1
        lastAdvance = now
        return nextFocusIndex(current, count, random, rng.nextFloat())
    }
}

/**
 * Maps reference words to whitespace-separated layout segments: a word that itself contains
 * spaces covers several segments. Returns for each segment the owning word index.
 */
internal fun segmentOwners(words: List<String>): IntArray {
    val owners = ArrayList<Int>()
    words.forEachIndexed { wi, w ->
        repeat(countTokens(w)) { owners.add(wi) }
    }
    return owners.toIntArray()
}

private fun countTokens(s: String): Int {
    var n = 0
    var inToken = false
    for (c in s) {
        if (c.isWhitespace()) inToken = false else if (!inToken) { inToken = true; n++ }
    }
    return n
}

/** Index of the word under [p] (or -1). Rects are full line-height boxes, so vertical gaps are hit too. */
internal fun hitTestWord(rects: List<Rect>, p: Offset): Int {
    for (i in rects.indices) {
        val r = rects[i]
        if (p.x >= r.left && p.x < r.right && p.y >= r.top && p.y < r.bottom) return i
    }
    return -1
}

/** The word box scaled around its centre (reference `frame`). */
internal fun scaledFrame(word: Rect, focusScale: Float): Rect {
    if (focusScale == 1f) return word
    val w = word.width * focusScale
    val h = word.height * focusScale
    return Rect(word.left - (w - word.width) / 2f, word.top - (h - word.height) / 2f, word.left - (w - word.width) / 2f + w, word.top - (h - word.height) / 2f + h)
}

/** Top-left of the four corner boxes (TL, TR, BL, BR) of the bracket frame. */
internal fun bracketCorners(frame: Rect, cornerSize: Float, gap: Float, out: FloatArray = FloatArray(8)): FloatArray {
    val left = frame.left - gap
    val top = frame.top - gap
    val right = frame.right + gap - cornerSize
    val bottom = frame.bottom + gap - cornerSize
    out[0] = left; out[1] = top
    out[2] = right; out[3] = top
    out[4] = left; out[5] = bottom
    out[6] = right; out[7] = bottom
    return out
}

/** Rectangle of the non-bracket frame styles for a [frame]. */
internal fun styleRect(style: TrueFocusFrameStyle, frame: Rect, gap: Float, borderWidth: Float): Rect = when (style) {
    TrueFocusFrameStyle.Box, TrueFocusFrameStyle.Brackets ->
        Rect(frame.left - gap, frame.top - gap, frame.right + gap, frame.bottom + gap)
    TrueFocusFrameStyle.Underline -> {
        val y = frame.bottom + gap * 0.4f
        Rect(frame.left, y, frame.right, y + borderWidth)
    }
    TrueFocusFrameStyle.Highlight -> {
        val h = frame.height
        Rect(frame.left - h * 0.14f, frame.top + h * 0.04f, frame.right + h * 0.14f, frame.top + h * 0.04f + h * 0.92f)
    }
}

internal fun lerpRect(a: Rect, b: Rect, t: Float): Rect =
    Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

/** Progress curves of the frame animation (reference: tween `[0.65,0,0.35,1]` or spring bounce 0.38). */
internal object FrameMotion {
    private val smooth = androidx.compose.animation.core.CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    const val SPRING_BOUNCE = 0.38f

    /** Frame progress at time [tMillis] after the move started (may overshoot 1 for the spring). */
    fun progress(easing: TrueFocusEasing, tMillis: Float, durationMillis: Float): Float {
        if (durationMillis <= 0f) return 1f
        return when (easing) {
            TrueFocusEasing.Smooth -> smooth.transform(clamp01(tMillis / durationMillis))
            TrueFocusEasing.Spring -> springProgress(tMillis / 1000f, durationMillis / 1000f, SPRING_BOUNCE)
        }
    }

    /** Milliseconds for the spring to settle (equals [durationMillis] for the tween). */
    fun settleMillis(durationMillis: Float): Float =
        if (durationMillis <= 0f) 0f else springSettleSeconds(durationMillis / 1000f, SPRING_BOUNCE) * 1000f

    /** Whether the frame motion has fully settled after [tMillis]. */
    fun isDone(easing: TrueFocusEasing, tMillis: Float, durationMillis: Float): Boolean =
        tMillis >= if (easing == TrueFocusEasing.Smooth) durationMillis else springSettleSeconds(durationMillis / 1000f, SPRING_BOUNCE) * 1000f
}

/**
 * Motion `spring({ visualDuration, bounce })` step response (0 → 1): `omega = 2π/(d·1.2)`,
 * damping ratio `1 - bounce`, unit mass.
 */
internal fun springProgress(tSeconds: Float, visualDurationSeconds: Float, bounce: Float): Float {
    if (tSeconds <= 0f) return 0f
    val omega = (2.0 * PI / (visualDurationSeconds * 1.2)).toFloat()
    val zeta = (1f - bounce).coerceIn(0.05f, 1f)
    if (zeta >= 1f) return 1f - exp(-omega * tSeconds) * (1f + omega * tSeconds)
    val wd = omega * sqrt(1f - zeta * zeta)
    val decay = exp(-zeta * omega * tSeconds)
    return 1f - decay * (cos(wd * tSeconds) + zeta * omega / wd * sin(wd * tSeconds))
}

/** Time after which the spring envelope is below 0.1 %. */
internal fun springSettleSeconds(visualDurationSeconds: Float, bounce: Float): Float {
    val omega = (2.0 * PI / (visualDurationSeconds * 1.2)).toFloat()
    val zeta = (1f - bounce).coerceIn(0.05f, 1f)
    return -ln(0.001f) / (zeta * omega)
}

/**
 * Mutable focus state (timeline milliseconds). The focus level of each word eases from
 * [fromLevels] to its target (1 for the focused word, 0 otherwise) over the animation duration.
 */
internal class FocusState(wordCount: Int) {
    var target: Int = 0
    var startedAt: Float = 0f
    var fromLevels: FloatArray = FloatArray(wordCount).also { if (wordCount > 0) it[0] = 1f }
    /** Animated frame rectangle at the moment of the last retarget (null until laid out). */
    var fromFrame: Rect? = null
    /** Initial frame fade-in progress is measured from this time. */
    var mountedAt: Float = 0f
    /** Timeline time until which something is still animating. */
    var settleUntil: Float = 0f

    /** Moves the focus to [index], snapshotting the current levels so interrupted moves continue smoothly. */
    fun retarget(index: Int, now: Float, durationMillis: Float, levelEase: (Float) -> Float, currentFrame: Rect?) {
        if (index == target) return
        for (w in fromLevels.indices) fromLevels[w] = level(w, now, durationMillis, levelEase)
        fromFrame = currentFrame
        target = index
        startedAt = now
    }

    /** Focus level of [word] in 0..1 at [now]. */
    fun level(word: Int, now: Float, durationMillis: Float, levelEase: (Float) -> Float): Float {
        if (word !in fromLevels.indices) return 0f
        val p = if (durationMillis <= 0f) 1f else levelEase(clamp01((now - startedAt) / durationMillis))
        return lerp(fromLevels[word], if (word == target) 1f else 0f, p)
    }

    /** Resizes for a changed word count, resetting to the first word. */
    fun reset(wordCount: Int) {
        target = 0; startedAt = 0f; mountedAt = 0f; fromFrame = null
        fromLevels = FloatArray(wordCount).also { if (wordCount > 0) it[0] = 1f }
    }
}
