package com.mejdi14.kmp_text_animation.showcase.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.TextStyle
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
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.PointerInteraction
import io.github.mejdi14.textmotion.ProximityFalloff
import io.github.mejdi14.textmotion.VariableProximity
import io.github.mejdi14.textmotion.proximityArea
import io.github.mejdi14.textmotion.rememberProximityArea
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val VariableProximityCode = """
val area = rememberProximityArea()          // the reference's containerRef
Box(Modifier.fillMaxWidth().proximityArea(area)) {
    VariableProximity(
        text = "Hover me! And then star TextMotion on GitHub, or else...",
        style = TextStyle(color = Color.White, fontSize = 32.sp, textAlign = TextAlign.Center),
        fromFontVariationSettings = "'wght' 400, 'opsz' 9",
        toFontVariationSettings = "'wght' 1000, 'opsz' 40",
        radius = 100.dp,
        falloff = ProximityFalloff.Linear,
        smoothing = 0.2f,
        area = area,
    )
}
""".trimIndent()

val variableProximityDemo = AnimationDemo(
    id = "variable-proximity",
    name = "Variable Proximity",
    description = "Each letter reacts to how close the pointer is, shifting its weight.",
    category = DemoCategory.Interactive,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            VariableProximity(
                "Proximity",
                style = TextStyle(color = Color.White, fontSize = 34.sp),
                radius = 80.dp,
                idleWave = true,
                pointerInteraction = PointerInteraction.PointerWithAutoFallback(),
            )
        }
    },
    screen = { VariableProximityScreen() },
    code = VariableProximityCode,
)

private class SettingsPreset(val name: String, val from: String, val to: String)

private val Presets = listOf(
    SettingsPreset("Weight + optical size", "'wght' 400, 'opsz' 9", "'wght' 1000, 'opsz' 40"),
    SettingsPreset("Thin → Black", "'wght' 100", "'wght' 1000"),
    SettingsPreset("Condensed → Wide", "'wght' 300, 'wdth' 50", "'wght' 800, 'wdth' 151"),
    SettingsPreset("Upright → Slanted", "'wght' 400, 'slnt' 0", "'wght' 700, 'slnt' -10"),
)

private val ProximityColors = listOf(Color.White, Color(0xFF5227FF), Color(0xFFFF9FFC), Color(0xFF22D3EE), Color(0xFFFACC15), Color(0xFFFF4D4D))

@Composable
private fun VariableProximityScreen() {
    var text by remember { mutableStateOf("Hover me! And then star TextMotion on GitHub, or else...") }
    var preset by remember { mutableStateOf(Presets.first()) }
    var from by remember { mutableStateOf(Presets.first().from) }
    var to by remember { mutableStateOf(Presets.first().to) }
    var radius by remember { mutableFloatStateOf(100f) }
    var falloff by remember { mutableStateOf(ProximityFalloff.Linear) }
    var smoothing by remember { mutableFloatStateOf(0.2f) }
    var fontSize by remember { mutableFloatStateOf(32f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var lift by remember { mutableFloatStateOf(0f) }
    var idleWave by remember { mutableStateOf(false) }
    var useColors by remember { mutableStateOf(false) }
    var fromColor by remember { mutableStateOf(Color.White) }
    var toColor by remember { mutableStateOf(ProximityColors[1]) }
    var useArea by remember { mutableStateOf(true) }
    var align by remember { mutableStateOf("Center") }
    var interaction by remember { mutableStateOf("Pointer") }
    val controller = rememberTextAnimationController()
    val area = rememberProximityArea()

    val pointer: PointerInteraction = when (interaction) {
        "Auto" -> PointerInteraction.Auto()
        "Pointer + auto" -> PointerInteraction.PointerWithAutoFallback()
        else -> PointerInteraction.Pointer
    }
    val textAlign = when (align) {
        "Start" -> TextAlign.Start
        "End" -> TextAlign.End
        else -> TextAlign.Center
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage(minHeight = 260.dp) {
            Box(
                Modifier.fillMaxWidth().height(260.dp).then(if (useArea) Modifier.proximityArea(area) else Modifier)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                VariableProximity(
                    text,
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(color = Color.White, fontSize = fontSize.sp, textAlign = textAlign),
                    fromFontVariationSettings = from,
                    toFontVariationSettings = to,
                    radius = radius.dp,
                    falloff = falloff,
                    smoothing = smoothing,
                    fromColor = if (useColors) fromColor else Color.Unspecified,
                    toColor = if (useColors) toColor else Color.Unspecified,
                    scale = scale,
                    lift = lift,
                    idleWave = idleWave,
                    area = if (useArea) area else null,
                    pointerInteraction = pointer,
                    controller = controller,
                )
            }
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            ChoiceControl("Preset", Presets, preset, { preset = it; from = it.from; to = it.to }, labelOf = { it.name })
            TextControl("From font-variation-settings", from) { from = it }
            TextControl("To font-variation-settings", to) { to = it }
            SliderControl("Radius (dp)", radius, { radius = it }, 10f..300f, valueFormat = { it.toInt().toString() })
            ChoiceControl("Falloff", ProximityFalloff.entries, falloff, { falloff = it })
            SliderControl("Smoothing", smoothing, { smoothing = it }, 0f..1f)
            SliderControl("Font size (sp)", fontSize, { fontSize = it }, 16f..72f, valueFormat = { it.toInt().toString() })
            SliderControl("Scale", scale, { scale = it }, 0.5f..2f)
            SliderControl("Lift (em)", lift, { lift = it }, 0f..0.6f)
            SwitchControl("Idle wave", idleWave) { idleWave = it }
            SwitchControl("Track the whole stage (containerRef)", useArea) { useArea = it }
            ChoiceControl("Alignment", listOf("Start", "Center", "End"), align, { align = it })
            ChoiceControl("Pointer", listOf("Pointer", "Pointer + auto", "Auto"), interaction, { interaction = it })
            SwitchControl("Colour mix", useColors) { useColors = it }
            ColorControl("From colour", fromColor, { fromColor = it }, ProximityColors)
            ColorControl("To colour", toColor, { toColor = it }, ProximityColors)
        }
        CodeBlock(VariableProximityCode)
    }
}
