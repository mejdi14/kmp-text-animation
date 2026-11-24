package io.github.mejdi14.textmotion.internal.blurtext

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.TimeSource

/** How the current BlurText run started. */
internal enum class BlurRunKind {
    /** Hidden "from" state, then the intro (`trigger`). */
    Intro,

    /** Started fully visible (reference `trigger = hover|click`). */
    Rested,

    /** Replay requested while rested: out → in. */
    Replay,
}

@Stable
internal class BlurRunState(initial: BlurRunKind) {
    var kind: BlurRunKind by mutableStateOf(initial)
    var replays: Int by mutableIntStateOf(0)

    fun replay() {
        kind = BlurRunKind.Replay
        replays++
    }

    /** Timeline time (see [BlurTextTiming]) at which the run starts. */
    fun startOffset(timing: BlurTextTiming, delayMillis: Float): Float = when (kind) {
        BlurRunKind.Intro -> -delayMillis
        BlurRunKind.Replay -> timing.replayStart
        BlurRunKind.Rested -> if (timing.isInfinite || timing.iterations > 1) timing.restLoopStart else timing.end
    }
}

/**
 * Reference `focusOnHover`: when a word is hovered, every other word fades to `blur(dim) opacity .3`
 * over 450 ms with the CSS ease. Levels are evaluated lazily from wall-clock time; [tick] is bumped
 * every frame while a transition runs so the draw phase is invalidated.
 */
@Stable
internal class FocusDimState {
    var tick: Int by mutableIntStateOf(0)
    var focus: Int = -1
        private set
    private val origin = TimeSource.Monotonic.markNow()
    private var start = FloatArray(0)
    private var target = FloatArray(0)
    private var t0 = FloatArray(0)

    fun nowMillis(): Float = origin.elapsedNow().inWholeMicroseconds / 1000f

    private fun ensure(words: Int) {
        if (start.size >= words) return
        val n = words.coerceAtLeast(8)
        start = start.copyOf(n); target = target.copyOf(n); t0 = t0.copyOf(n)
    }

    /** Dim level of [word] at [now]: 0 = sharp, 1 = dimmed. */
    fun level(word: Int, now: Float): Float {
        if (word >= start.size) return 0f
        val p = ((now - t0[word]) / DURATION).coerceIn(0f, 1f)
        return start[word] + (target[word] - start[word]) * BlurCssEase.transform(p)
    }

    /** Whether a transition is still running at [now]. */
    fun isAnimating(now: Float): Boolean = t0.any { now - it < DURATION }

    fun setFocus(word: Int, words: Int, now: Float) {
        if (word == focus) return
        ensure(words)
        for (w in start.indices) {
            start[w] = level(w, now)
            target[w] = if (word != -1 && word != w) 1f else 0f
            t0[w] = now
        }
        focus = word
    }

    companion object {
        const val DURATION = 450f
    }
}
