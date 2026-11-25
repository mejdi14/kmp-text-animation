package io.github.mejdi14.textmotion.internal.splittext

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mejdi14.textmotion.SplitTextEffect
import io.github.mejdi14.textmotion.SplitTextKeyframe
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.internal.SegmentedTextLayout
import io.github.mejdi14.textmotion.internal.graphemes

/** Whether the current run is an intro (hidden → revealed) or a replay (revealed → exit → reveal). */
@Stable
internal class SplitRunState {
    var replay: Boolean by mutableStateOf(false)
    var replays: Int by mutableIntStateOf(0)

    fun requestReplay() {
        replay = true
        replays++
    }

    /** Resets to an intro run (controller restart). */
    fun resetToIntro() {
        replay = false
    }
}

/** Caches the [SplitTextSpec] for the last seen font size / density (presets are `em`-relative). */
internal class SplitTextSpecCache(
    private val effect: SplitTextEffect,
    private val custom: Boolean,
    private val from: SplitTextKeyframe?,
    private val to: SplitTextKeyframe?,
) {
    private var em = -1f
    private var density = -1f
    private var spec: SplitTextSpec? = null

    fun get(emPx: Float, density: Float): SplitTextSpec {
        val cached = spec
        if (cached != null && em == emPx && this.density == density) return cached
        return buildSplitTextSpec(effect, emPx, density, custom, from, to).also {
            spec = it; em = emPx; this.density = density
        }
    }
}

/** Computed font size (`em`) in px of the measured text. */
internal fun SegmentedTextLayout.emPx(): Float {
    val input = layout.layoutInput
    val fs = input.style.fontSize
    return if (fs.isSp) with(input.density) { fs.toPx() } else 16f * input.density.density
}

/** Estimated number of animated units before the first layout (corrected from the real layout). */
internal fun estimateUnitCount(text: String, granularity: TextGranularity): Int = when (granularity) {
    TextGranularity.Character -> graphemes(text).count { it.isNotBlank() }
    TextGranularity.Word -> text.split(' ', '\n', '\t').count { it.isNotBlank() }
    TextGranularity.Line -> text.split('\n').count { it.isNotBlank() }
}
