package io.github.mejdi14.textmotion.internal.morph

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.TextSegment

/**
 * One animated piece (grapheme, or word for complex scripts). Mutated only when the text layout
 * changes (never per frame); the frame is a pure function of these fields and the clock.
 */
internal class MorphItem(
    val key: String,
    /** Layout the piece is drawn from (the old layout for exiting pieces). */
    val layout: SegmentedTextLayout,
    val segment: TextSegment,
) {
    /** True once the key disappeared from the text: fading out at its last position (popLayout). */
    var exiting: Boolean = false

    /** Blend towards the exit variant instead of the initial one. */
    var useExitVariant: Boolean = false

    /** Present in the previous text too: its start offset gets the container-shift correction. */
    var persisted: Boolean = false

    /** Layout move: visual offset = (fromX, fromY) * remaining(t - moveStart). */
    var fromX: Float = 0f
    var fromY: Float = 0f
    var moveStart: Float = 0f

    /** Presence: 0 = initial/exit variant, 1 = resting ("animate") state. */
    var presenceFrom: Float = 1f
    var presenceVelocity: Float = 0f
    var presenceTarget: Float = 1f
    var presenceStart: Float = 0f
    var presenceSettle: Float = 0f

    fun moveRemaining(motion: MorphMotion, now: Float): Float =
        if (fromX == 0f && fromY == 0f) 0f else motion.layoutRemaining(now - moveStart)

    fun presence(motion: MorphMotion, now: Float): Float =
        motion.presence(presenceFrom, presenceVelocity, presenceTarget, now - presenceStart, presenceSettle)

    fun presenceVelocityAt(motion: MorphMotion, now: Float): Float =
        motion.presenceVelocity(presenceFrom, presenceVelocity, presenceTarget, now - presenceStart, presenceSettle)

    /** Moment (morph clock) at which both channels are at rest. */
    fun restAt(motion: MorphMotion): Float {
        val move = if (fromX == 0f && fromY == 0f) 0f else moveStart + motion.layoutSettleMillis
        return maxOf(move, presenceStart + presenceSettle)
    }

    /** Current visual top-left of the piece (in its own layout's coordinates). */
    fun visualAnchor(motion: MorphMotion, now: Float): Offset {
        val r = moveRemaining(motion, now)
        return Offset(segment.bounds.left + fromX * r, segment.bounds.top + fromY * r)
    }

    fun startPresence(motion: MorphMotion, from: Float, velocity: Float, target: Float, now: Float) {
        presenceFrom = from
        presenceVelocity = velocity
        presenceTarget = target
        presenceStart = now
        presenceSettle = motion.presenceSettleMillis(from, velocity, target)
    }
}

/**
 * Keeps the set of morphing pieces and the morph clock. [sync] diffs a new layout against the
 * live pieces (Motion `layoutId` + `AnimatePresence mode="popLayout"` semantics).
 */
@Stable
internal class MorphState {
    /** Morph clock in ms (speed-scaled). Read in the draw phase only. */
    var clockMillis: Float by mutableFloatStateOf(0f)

    /** Non-observable mirror of [clockMillis] for use during measurement. */
    var now: Float = 0f
        private set

    /** Bumped whenever a new morph starts; keys the frame loop. */
    var generation: Int by mutableIntStateOf(0)
        private set

    var items: List<MorphItem> = emptyList()
        private set

    var motion: MorphMotion? = null

    /** Clock value at which the latest morph started / will be fully at rest. */
    var morphStart: Float = 0f
        private set
    var morphEnd: Float = 0f
        private set

    /** Whether the latest sync started something that moves (drives onFinished). */
    var hasPendingMorph: Boolean = false

    /** Last controller restart count handled (MIN_VALUE = none seen yet). */
    var lastRestart: Int = Int.MIN_VALUE

    private var synced = false
    private var syncedLayout: SegmentedTextLayout? = null

    /** Position of the composable in the root at the previous placement, and pending correction. */
    private var lastRoot: Offset? = null
    private var shiftPending = false

