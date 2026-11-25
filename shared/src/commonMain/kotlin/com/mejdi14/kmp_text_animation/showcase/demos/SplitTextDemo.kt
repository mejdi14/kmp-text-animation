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
import androidx.compose.ui.text.style.TextAlign
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
import io.github.mejdi14.textmotion.SplitText
import io.github.mejdi14.textmotion.SplitTextDefaults
import io.github.mejdi14.textmotion.SplitTextEffect
import io.github.mejdi14.textmotion.SplitTextHoverEffect
import io.github.mejdi14.textmotion.SplitTextReplay
import io.github.mejdi14.textmotion.Stagger
import io.github.mejdi14.textmotion.StaggerFrom
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val SplitTextCode = """
SplitText(
    text = "Hello, TextMotion!",
    style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White),
    effect = SplitTextEffect.Rise,            // Fade, Blur, Flip, Pop, Skew, Wave
    granularity = TextGranularity.Character,  // Word, Line
    stagger = Stagger(eachMillis = 50, from = StaggerFrom.Start),
    durationMillis = 1250,
    mask = false,
    accentColor = Color(0xFF8B5CF6),          // flash colour while revealing
    hoverEffect = SplitTextHoverEffect.Lift,  // Wave
    replayOn = SplitTextReplay.Click,         // Hover
    repeat = SplitTextDefaults.Loop,          // loop with a 1.5 s pause
    onFinished = { /* every reveal complete */ },
)
""".trimIndent()

private val Violet = Color(0xFF8B5CF6)

val splitTextDemo = AnimationDemo(
    id = "split-text",
    name = "Split Text",
    description = "Text is split into characters, words or lines that animate in with a stagger.",
    category = DemoCategory.Entrance,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SplitText(
                "Split Text",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White),
                repeat = SplitTextDefaults.Loop,
                durationMillis = 900,
            )
        }
    },
    screen = { SplitTextScreen() },
    code = SplitTextCode,
)

@Composable
private fun SplitTextScreen() {
    var text by remember { mutableStateOf("Hello, TextMotion! Every letter arrives on its own.") }
    var effect by remember { mutableStateOf(SplitTextEffect.Rise) }
    var granularity by remember { mutableStateOf(TextGranularity.Character) }
    var from by remember { mutableStateOf(StaggerFrom.Start) }
    var each by remember { mutableIntStateOf(50) }
    var duration by remember { mutableIntStateOf(1250) }
    var mask by remember { mutableStateOf(false) }
    var accentOn by remember { mutableStateOf(false) }
    var accent by remember { mutableStateOf(Violet) }
    var hover by remember { mutableStateOf(SplitTextHoverEffect.None) }
    var replay by remember { mutableStateOf(SplitTextReplay.Click) }
    var loop by remember { mutableStateOf(false) }
    var size by remember { mutableFloatStateOf(36f) }
    var speed by remember { mutableFloatStateOf(1f) }
    val controller = rememberTextAnimationController()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage(minHeight = 260.dp) {
            SplitText(
                text,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = size.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    textAlign = TextAlign.Center, lineHeight = (size * 1.25f).sp,
                ),
                effect = effect,
                granularity = granularity,
                mask = mask,
                accentColor = if (accentOn) accent else Color.Unspecified,
                hoverEffect = hover,
                replayOn = replay,
                durationMillis = duration,
                stagger = Stagger(eachMillis = each, from = from),
                repeat = if (loop) SplitTextDefaults.Loop else Repeat.Once,
                speed = speed,
                controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            ChoiceControl("Effect", SplitTextEffect.entries, effect, { effect = it })
            ChoiceControl("Split by", TextGranularity.entries, granularity, { granularity = it }) {
                when (it) {
                    TextGranularity.Character -> "chars"
                    TextGranularity.Word -> "words"
                    TextGranularity.Line -> "lines"
                }
            }
            ChoiceControl("Stagger from", StaggerFrom.entries, from, { from = it })
            IntSliderControl("Delay between units (ms)", each, { each = it }, 0..200)
            DurationControl("Duration", duration, { duration = it }, 200..4000)
            SliderControl("Font size", size, { size = it }, 18f..64f)
            SliderControl("Speed", speed, { speed = it }, 0.25f..3f)
            SwitchControl("Mask each unit", mask) { mask = it }
            SwitchControl("Accent flash", accentOn) { accentOn = it }
            if (accentOn) ColorControl("Accent colour", accent, { accent = it })
            SwitchControl("Loop (1.5 s pause)", loop) { loop = it }
            ChoiceControl("Replay on", SplitTextReplay.entries, replay, { replay = it })
            ChoiceControl("Hover effect", SplitTextHoverEffect.entries, hover, { hover = it })
        }
        CodeBlock(SplitTextCode)
    }
}
