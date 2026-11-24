package com.mejdi14.kmp_text_animation.showcase.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.AnimationDemo
import com.mejdi14.kmp_text_animation.showcase.DemoCategory
import com.mejdi14.kmp_text_animation.showcase.controls.ChoiceControl
import com.mejdi14.kmp_text_animation.showcase.controls.CodeBlock
import com.mejdi14.kmp_text_animation.showcase.controls.ColorControl
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.PointerInteraction
import io.github.mejdi14.textmotion.TextPressure
import io.github.mejdi14.textmotion.TextPressureIdle
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val TextPressureCode = """
TextPressure(
    text = "Compressa",
    modifier = Modifier.fillMaxWidth(),
    font = VariableFont.RobotoFlex,   // bundled variable font (wght, wdth, slnt)
    width = true,
    weight = true,
    italic = true,
    alpha = false,
    flex = true,
    stroke = false,
    scale = false,
    radius = 0.5f,
    smoothing = 0.5f,
    idle = TextPressureIdle.None,
    clickRipple = true,
    textColor = Color.White,
    pressureColor = Color.Unspecified,
    strokeColor = Color(0xFFFF0000),
    minFontSize = 24.sp,
)
""".trimIndent()

val textPressureDemo = AnimationDemo(
    id = "text-pressure",
    name = "Text Pressure",
    description = "Letter weight and width swell as your pointer comes near.",
    category = DemoCategory.Interactive,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextPressure(
                "Pressure",
                modifier = Modifier.fillMaxWidth(),
                textColor = Color.White,
                idle = TextPressureIdle.Wave,
            )
        }
    },
    screen = { TextPressureScreen() },
    code = TextPressureCode,
)

private val PressureColors = listOf(Color(0xFF5227FF), Color(0xFFFF9FFC), Color(0xFF22D3EE), Color(0xFFFACC15), Color(0xFFFF4D4D))

@Composable
private fun TextPressureScreen() {
    var text by remember { mutableStateOf("Compressa") }
    var width by remember { mutableStateOf(true) }
    var weight by remember { mutableStateOf(true) }
    var italic by remember { mutableStateOf(true) }
    var alpha by remember { mutableStateOf(false) }
    var flex by remember { mutableStateOf(true) }
    var stroke by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(false) }
    var ripple by remember { mutableStateOf(true) }
    var uppercase by remember { mutableStateOf(true) }
    var radius by remember { mutableFloatStateOf(0.5f) }
    var smoothing by remember { mutableFloatStateOf(0.5f) }
    var idle by remember { mutableStateOf(TextPressureIdle.None) }
    var idleSpeed by remember { mutableFloatStateOf(1f) }
    var textColor by remember { mutableStateOf(Color.White) }
    var usePressureColor by remember { mutableStateOf(false) }
    var pressureColor by remember { mutableStateOf(PressureColors.first()) }
    var strokeColor by remember { mutableStateOf(Color(0xFFFF0000)) }
    var strokeWidth by remember { mutableFloatStateOf(3f) }
    var interaction by remember { mutableStateOf("Pointer") }
    val controller = rememberTextAnimationController()

    val pointer: PointerInteraction = when (interaction) {
        "Auto" -> PointerInteraction.Auto()
        "Pointer + auto" -> PointerInteraction.PointerWithAutoFallback()
        else -> PointerInteraction.Pointer
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage(minHeight = 260.dp) {
            val stageModifier = if (scale) Modifier.fillMaxWidth().height(212.dp) else Modifier.fillMaxWidth()
            Box(stageModifier, contentAlignment = Alignment.Center) {
                TextPressure(
                    text,
                    modifier = if (scale) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                    width = width, weight = weight, italic = italic, alpha = alpha, flex = flex,
                    stroke = stroke, scale = scale, radius = radius, smoothing = smoothing,
                    idle = idle, idleSpeed = idleSpeed, clickRipple = ripple,
                    textColor = textColor,
                    pressureColor = if (usePressureColor) pressureColor else Color.Unspecified,
                    strokeColor = strokeColor, strokeWidth = strokeWidth.dp,
                    minFontSize = 24.sp, uppercase = uppercase,
                    pointerInteraction = pointer, controller = controller,
                )
            }
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            SwitchControl("Width (wdth)", width) { width = it }
            SwitchControl("Weight (wght)", weight) { weight = it }
            SwitchControl("Italic (slnt)", italic) { italic = it }
            SwitchControl("Alpha", alpha) { alpha = it }
            SwitchControl("Flex (fill width)", flex) { flex = it }
            SwitchControl("Stroke", stroke) { stroke = it }
            SwitchControl("Scale to height", scale) { scale = it }
            SwitchControl("Click ripple", ripple) { ripple = it }
            SwitchControl("Uppercase", uppercase) { uppercase = it }
            SliderControl("Radius (fraction of width)", radius, { radius = it }, 0.1f..1.5f)
            SliderControl("Smoothing", smoothing, { smoothing = it }, 0f..1f)
            ChoiceControl("Idle", TextPressureIdle.entries, idle, { idle = it })
            SliderControl("Idle speed", idleSpeed, { idleSpeed = it }, 0.25f..3f)
            ChoiceControl("Pointer", listOf("Pointer", "Pointer + auto", "Auto"), interaction, { interaction = it })
            ColorControl("Text colour", textColor, { textColor = it })
            SwitchControl("Pressure colour", usePressureColor) { usePressureColor = it }
            ColorControl("Pressure colour", pressureColor, { pressureColor = it }, PressureColors)
            ColorControl("Stroke colour", strokeColor, { strokeColor = it })
            SliderControl("Stroke width (dp)", strokeWidth, { strokeWidth = it }, 0.5f..8f)
        }
        CodeBlock(TextPressureCode)
    }
}
