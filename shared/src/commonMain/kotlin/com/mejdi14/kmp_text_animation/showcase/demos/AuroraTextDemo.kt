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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.AnimationDemo
import com.mejdi14.kmp_text_animation.showcase.DemoCategory
import com.mejdi14.kmp_text_animation.showcase.controls.CodeBlock
import com.mejdi14.kmp_text_animation.showcase.controls.ColorsControl
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.DurationControl
import com.mejdi14.kmp_text_animation.showcase.controls.GradientPalettes
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.AuroraText
import io.github.mejdi14.textmotion.AuroraTextDefaults
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val AuroraTextCode = """
AuroraText(
    text = "Ship faster",
    style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold),
    colors = listOf(
        Color(0xFFFF0080), Color(0xFF7928CA),
        Color(0xFF0070F3), Color(0xFF38BDF8),
    ),
    speed = 1f, // one keyframe pass = 10 s / speed
)
""".trimIndent()

private val AuroraPalettes: List<List<androidx.compose.ui.graphics.Color>> =
    listOf(AuroraTextDefaults.Colors) + GradientPalettes

val auroraTextDemo = AnimationDemo(
    id = "aurora-text",
    name = "Aurora Text",
    description = "Slow drifting aurora colors flow through the letters.",
    category = DemoCategory.Continuous,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AuroraText(
                "Aurora Text",
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
        }
    },
    screen = { AuroraTextScreen() },
    code = AuroraTextCode,
)

@Composable
private fun AuroraTextScreen() {
    var text by remember { mutableStateOf("Ship faster") }
    var colors by remember { mutableStateOf(AuroraTextDefaults.Colors) }
    var speed by remember { mutableFloatStateOf(1f) }
    var duration by remember { mutableIntStateOf(AuroraTextDefaults.DurationMillis) }
    var angle by remember { mutableFloatStateOf(AuroraTextDefaults.AngleDegrees) }
    var intensity by remember { mutableFloatStateOf(1f) }
    var fontSize by remember { mutableFloatStateOf(56f) }
    val controller = rememberTextAnimationController()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            AuroraText(
                text,
                style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                colors = colors,
                durationMillis = duration,
                angleDegrees = angle,
                motionIntensity = intensity,
                speed = speed,
                controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            ColorsControl("Colours", colors, { colors = it }, AuroraPalettes)
            SliderControl("Speed", speed, { speed = it }, 0.25f..5f)
            DurationControl("Keyframe pass (at speed 1)", duration, { duration = it }, 1000..20000)
            SliderControl("Gradient angle", angle, { angle = it }, 0f..360f, valueFormat = { "${it.toInt()}°" })
            SliderControl("Sway intensity", intensity, { intensity = it }, 0f..2f)
            SliderControl("Font size", fontSize, { fontSize = it }, 20f..96f, valueFormat = { "${it.toInt()} sp" })
        }
        CodeBlock(AuroraTextCode)
    }
}
