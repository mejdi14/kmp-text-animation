package com.mejdi14.kmp_text_animation.showcase

import androidx.compose.runtime.Composable
import com.mejdi14.kmp_text_animation.showcase.demos.auroraTextDemo
import com.mejdi14.kmp_text_animation.showcase.demos.blurTextDemo
import com.mejdi14.kmp_text_animation.showcase.demos.placeholderDemo
import com.mejdi14.kmp_text_animation.showcase.demos.splitTextDemo
import com.mejdi14.kmp_text_animation.showcase.demos.textMorphDemo
import com.mejdi14.kmp_text_animation.showcase.demos.textPressureDemo
import com.mejdi14.kmp_text_animation.showcase.demos.textScrambleDemo
import com.mejdi14.kmp_text_animation.showcase.demos.textShimmerDemo
import com.mejdi14.kmp_text_animation.showcase.demos.trueFocusDemo
import com.mejdi14.kmp_text_animation.showcase.demos.variableProximityDemo

enum class DemoCategory { Entrance, Continuous, Transition, Interactive, Scroll }

data class AnimationDemo(
    val id: String,
    val name: String,
    val description: String,
    val category: DemoCategory,
    val implemented: Boolean,
    val preview: @Composable () -> Unit,
    val screen: @Composable () -> Unit,
    val code: String,
)

object DemoRegistry {
    // Each feature replaces ONLY its own line with `xxxDemo`, defined in `showcase/demos/<Name>Demo.kt`.
    val demos: List<AnimationDemo> = listOf(
        blurTextDemo,
        textShimmerDemo,
        textMorphDemo,
        textPressureDemo,
        textScrambleDemo,
        trueFocusDemo,
        splitTextDemo,
        auroraTextDemo,
        variableProximityDemo,
        placeholderDemo("rotating-text", "Rotating Text", "A list of phrases rotates in place with a sliding transition.", DemoCategory.Transition),
        placeholderDemo("fuzzy-text", "Fuzzy Text", "Text with a fuzzy, static-like edge that intensifies on hover.", DemoCategory.Interactive),
        placeholderDemo("scroll-reveal", "Scroll Reveal", "Words unblur and fade in progressively as you scroll.", DemoCategory.Scroll),
        placeholderDemo("text-roll", "Text Roll", "Letters roll vertically like a slot machine, one after another.", DemoCategory.Entrance),
        placeholderDemo("gradient-text", "Gradient Text", "Animated multi-color gradient painted across the glyphs.", DemoCategory.Continuous),
        placeholderDemo("text-loop", "Text Loop", "Cycles through a set of strings with smooth enter and exit motion.", DemoCategory.Transition),
        placeholderDemo("text-shimmer-wave", "Text Shimmer Wave", "A wave of shimmer ripples through the letters with a vertical lift.", DemoCategory.Continuous),
        placeholderDemo("scroll-float", "Scroll Float", "Letters float up into place as the text scrolls into view.", DemoCategory.Scroll),
        placeholderDemo("glitch-text", "Glitch Text", "Cyberpunk-style glitch with color-split slices and jitter.", DemoCategory.Continuous),
        placeholderDemo("decrypted-text", "Decrypted Text", "Encrypted-looking characters decode into readable text.", DemoCategory.Transition),
        placeholderDemo("text-effect", "Text Effect", "A flexible preset-based effect for blur, slide and scale reveals.", DemoCategory.Entrance),
    )

    fun byId(id: String): AnimationDemo? = demos.firstOrNull { it.id == id }
}
