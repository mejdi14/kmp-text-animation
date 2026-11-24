package com.mejdi14.kmp_text_animation.showcase.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.AnimationDemo
import com.mejdi14.kmp_text_animation.showcase.DemoCategory
import com.mejdi14.kmp_text_animation.showcase.controls.ChoiceControl
import com.mejdi14.kmp_text_animation.showcase.controls.CodeBlock
import com.mejdi14.kmp_text_animation.showcase.controls.ColorControl
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.DurationControl
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.TextEasing
import io.github.mejdi14.textmotion.TextMorph
import io.github.mejdi14.textmotion.TextMorphTransition
import io.github.mejdi14.textmotion.TextMorphVariant
import io.github.mejdi14.textmotion.rememberTextAnimationController
import kotlinx.coroutines.delay

private val TextMorphCode = """
var text by remember { mutableStateOf("Hello") }

TextMorph(
    text = text, // change it: shared letters glide, others fade
    style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
    transition = TextMorphTransition.Spring(stiffness = 280f, damping = 18f, mass = 0.3f),
    enter = TextMorphVariant.Hidden, // { opacity: 0 }
    exit = TextMorphVariant.Hidden,
)
""".trimIndent()

private val DefaultWords = "Hello, Help, Yellow, Hold, World, Motion, Lemon"

private enum class Kind { Spring, Tween }
private enum class Variant(val label: String) { Fade("Fade"), Rise("Fade + rise"), Pop("Fade + scale") }

@Composable
private fun cycledWord(words: List<String>, intervalMillis: Long): String {
    var index by remember(words) { mutableIntStateOf(0) }
    LaunchedEffect(words, intervalMillis) {
        while (words.size > 1) {
            delay(intervalMillis)
            index = (index + 1) % words.size
        }
    }
    return words.getOrElse(index) { "" }
}

val textMorphDemo = AnimationDemo(
    id = "text-morph",
    name = "Text Morph",
    description = "Characters fluidly morph from one string into the next.",
    category = DemoCategory.Transition,
    implemented = true,
    preview = {
        val word = cycledWord(listOf("Morph", "Motion", "Moment", "Mortal"), 1800L)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextMorph(word, style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
        }
    },
    screen = { TextMorphScreen() },
    code = TextMorphCode,
)

@Composable
private fun TextMorphScreen() {
    var wordsInput by remember { mutableStateOf(DefaultWords) }
    var cycle by remember { mutableStateOf(true) }
    var interval by remember { mutableIntStateOf(1800) }
    var typed by remember { mutableStateOf("Edit me to morph") }
    var kind by remember { mutableStateOf(Kind.Spring) }
    var stiffness by remember { mutableFloatStateOf(280f) }
    var damping by remember { mutableFloatStateOf(18f) }
    var mass by remember { mutableFloatStateOf(0.3f) }
    var duration by remember { mutableIntStateOf(300) }
    var variant by remember { mutableStateOf(Variant.Fade) }
    var ignoreCase by remember { mutableStateOf(true) }
    var fontSize by remember { mutableFloatStateOf(40f) }
    var color by remember { mutableStateOf(Color.White) }
    val controller = rememberTextAnimationController()

    val words = remember(wordsInput) { wordsInput.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    val cycled = cycledWord(words, interval.toLong())
    val text = if (cycle) cycled else typed
    val transition = when (kind) {
        Kind.Spring -> TextMorphTransition.Spring(stiffness, damping, mass)
        Kind.Tween -> TextMorphTransition.Tween(duration, TextEasing.MotionEaseInOut)
    }
    val (enter, exit) = when (variant) {
        Variant.Fade -> TextMorphVariant.Hidden to TextMorphVariant.Hidden
        Variant.Rise -> TextMorphVariant(alpha = 0f, offsetY = 12.dp) to TextMorphVariant(alpha = 0f, offsetY = (-12).dp)
        Variant.Pop -> TextMorphVariant(alpha = 0f, scale = 0.6f) to TextMorphVariant(alpha = 0f, scale = 0.6f)
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            TextMorph(
                text,
                style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, color = color),
                transition = transition,
                enter = enter,
                exit = exit,
                ignoreCase = ignoreCase,
                controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            SwitchControl("Cycle through words", cycle) { cycle = it }
            if (cycle) {
                TextControl("Words (comma separated)", wordsInput) { wordsInput = it }
                DurationControl("Interval", interval, { interval = it }, 600..5000)
            } else {
                TextControl("Text (morphs as you type)", typed) { typed = it }
            }
            ChoiceControl("Transition", Kind.entries, kind, { kind = it })
            if (kind == Kind.Spring) {
                SliderControl("Stiffness", stiffness, { stiffness = it }, 50f..1000f)
                SliderControl("Damping", damping, { damping = it }, 2f..60f)
                SliderControl("Mass", mass, { mass = it }, 0.05f..3f)
            } else {
                DurationControl("Duration", duration, { duration = it }, 100..2000)
            }
            ChoiceControl("Enter / exit", Variant.entries, variant, { variant = it }, labelOf = { it.label })
            SwitchControl("Ignore case (reference)", ignoreCase) { ignoreCase = it }
            SliderControl("Font size", fontSize, { fontSize = it }, 16f..72f)
            ColorControl("Colour", color, { color = it })
        }
        CodeBlock(TextMorphCode)
    }
}
