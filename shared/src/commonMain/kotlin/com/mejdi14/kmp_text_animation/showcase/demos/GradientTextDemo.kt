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
import com.mejdi14.kmp_text_animation.showcase.controls.ChoiceControl
import com.mejdi14.kmp_text_animation.showcase.controls.CodeBlock
import com.mejdi14.kmp_text_animation.showcase.controls.ColorsControl
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.DurationControl
import com.mejdi14.kmp_text_animation.showcase.controls.GradientPalettes
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.GradientText
import io.github.mejdi14.textmotion.GradientTextDefaults
import io.github.mejdi14.textmotion.GradientTextDirection
import io.github.mejdi14.textmotion.GradientTextVariant
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val GradientTextCode = """
GradientText(
    text = "Add a splash of color",
    style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold),
    colors = listOf(Color(0xFF5227FF), Color(0xFFFF9FFC), Color(0xFFB497CF)),
    cycleDurationMillis = 8_000,            // reference animationSpeed = 8 s
    variant = GradientTextVariant.Linear,   // Linear | Flow | Conic
    direction = GradientTextDirection.Horizontal,
    gradientScale = 3f,
    yoyo = true,
    glow = 0.5f,
    showBorder = true,
    pauseOnHover = false,
    followPointer = true,
)
""".trimIndent()

private val GradientPalettesWithDefault: List<List<androidx.compose.ui.graphics.Color>> =
    listOf(GradientTextDefaults.Colors) + GradientPalettes

private enum class DirectionChoice(val label: String, val direction: GradientTextDirection?) {
    Angle("Angle", null),
    Horizontal("Horizontal", GradientTextDirection.Horizontal),
    Vertical("Vertical", GradientTextDirection.Vertical),
    Diagonal("Diagonal", GradientTextDirection.Diagonal),
}

val gradientTextDemo = AnimationDemo(
    id = "gradient-text",
    name = "Gradient Text",
    description = "Animated multi-color gradient painted across the glyphs.",
    category = DemoCategory.Continuous,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GradientText(
                "Gradient Text",
                style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
        }
    },
    screen = { GradientTextScreen() },
    code = GradientTextCode,
)

@Composable
private fun GradientTextScreen() {
    var text by remember { mutableStateOf("Add a splash of color") }
    var colors by remember { mutableStateOf(GradientTextDefaults.Colors) }
    var variant by remember { mutableStateOf(GradientTextVariant.Linear) }
    var directionChoice by remember { mutableStateOf(DirectionChoice.Horizontal) }
    var angle by remember { mutableFloatStateOf(GradientTextDefaults.AngleDegrees) }
    var cycle by remember { mutableIntStateOf(GradientTextDefaults.CycleDurationMillis) }
    var scale by remember { mutableFloatStateOf(GradientTextDefaults.GradientScale) }
    var yoyo by remember { mutableStateOf(true) }
    var glow by remember { mutableFloatStateOf(0f) }
    var showBorder by remember { mutableStateOf(false) }
    var borderWidth by remember { mutableFloatStateOf(1.5f) }
    var pauseOnHover by remember { mutableStateOf(false) }
    var followPointer by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var fontSize by remember { mutableFloatStateOf(48f) }
    val controller = rememberTextAnimationController()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            GradientText(
                text,
                style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                colors = colors,
                cycleDurationMillis = cycle,
                variant = variant,
                angle = angle,
                direction = directionChoice.direction,
                gradientScale = scale,
                yoyo = yoyo,
                glow = glow,
                showBorder = showBorder,
                borderWidth = borderWidth.dp,
                pauseOnHover = pauseOnHover,
                followPointer = followPointer,
                speed = speed,
                controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            ColorsControl("Colours", colors, { colors = it }, GradientPalettesWithDefault)
            ChoiceControl("Variant", GradientTextVariant.entries, variant, { variant = it })
            ChoiceControl("Direction", DirectionChoice.entries, directionChoice, { directionChoice = it }) { it.label }
            if (directionChoice == DirectionChoice.Angle) {
                SliderControl("Angle", angle, { angle = it }, 0f..360f, valueFormat = { "${it.toInt()}°" })
            }
            DurationControl("Cycle (at speed 1)", cycle, { cycle = it }, 500..20000)
            SliderControl("Speed", speed, { speed = it }, 0.25f..5f)
            SliderControl("Gradient scale", scale, { scale = it }, 1f..6f)
            SwitchControl("Yoyo", yoyo) { yoyo = it }
            SliderControl("Glow", glow, { glow = it }, 0f..1f)
            SwitchControl("Show border", showBorder) { showBorder = it }
            if (showBorder) {
                SliderControl("Border width", borderWidth, { borderWidth = it }, 0.5f..6f, valueFormat = { "${it} dp" })
            }
            SwitchControl("Pause on hover", pauseOnHover) { pauseOnHover = it }
            SwitchControl("Follow pointer", followPointer) { followPointer = it }
            SliderControl("Font size", fontSize, { fontSize = it }, 20f..96f, valueFormat = { "${it.toInt()} sp" })
        }
        CodeBlock(GradientTextCode)
    }
}
