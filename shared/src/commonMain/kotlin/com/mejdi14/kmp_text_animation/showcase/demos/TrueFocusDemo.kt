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
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.TrueFocus
import io.github.mejdi14.textmotion.TrueFocusEasing
import io.github.mejdi14.textmotion.TrueFocusFrameStyle
import io.github.mejdi14.textmotion.TrueFocusOrder
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val TrueFocusCode = """
TrueFocus(
    text = "True Focus",
    style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.White),
    manualMode = false,               // true: hover / touch a word to focus it
    frameStyle = TrueFocusFrameStyle.Brackets,
    blurAmount = 5.dp,
    borderColor = Color(0xFF00FF00),
    glowColor = Color(0xFF00FF00),
    animationDurationMillis = 500,
    pauseBetweenMillis = 1000,
)
""".trimIndent()

private val Green = Color(0xFF00FF00)

val trueFocusDemo = AnimationDemo(
    id = "true-focus",
    name = "True Focus",
    description = "A focus frame hops between words while the rest stay blurred.",
    category = DemoCategory.Continuous,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TrueFocus(
                "True Focus",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White),
                borderColor = Green, glowColor = Green,
            )
        }
    },
    screen = { TrueFocusScreen() },
    code = TrueFocusCode,
)

@Composable
private fun TrueFocusScreen() {
    var text by remember { mutableStateOf("True Focus Makes Words Stand Out") }
    var manual by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf(TrueFocusFrameStyle.Brackets) }
    var order by remember { mutableStateOf(TrueFocusOrder.Sequential) }
    var easing by remember { mutableStateOf(TrueFocusEasing.Smooth) }
    var blur by remember { mutableFloatStateOf(5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var duration by remember { mutableIntStateOf(500) }
    var pause by remember { mutableIntStateOf(1000) }
    var border by remember { mutableStateOf(Green) }
    var glow by remember { mutableStateOf(Green) }
    var unfocused by remember { mutableFloatStateOf(1f) }
    var speed by remember { mutableFloatStateOf(1f) }
    val controller = rememberTextAnimationController()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            TrueFocus(
                text,
                style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White),
                manualMode = manual, frameStyle = frame, order = order, easing = easing,
                blurAmount = blur.dp, focusScale = scale, borderColor = border, glowColor = glow,
                animationDurationMillis = duration, pauseBetweenMillis = pause, unfocusedOpacity = unfocused,
                speed = speed, controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Sentence", text) { text = it }
            SwitchControl("Manual mode (hover / touch a word)", manual) { manual = it }
            ChoiceControl("Frame", TrueFocusFrameStyle.entries, frame, { frame = it })
            ChoiceControl("Order", TrueFocusOrder.entries, order, { order = it })
            ChoiceControl("Easing", TrueFocusEasing.entries, easing, { easing = it })
            SliderControl("Blur (dp)", blur, { blur = it }, 0f..16f)
            SliderControl("Focus scale", scale, { scale = it }, 0.8f..1.6f)
            SliderControl("Unfocused opacity", unfocused, { unfocused = it }, 0f..1f)
            DurationControl("Animation duration", duration, { duration = it }, 100..2000)
            DurationControl("Pause between", pause, { pause = it }, 0..4000)
            SliderControl("Speed", speed, { speed = it }, 0.25f..3f)
            ColorControl("Border colour", border, { border = it })
            ColorControl("Glow colour", glow, { glow = it })
        }
        CodeBlock(TrueFocusCode)
    }
}
