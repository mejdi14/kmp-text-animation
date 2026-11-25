package io.github.mejdi14.textmotion.internal.rotatingtext

import io.github.mejdi14.textmotion.RotatingTextStaggerFrom
import io.github.mejdi14.textmotion.RotatingTextTransition
import kotlin.math.max

/** Everything the [RotatingTextMachine] needs to know about the texts and the effect. Immutable. */
internal class RotatingTextPlan(
    /** Number of animated units of each text. */
    val unitCounts: IntArray,
    val spec: RotatingEffectSpec,
    val staggerFrom: RotatingTextStaggerFrom,
    val staggerMillis: Float,
    /** Spring of the container size animation (`layout` spring, or the highlight pill spring). */
    val sizeSpring: RotatingTextTransition.Spring,
) {
    val textCount: Int get() = unitCounts.size
    val sizeSettle: Float = sizeSpring.settleMillis()

    companion object {
        val Empty = RotatingTextPlan(
            IntArray(0), buildEffectSpec(
                io.github.mejdi14.textmotion.RotatingTextEffect.Slide,
                io.github.mejdi14.textmotion.RotatingTextDirection.Up, 0f, null,
            ),
            RotatingTextStaggerFrom.First, 0f, RotatingTextTransition.Spring(25f, 300f),
        )
    }
}

/**
 * State machine of a RotatingText: which text is shown, whether it is entering, resting or
 * exiting (`AnimatePresence mode="wait"`: the old text fully exits, then the new one enters), the
 * auto-rotation timer and the animated container size. Pure logic (no Compose state), advanced by
 * the composable's frame loop and read in the draw/layout phases.
 */
internal class RotatingTextMachine {
    enum class Phase { Rest, Exiting, Entering }

    var plan: RotatingTextPlan = RotatingTextPlan.Empty
        private set

    // ---- configuration pushed by the composable on every recomposition
    var intervalMillis: Float = 2000f
    var loop: Boolean = true
    /** Auto rotation timer runs (auto on, not paused by hover). */
    var autoEnabled: Boolean = true
    var onActiveChanged: ((Int) -> Unit)? = null
    /** Called when something outside the frame loop (next(), jumpTo()) changed the state. */
    var onWake: (() -> Unit)? = null

    // ---- state
    /** Index the user/auto rotation asked for (reference `currentTextIndex`). */
    var active: Int = 0
        private set
    /** Index currently drawn (differs from [active] while the old text exits). */
    var shown: Int = 0
        private set
    var phase: Phase = Phase.Rest
        private set
    var phaseTime: Float = 0f
        private set
    private var phaseSpan: Float = 0f
    var autoClock: Float = 0f
        private set
    private var autoStopped = false
    private var cycle = 0

    var delays: FloatArray = FloatArray(0)
        private set
    private var exitFrom = FloatArray(0)

    // ---- container size animation
    private var sizes = FloatArray(0)
    private var sizeInit = false
    private var fromW = 0f
    private var fromH = 0f
    private var toW = 0f
    private var toH = 0f
    private var sizeTime = Float.POSITIVE_INFINITY

    val isAnimating: Boolean get() = phase != Phase.Rest
    val isSizeAnimating: Boolean get() = sizeTime < plan.sizeSettle

    // ------------------------------------------------------------ configuration

    /**
     * Installs a new [plan] (texts or effect changed). The shown text snaps to its rest pose; with
     * [enter] it plays the enter animation instead (`animatePresenceInitial`).
     */
    fun setPlan(plan: RotatingTextPlan, enter: Boolean) {
        this.plan = plan
        if (plan.textCount == 0) { active = 0; shown = 0; phase = Phase.Rest; delays = FloatArray(0); return }
        active = active.coerceIn(0, plan.textCount - 1)
        shown = active
        autoClock = 0f
        autoStopped = false
        beginEnterPose(enter)
        if (sizes.size >= 2 * plan.textCount) snapSize()
    }

    private fun beginEnterPose(animate: Boolean) {
        cycle++
        delays = computeDelays(shown)
        exitFrom = FloatArray(plan.unitCounts[shown] * RCH)
        if (animate) {
            phase = Phase.Entering
            phaseTime = 0f
            phaseSpan = (delays.maxOrNull() ?: 0f) + plan.spec.enterSettle
        } else {
            phase = Phase.Rest
            phaseTime = 0f
        }
    }

    private fun computeDelays(index: Int): FloatArray =
        rotatingDelays(plan.unitCounts[index], plan.staggerFrom, plan.staggerMillis, cycle * 7919 + index)

    /** Natural container sizes (width, height pairs) of every text for the current constraints. */
    fun setSizes(newSizes: FloatArray) {
        sizes = newSizes
        if (plan.textCount == 0 || newSizes.size < 2 * plan.textCount) return
        if (!sizeInit || !isSizeAnimating) snapSize() else { toW = sizes[2 * shown]; toH = sizes[2 * shown + 1] }
    }

    private fun snapSize() {
        val s = shown.coerceIn(0, plan.textCount - 1)
        toW = sizes[2 * s]; toH = sizes[2 * s + 1]
        fromW = toW; fromH = toH
        sizeTime = Float.POSITIVE_INFINITY
        sizeInit = true
    }

