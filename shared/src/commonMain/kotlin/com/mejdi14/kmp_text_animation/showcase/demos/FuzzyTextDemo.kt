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
import com.mejdi14.kmp_text_animation.showcase.controls.ColorsControl
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.DurationControl
import com.mejdi14.kmp_text_animation.showcase.controls.GradientPalettes
import com.mejdi14.kmp_text_animation.showcase.controls.IntSliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.FuzzyDirection
import io.github.mejdi14.textmotion.FuzzyText
import io.github.mejdi14.textmotion.FuzzyTextDefaults
import io.github.mejdi14.textmotion.FuzzyTrigger
import io.github.mejdi14.textmotion.PointerInteraction
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val FuzzyTextCode = """
FuzzyText(
    text = "404",
    style = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Black, color = Color.White),
    baseIntensity = 0.18f,
    hoverIntensity = 0.5f,
    fuzzRange = 30.dp,
    direction = FuzzyDirection.Horizontal,
    trigger = FuzzyTrigger.Hover,   // or Click (burst) / Always
    colorSplit = 0f,                 // > 0 adds RGB fringes
    glitchMode = false,
    intro = false,
)
""".trimIndent()

val fuzzyTextDemo = AnimationDemo(
    id = "fuzzy-text",
    name = "Fuzzy Text",
    description = "Text with a fuzzy, static-like edge that intensifies on hover.",
    category = DemoCategory.Interactive,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FuzzyText(
                "404",
                style = TextStyle(fontSize = 40.sp, color = Color(0xFFFAFAFA)),
                pointerInteraction = PointerInteraction.None,
            )
        }
    },
    screen = { FuzzyTextScreen() },
    code = FuzzyTextCode,
)

private enum class PointerMode(val label: String, val interaction: PointerInteraction) {
    Pointer("Pointer", PointerInteraction.Pointer),
    Auto("Auto", PointerInteraction.Auto()),
    None("None", PointerInteraction.None),
}

@Composable
private fun FuzzyTextScreen() {
    var text by remember { mutableStateOf("404") }
    var color by remember { mutableStateOf(Color(0xFFFAFAFA)) }
    var useGradient by remember { mutableStateOf(false) }
    var gradient by remember { mutableStateOf(GradientPalettes.first()) }
    var base by remember { mutableFloatStateOf(FuzzyTextDefaults.BaseIntensity) }
    var hover by remember { mutableFloatStateOf(FuzzyTextDefaults.HoverIntensity) }
    var range by remember { mutableFloatStateOf(30f) }
    var slice by remember { mutableFloatStateOf(1f) }
    var fps by remember { mutableIntStateOf(FuzzyTextDefaults.Fps) }
    var direction by remember { mutableStateOf(FuzzyDirection.Horizontal) }
    var trigger by remember { mutableStateOf(FuzzyTrigger.Hover) }
    var transition by remember { mutableIntStateOf(0) }
    var clickEffect by remember { mutableStateOf(false) }
    var glitch by remember { mutableStateOf(false) }
    var split by remember { mutableFloatStateOf(0f) }
    var intro by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(PointerMode.Pointer) }
    var speed by remember { mutableFloatStateOf(1f) }
    var fontSize by remember { mutableFloatStateOf(96f) }
    val controller = rememberTextAnimationController()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            FuzzyText(
                text,
                style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Black, color = color, textAlign = TextAlign.Center),
                baseIntensity = base,
                hoverIntensity = hover,
                fuzzRange = range.dp,
                direction = direction,
                sliceSize = slice.dp,
                fps = fps,
                trigger = trigger,
                transitionDurationMillis = transition,
                clickEffect = clickEffect,
                glitchMode = glitch,
                gradient = if (useGradient) gradient else null,
                colorSplit = split,
                intro = intro,
                pointerInteraction = pointer.interaction,
                speed = speed,
                controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            ChoiceControl("Trigger", FuzzyTrigger.entries, trigger, { trigger = it })
            ChoiceControl("Pointer", PointerMode.entries, pointer, { pointer = it }, labelOf = { it.label })
            ChoiceControl("Direction", FuzzyDirection.entries, direction, { direction = it })
            SliderControl("Base intensity", base, { base = it }, 0f..1f)
            SliderControl("Hover intensity", hover, { hover = it }, 0f..1f)
            SliderControl("Fuzz range", range, { range = it }, 0f..80f, valueFormat = { "${it.toInt()} dp" })
            SliderControl("Slice size", slice, { slice = it }, 1f..8f, valueFormat = { "${(it * 10).toInt() / 10f} dp" })
            IntSliderControl("FPS", fps, { fps = it }, 5..60)
            DurationControl("Transition", transition, { transition = it }, 0..1000)
            SliderControl("Colour split", split, { split = it }, 0f..3f)
            SwitchControl("Click flash", clickEffect) { clickEffect = it }
            SwitchControl("Glitch mode", glitch) { glitch = it }
            SwitchControl("Intro (replay with restart)", intro) { intro = it; if (it) controller.restart() }
            ColorControl("Colour", color, { color = it })
            SwitchControl("Gradient", useGradient) { useGradient = it }
            if (useGradient) ColorsControl("Gradient colours", gradient, { gradient = it })
            SliderControl("Speed", speed, { speed = it }, 0.25f..3f)
            SliderControl("Font size", fontSize, { fontSize = it }, 24f..160f, valueFormat = { "${it.toInt()} sp" })
        }
        CodeBlock(FuzzyTextCode)
    }
}
