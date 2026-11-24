package com.mejdi14.kmp_text_animation.showcase

import androidx.compose.runtime.Composable
import com.mejdi14.kmp_text_animation.showcase.demos.blurTextDemo
import com.mejdi14.kmp_text_animation.showcase.demos.placeholderDemo
import com.mejdi14.kmp_text_animation.showcase.demos.textPressureDemo
import com.mejdi14.kmp_text_animation.showcase.demos.textShimmerDemo

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
        placeholderDemo("text-morph", "Text Morph", "Characters fluidly morph from one string into the next.", DemoCategory.Transition),
        textPressureDemo,
        placeholderDemo("text-scramble", "Text Scramble", "Random glyphs shuffle and resolve into the final message.", DemoCategory.Transition),
        placeholderDemo("true-focus", "True Focus", "A focus frame hops between words while the rest stay blurred.", DemoCategory.Continuous),
        placeholderDemo("split-text", "Split Text", "Text is split into characters, words or lines that animate in with a stagger.", DemoCategory.Entrance),
        placeholderDemo("aurora-text", "Aurora Text", "Slow drifting aurora colors flow through the letters.", DemoCategory.Continuous),
        placeholderDemo("variable-proximity", "Variable Proximity", "Each letter reacts to how close the pointer is, shifting its weight.", DemoCategory.Interactive),
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
