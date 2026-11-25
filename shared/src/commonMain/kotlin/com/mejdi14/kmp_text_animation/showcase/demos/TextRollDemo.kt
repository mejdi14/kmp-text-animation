package com.mejdi14.kmp_text_animation.showcase.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import com.mejdi14.kmp_text_animation.showcase.controls.IntSliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.Repeat
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.TextRoll
import io.github.mejdi14.textmotion.TextRollDefaults
import io.github.mejdi14.textmotion.TextRollVariants
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val TextRollCode = """
// Reference defaults: rotateX roll, 0.5 s easeIn, enter i*0.1 s, exit i*0.1+0.2 s
TextRoll(
    text = "motion-primitives",
    style = TextStyle(fontSize = 36.sp, color = Color.White),
    onFinished = { /* last letter landed */ },
)

// Custom variants, delays and easing (the docs' slide example)
TextRoll(
    text = "motion-primitives",
    variants = TextRollVariants.Slide,   // or TextRollVariants(enter = …, exit = …)
    clipToBounds = true,
    durationMillis = 300,
    enterDelayMillis = { i -> i * 50 },
    exitDelayMillis = { i -> i * 50 + 50 },
    easing = TextRollDefaults.SlideEasing,
)
""".trimIndent()

private val Light = Color(0xFFE4E4E7)

private enum class RollPreset(val label: String, val variants: TextRollVariants) {
    Roll("Roll", TextRollVariants.Roll),
    BlurRoll("Blur roll", TextRollVariants.BlurRoll),
    Slide("Slide", TextRollVariants.Slide),
}

val textRollDemo = AnimationDemo(
    id = "text-roll",
    name = "Text Roll",
    description = "Letters roll over in 3D one after another, like flaps on a split-flap board.",
    category = DemoCategory.Entrance,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextRoll(
                "Text Roll",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Light),
                repeat = Repeat(Repeat.INFINITE, delayBetweenMillis = 1200),
            )
        }
    },
    screen = { TextRollScreen() },
    code = TextRollCode,
)

@Composable
private fun TextRollScreen() {
    var text by remember { mutableStateOf("motion-primitives") }
    var preset by remember { mutableStateOf(RollPreset.Roll) }
    var granularity by remember { mutableStateOf(TextGranularity.Character) }
    var duration by remember { mutableIntStateOf(TextRollDefaults.DurationMillis) }
    var each by remember { mutableIntStateOf(100) }
    var exitOffset by remember { mutableIntStateOf(200) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(Light) }
    val controller = rememberTextAnimationController()
    val enterDelay = remember(each) { TextRollDefaults.staggered(each) }
    val exitDelay = remember(each, exitOffset) { TextRollDefaults.staggered(each, exitOffset) }
    val slide = preset == RollPreset.Slide
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            TextRoll(
                text,
                style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = color),
                variants = preset.variants,
                enterDelayMillis = enterDelay,
                exitDelayMillis = exitDelay,
                clipToBounds = slide,
                granularity = granularity,
                durationMillis = duration,
                easing = if (slide) TextRollDefaults.SlideEasing else TextRollDefaults.Easing,
                repeat = if (loop) Repeat(Repeat.INFINITE, delayBetweenMillis = 800) else Repeat.Once,
                speed = speed,
                controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            ChoiceControl("Variants", RollPreset.entries.toList(), preset, {
                preset = it
                if (it == RollPreset.Slide) { duration = 300; each = 50; exitOffset = 50 }
                else { duration = TextRollDefaults.DurationMillis; each = 100; exitOffset = 200 }
            }) { it.label }
            ChoiceControl("Unit", TextGranularity.entries.toList(), granularity, { granularity = it }) { it.name }
            DurationControl("Duration", duration, { duration = it }, 100..2000)
            IntSliderControl("Stagger (ms per unit)", each, { each = it }, 0..300)
            IntSliderControl("Exit delay offset (ms)", exitOffset, { exitOffset = it }, 0..600)
            SliderControl("Speed", speed, { speed = it }, 0.25f..3f)
            SwitchControl("Loop", loop) { loop = it }
            ColorControl("Color", color, { color = it })
        }
        CodeBlock(TextRollCode)
    }
}
