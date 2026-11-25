package com.mejdi14.kmp_text_animation.showcase.demos

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.AnimationDemo
import com.mejdi14.kmp_text_animation.showcase.DemoCategory
import com.mejdi14.kmp_text_animation.showcase.ShowcaseColors
import com.mejdi14.kmp_text_animation.showcase.controls.ChoiceControl
import com.mejdi14.kmp_text_animation.showcase.controls.CodeBlock
import com.mejdi14.kmp_text_animation.showcase.controls.ColorControl
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.IntSliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.ScrollReveal
import io.github.mejdi14.textmotion.ScrollRevealEffect
import io.github.mejdi14.textmotion.ScrollRevealMode
import io.github.mejdi14.textmotion.TextGranularity
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val ScrollRevealCode = """
Column(Modifier.verticalScroll(rememberScrollState())) {
    Spacer(Modifier.height(600.dp))
    ScrollReveal(
        text = "Words unblur and fade in as you scroll.",
        style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
        effect = ScrollRevealEffect.Fade,        // Rise, Flip, Scale
        granularity = TextGranularity.Word,      // Character, Line
        baseOpacity = 0.1f,
        baseRotation = 3f,                       // block starts tilted, settles upright
        blurStrength = 4.dp,
        enableBlur = true,
        stagger = 0.05f,
        highlightColor = Color(0xFF8B5CF6),      // optional colour flash per word
        // reference start/end: ScrollRevealDefaults.WordRange / RotationRange
        // scrollProgress = { myProgress },      // or drive it yourself
    )
    Spacer(Modifier.height(600.dp))
}
""".trimIndent()

private val Light = Color(0xFFE4E4E7)
private const val Sample =
    "When I look at the world around me, every word I read fades slowly into focus, " +
        "line by line, as the page moves under my fingers."

val scrollRevealDemo = AnimationDemo(
    id = "scroll-reveal",
    name = "Scroll Reveal",
    description = "Words unblur and fade in progressively as you scroll.",
    category = DemoCategory.Scroll,
    implemented = true,
    preview = {
        // Auto-scrubbed so the gallery card shows the effect without any scrolling.
        val transition = rememberInfiniteTransition()
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            ScrollReveal(
                text = "Every word fades into focus as you scroll.",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Light),
                scrollProgress = { progress },
            )
        }
    },
    screen = { ScrollRevealScreen() },
    code = ScrollRevealCode,
)

private val Granularities = listOf(TextGranularity.Word, TextGranularity.Character, TextGranularity.Line)

@Composable
private fun ScrollRevealScreen() {
    var text by remember { mutableStateOf(Sample) }
    var effect by remember { mutableStateOf(ScrollRevealEffect.Fade) }
    var granularity by remember { mutableStateOf(TextGranularity.Word) }
    var enter by remember { mutableStateOf(false) }
    var blur by remember { mutableStateOf(true) }
    var blurStrength by remember { mutableFloatStateOf(4f) }
    var baseOpacity by remember { mutableFloatStateOf(0.1f) }
    var rotation by remember { mutableFloatStateOf(3f) }
    var offset by remember { mutableIntStateOf(0) }
    var stagger by remember { mutableFloatStateOf(0.05f) }
    var smoothing by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(800) }
    var highlight by remember { mutableStateOf(false) }
    var highlightColor by remember { mutableStateOf(Color(0xFF8B5CF6)) }
    var manual by remember { mutableStateOf(false) }
    var manualProgress by remember { mutableFloatStateOf(0f) }
    val controller = rememberTextAnimationController()
    val style = TextStyle(fontSize = 30.sp, lineHeight = 45.sp, fontWeight = FontWeight.SemiBold, color = Light)

    @Composable
    fun Reveal() {
        ScrollReveal(
            text = text,
            style = style,
            effect = effect,
            granularity = granularity,
            enableBlur = blur,
            baseOpacity = baseOpacity,
            baseRotation = rotation,
            blurStrength = blurStrength.dp,
            baseOffset = offset.dp,
            stagger = stagger,
            highlightColor = if (highlight) highlightColor else Color.Unspecified,
            mode = if (enter) ScrollRevealMode.Enter else ScrollRevealMode.Scrub,
            scrollProgress = if (manual && !enter) ({ manualProgress }) else null,
            smoothingMillis = smoothing,
            durationMillis = duration,
            controller = controller,
        )
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ControlPanel {
            TextControl("Text", text) { text = it }
            ChoiceControl("Effect", ScrollRevealEffect.entries, effect, { effect = it }) { it.name }
            ChoiceControl("Split by", Granularities, granularity, { granularity = it }) {
                when (it) {
                    TextGranularity.Word -> "Words"
                    TextGranularity.Character -> "Characters"
                    TextGranularity.Line -> "Lines"
                }
            }
            SwitchControl("Enter mode (timed one-shot instead of scrubbed)", enter) { enter = it }
            if (!enter) SwitchControl("Manual progress (no scrolling needed)", manual) { manual = it }
            if (manual && !enter) {
                SliderControl("Progress", manualProgress, { manualProgress = it }, 0f..1f)
            }
            SwitchControl("Blur", blur) { blur = it }
            SliderControl("Blur strength (dp)", blurStrength, { blurStrength = it }, 0f..16f)
            SliderControl("Base opacity", baseOpacity, { baseOpacity = it }, 0f..1f)
            SliderControl("Base rotation (deg)", rotation, { rotation = it }, -12f..12f)
            IntSliderControl("Base offset (dp)", offset, { offset = it }, 0..60)
            SliderControl("Stagger", stagger, { stagger = it }, 0f..0.3f)
            if (!enter) IntSliderControl("Smoothing (ms)", smoothing, { smoothing = it }, 0..2000)
            if (enter) IntSliderControl("Duration (ms)", duration, { duration = it }, 100..3000)
            SwitchControl("Highlight colour flash", highlight) { highlight = it }
            if (highlight) ColorControl("Highlight colour", highlightColor, { highlightColor = it })
            if (enter) PlaybackControls(controller)
        }

        if (enter || manual) {
            DemoStage { Box(Modifier.fillMaxWidth()) { Reveal() } }
        } else {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(ShowcaseColors.Surface)
                    .border(1.dp, ShowcaseColors.Border, shape)
                    .padding(24.dp),
            ) {
                Text("Scroll down", color = ShowcaseColors.TextSecondary, fontSize = 14.sp)
                Box(Modifier.height(520.dp))
                Reveal()
                Box(Modifier.height(520.dp))
                Text("Scroll back up to reverse it", color = ShowcaseColors.TextMuted, fontSize = 13.sp)
            }
        }
    }
}
