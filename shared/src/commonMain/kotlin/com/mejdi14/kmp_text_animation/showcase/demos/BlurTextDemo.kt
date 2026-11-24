package com.mejdi14.kmp_text_animation.showcase.demos

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.AnimationDemo
import com.mejdi14.kmp_text_animation.showcase.DemoCategory
import com.mejdi14.kmp_text_animation.showcase.ShowcaseColors
import com.mejdi14.kmp_text_animation.showcase.controls.ChoiceControl
import com.mejdi14.kmp_text_animation.showcase.controls.ColorControl
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.DurationControl
import com.mejdi14.kmp_text_animation.showcase.controls.IntSliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.BlurText
import io.github.mejdi14.textmotion.BlurTextDefaults
import io.github.mejdi14.textmotion.BlurTextDirection
import io.github.mejdi14.textmotion.BlurTextEffect
import io.github.mejdi14.textmotion.BlurTextReplay
import io.github.mejdi14.textmotion.Repeat
import io.github.mejdi14.textmotion.Stagger
import io.github.mejdi14.textmotion.StaggerFrom
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.rememberTextAnimationController

private const val BlurTextCode = """BlurText(
    text = "Isn't this so cool?!",
    style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
    effect = BlurTextEffect.Blur,          // Zoom, Tilt, Focus, Drift
    direction = BlurTextDirection.Top,
    granularity = TextGranularity.Word,    // or Character
    stagger = Stagger(eachMillis = 150),
    blur = 10.dp,
    distance = 50.dp,
    onFinished = { println("Animation completed!") },
)"""

private val demoStyle = TextStyle(
    fontSize = 34.sp,
    fontWeight = FontWeight.SemiBold,
    color = ShowcaseColors.TextPrimary,
    textAlign = TextAlign.Center,
)

val blurTextDemo: AnimationDemo = AnimationDemo(
    id = "blur-text",
    name = "Blur Text",
    description = "Text sharpens from a soft blur as each word or letter fades into focus.",
    category = DemoCategory.Entrance,
    implemented = true,
    preview = {
        BlurText(
            text = "Isn't this so cool?!",
            style = demoStyle.copy(fontSize = 26.sp),
            repeat = BlurTextDefaults.Loop,
        )
    },
    screen = { BlurTextScreen() },
    code = BlurTextCode,
)

@Composable
private fun BlurTextScreen() {
    var text by remember { mutableStateOf("Isn't this so cool?!") }
    var effect by remember { mutableStateOf(BlurTextEffect.Blur) }
    var direction by remember { mutableStateOf(BlurTextDirection.Top) }
    var granularity by remember { mutableStateOf(TextGranularity.Word) }
    var staggerFrom by remember { mutableStateOf(StaggerFrom.Start) }
    var replayOn by remember { mutableStateOf(BlurTextReplay.None) }
    var delay by remember { mutableIntStateOf(200) }
    var step by remember { mutableIntStateOf(350) }
    var blur by remember { mutableFloatStateOf(10f) }
    var distance by remember { mutableFloatStateOf(50f) }
    var color by remember { mutableStateOf(ShowcaseColors.TextPrimary) }
    var tint by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(false) }
    var focusOnHover by remember { mutableStateOf(false) }
    var completions by remember { mutableIntStateOf(0) }
    val controller = rememberTextAnimationController()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage(Modifier.fillMaxWidth().padding(vertical = 4.dp), minHeight = 260.dp) {
            BlurText(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = demoStyle.copy(color = color),
                effect = effect,
                direction = direction,
                blur = blur.dp,
                distance = distance.dp,
                fromColor = if (tint) Color(0xFF8B5CF6) else Color.Unspecified,
                focusOnHover = focusOnHover,
                replayOn = replayOn,
                stepDurationMillis = step,
                stagger = Stagger(eachMillis = delay, from = staggerFrom),
                granularity = granularity,
                repeat = if (loop) BlurTextDefaults.Loop else Repeat.Once,
                controller = controller,
                onFinished = { completions++ },
            )
        }
        ControlPanel {
            TextControl("Text", text) { text = it }
            PlaybackControls(controller)
            ChoiceControl("Effect", BlurTextEffect.entries, effect, { effect = it })
            ChoiceControl("Direction", BlurTextDirection.entries, direction, { direction = it })
            ChoiceControl(
                "Animate by", listOf(TextGranularity.Word, TextGranularity.Character), granularity, { granularity = it },
                labelOf = { if (it == TextGranularity.Word) "Words" else "Letters" },
            )
            ChoiceControl("Stagger from", listOf(StaggerFrom.Start, StaggerFrom.End, StaggerFrom.Center, StaggerFrom.Random), staggerFrom, { staggerFrom = it })
            ChoiceControl("Replay on", BlurTextReplay.entries, replayOn, { replayOn = it })
            IntSliderControl("Delay between pieces", delay, { delay = it }, 0..500, valueFormat = { "$it ms" })
            DurationControl("Step duration", step, { step = it }, 100..1500)
            SliderControl("Blur", blur, { blur = it }, 0f..30f, valueFormat = { "${it.toInt()} dp" })
            SliderControl("Distance", distance, { distance = it }, 0f..150f, valueFormat = { "${it.toInt()} dp" })
            ColorControl("Color", color, { color = it })
            SwitchControl("Tint hidden text (fromColor)", tint) { tint = it }
            SwitchControl("Loop", loop) { loop = it }
            SwitchControl("Focus on hover", focusOnHover) { focusOnHover = it }
            androidx.compose.material3.Text("Completed intros: $completions", color = ShowcaseColors.TextSecondary, fontSize = 12.sp)
        }
    }
}
