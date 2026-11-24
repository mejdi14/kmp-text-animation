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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.AnimationDemo
import com.mejdi14.kmp_text_animation.showcase.DemoCategory
import com.mejdi14.kmp_text_animation.showcase.controls.CodeBlock
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.DurationControl
import com.mejdi14.kmp_text_animation.showcase.controls.IntSliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.TextCharsets
import io.github.mejdi14.textmotion.TextScramble
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val TextScrambleCode = """
TextScramble(
    text = "Scrambling into place",
    style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
    durationMillis = 800,
    tickMillis = 40,
    characterSet = TextCharsets.Alphanumeric,
    onFinished = { /* scramble complete */ },
)
""".trimIndent()

private val Light = Color(0xFFE4E4E7)

val textScrambleDemo = AnimationDemo(
    id = "text-scramble",
    name = "Text Scramble",
    description = "Random glyphs shuffle and resolve into the final message.",
    category = DemoCategory.Transition,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextScramble(
                "Text Scramble",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Light),
            )
        }
    },
    screen = { TextScrambleScreen() },
    code = TextScrambleCode,
)

@Composable
private fun TextScrambleScreen() {
    var text by remember { mutableStateOf("Scrambling into place") }
    var duration by remember { mutableIntStateOf(800) }
    var tick by remember { mutableIntStateOf(40) }
    var speed by remember { mutableFloatStateOf(1f) }
    var charset by remember { mutableStateOf(TextCharsets.Alphanumeric) }
    var mono by remember { mutableStateOf(false) }
    val controller = rememberTextAnimationController()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            TextScramble(
                text,
                style = TextStyle(
                    fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Light,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                ),
                durationMillis = duration, tickMillis = tick, characterSet = charset,
                speed = speed, controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            DurationControl("Duration", duration, { duration = it }, 200..4000)
            IntSliderControl("Tick (ms per swap)", tick, { tick = it }, 10..200)
            SliderControl("Speed", speed, { speed = it }, 0.25f..3f)
            TextControl("Character set", charset) { charset = it }
            com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl("Monospace font", mono) { mono = it }
        }
        CodeBlock(TextScrambleCode)
    }
}