    /** Container size in px at the current time. */
    fun currentWidth(): Float = sizeAt(fromW, toW)
    fun currentHeight(): Float = sizeAt(fromH, toH)

    private fun sizeAt(from: Float, to: Float): Float {
        if (!sizeInit) return 0f
        if (sizeTime >= plan.sizeSettle) return to
        val p = plan.sizeSpring.valueAt(sizeTime)
        return from + (to - from) * p
    }

    // ------------------------------------------------------------ requests

    /** Next index by the reference rule (`i < last ? i + 1 : loop ? 0 : i`) from [active]. */
    fun nextIndex(): Int = if (active < plan.textCount - 1) active + 1 else if (loop) 0 else active

    fun previousIndex(): Int = if (active > 0) active - 1 else if (loop) plan.textCount - 1 else active

    fun next() { requestIndex(nextIndex()) }
    fun previous() { requestIndex(previousIndex()) }
    fun jumpTo(index: Int) { requestIndex(index) }

    /** Plays the enter animation of text 0 from scratch (controller restart / reset). */
    fun restart() {
        if (plan.textCount == 0) return
        val changed = active != 0
        active = 0
        shown = 0
        autoClock = 0f
        autoStopped = false
        beginEnterPose(true)
        if (sizes.size >= 2 * plan.textCount) snapSize()
        if (changed) onActiveChanged?.invoke(0)
        onWake?.invoke()
    }

    /** Asks for text [index]. Returns false when it is already the active one. */
    fun requestIndex(index: Int): Boolean {
        if (plan.textCount == 0) return false
        val target = index.coerceIn(0, plan.textCount - 1)
        if (target == active) return false
        active = target
        autoClock = 0f
        autoStopped = false
        onActiveChanged?.invoke(target)
        if (phase != Phase.Exiting) beginExit()
        onWake?.invoke()
        return true
    }

    private fun beginExit() {
        val n = plan.unitCounts[shown]
        if (exitFrom.size != n * RCH) exitFrom = FloatArray(n * RCH)
        val tmp = FloatArray(RCH)
        for (u in 0 until n) {
            pose(u, tmp)
            tmp.copyInto(exitFrom, u * RCH)
        }
        phase = Phase.Exiting
        phaseTime = 0f
        phaseSpan = (delays.maxOrNull() ?: 0f) + plan.spec.exitSettle
    }

    private fun beginEnter() {
        shown = active
        cycle++
        delays = computeDelays(shown)
        phase = Phase.Entering
        phaseTime = 0f
        phaseSpan = (delays.maxOrNull() ?: 0f) + plan.spec.enterSettle
        if (sizeInit && sizes.size >= 2 * plan.textCount) {
            fromW = currentWidth(); fromH = currentHeight()
            toW = sizes[2 * shown]; toH = sizes[2 * shown + 1]
            sizeTime = 0f
        }
    }

    // ------------------------------------------------------------ time

    /** Milliseconds until the auto timer fires, or +Inf when it will not. */
    fun millisUntilAuto(): Float =
        if (!autoEnabled || autoStopped || plan.textCount < 2) Float.POSITIVE_INFINITY
        else max(0f, intervalMillis - autoClock)

    /** Adds idle time to the auto timer only (no phase is running). */
    fun addAutoTime(dt: Float) {
        if (autoEnabled && !autoStopped) autoClock += dt
    }

    /** Resets the auto timer (hover enter/leave re-arms a full interval). */
    fun resetAutoClock() { autoClock = 0f }

    /** Fires the auto rotation when its timer elapsed. */
    fun pollAuto() {
        if (!autoEnabled || autoStopped || plan.textCount < 2 || autoClock < intervalMillis) return
        val t = nextIndex()
        if (t == active) { autoStopped = true; return }
        requestIndex(t)
    }

    /** Advances the animation by [dt] ms of machine time. */
    fun advance(dt: Float) {
        if (plan.textCount == 0) return
        if (autoEnabled && !autoStopped) autoClock += dt
        if (sizeTime < plan.sizeSettle) sizeTime += dt
        when (phase) {
            Phase.Rest -> Unit
            Phase.Exiting -> {
                phaseTime += dt
                if (phaseTime >= phaseSpan) beginEnter()
            }
            Phase.Entering -> {
                phaseTime += dt
                if (phaseTime >= phaseSpan) { phase = Phase.Rest; phaseTime = 0f }
            }
        }
        pollAuto()
    }

    // ------------------------------------------------------------ pose

    /** Writes the channels of unit [unit] of the shown text into [out] (length [RCH]). */
    fun pose(unit: Int, out: FloatArray) {
        val spec = plan.spec
        val delay = if (unit < delays.size) delays[unit] else 0f
        when (phase) {
            Phase.Rest -> spec.rest.copyInto(out)
            Phase.Entering -> {
                val p = spec.enter.valueAt(phaseTime - delay)
                for (c in 0 until RCH) out[c] = spec.from[c] + (spec.rest[c] - spec.from[c]) * p
            }
            Phase.Exiting -> {
                val p = spec.exitTransition.valueAt(phaseTime - delay)
                val base = unit * RCH
                for (c in 0 until RCH) {
                    val from = if (base + c < exitFrom.size) exitFrom[base + c] else spec.rest[c]
                    out[c] = from + (spec.exit[c] - from) * p
                }
            }
        }
    }
}