    fun advance(deltaMillis: Float) {
        now += deltaMillis
        clockMillis = now
    }

    /**
     * Diffs [layout] against the live pieces at the current clock time.
     *
     * @param animate false for the very first layout when the initial animation is disabled
     *   (`AnimatePresence initial={false}`).
     */
    fun sync(layout: SegmentedTextLayout, ignoreCase: Boolean, animateInitial: Boolean) {
        if (syncedLayout === layout) return
        syncedLayout = layout
        val motion = motion ?: return
        val t = now
        val prefix = when (layout.granularity) {
            TextGranularity.Word -> "w:"
            TextGranularity.Line -> "l:"
            else -> "c:"
        }
        val keys = morphKeys(layout.segments.map { it.text }, ignoreCase, prefix)
        val first = !synced
        synced = true
        val live = HashMap<String, MorphItem>(items.size * 2)
        for (item in items) {
            // Exiting pieces that finished fading are gone (popLayout removed them).
            if (item.exiting && t >= item.presenceStart + item.presenceSettle) continue
            live[item.key] = item
        }
        val next = ArrayList<MorphItem>(keys.size + live.size)
        var moving = false
        layout.segments.forEachIndexed { i, seg ->
            val item = MorphItem(keys[i], layout, seg)
            val old = live.remove(keys[i])
            if (old != null) {
                val anchor = old.visualAnchor(motion, t)
                item.fromX = anchor.x - seg.bounds.left
                item.fromY = anchor.y - seg.bounds.top
                item.moveStart = t
                item.persisted = true
                item.useExitVariant = old.useExitVariant
                item.startPresence(motion, old.presence(motion, t), old.presenceVelocityAt(motion, t), 1f, t)
            } else if (first && !animateInitial) {
                item.startPresence(motion, 1f, 0f, 1f, t)
            } else {
                item.startPresence(motion, 0f, 0f, 1f, t)
            }
            if (item.restAt(motion) > t) moving = true
            next.add(item)
        }
        // Keys that disappeared: fade out where they are, out of the layout flow.
        for (old in live.values) {
            if (!old.exiting) {
                old.exiting = true
                old.useExitVariant = true
                old.startPresence(motion, old.presence(motion, t), old.presenceVelocityAt(motion, t), 0f, t)
            }
            moving = true
            next.add(old)
        }
        items = next
        morphStart = t
        morphEnd = next.maxOfOrNull { it.restAt(motion) } ?: t
        shiftPending = !first
        if (moving) hasPendingMorph = true
        generation++
    }

    /**
     * Called after placement with the composable's position in the root. When the container
     * itself moved because the text changed size (e.g. centred text), persistent pieces start
     * from their previous on-screen position, like Motion's page-space layout projection.
     */
    fun onPositioned(rootPosition: Offset) {
        val prev = lastRoot
        lastRoot = rootPosition
        if (!shiftPending || prev == null) return
        shiftPending = false
        val dx = rootPosition.x - prev.x
        val dy = rootPosition.y - prev.y
        if (dx == 0f && dy == 0f) return
        val motion = motion ?: return
        for (item in items) {
            if (item.persisted && !item.exiting && item.moveStart == morphStart) {
                item.fromX -= dx
                item.fromY -= dy
            }
        }
        morphEnd = maxOf(morphEnd, items.maxOfOrNull { it.restAt(motion) } ?: morphEnd)
    }

    /** Drawing happened: any later root movement is scrolling, not a morph. */
    fun onDrawn() { shiftPending = false }

    /** Replays the latest morph from its original start state (controller restart). */
    fun replay() {
        val motion = motion ?: return
        val t = now
        val dt = t - morphStart
        for (item in items) {
            item.moveStart += dt
            item.presenceStart += dt
        }
        morphStart = t
        morphEnd = items.maxOfOrNull { it.restAt(motion) } ?: t
        hasPendingMorph = morphEnd > t
        generation++
    }

    /** True when every piece rests at the current clock time. */
    fun isSettled(): Boolean = now >= morphEnd
}
