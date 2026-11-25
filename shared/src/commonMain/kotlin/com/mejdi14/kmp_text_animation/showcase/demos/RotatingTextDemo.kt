package com.mejdi14.kmp_text_animation.showcase.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mejdi14.kmp_text_animation.showcase.controls.SwitchControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.RotatingText
import io.github.mejdi14.textmotion.RotatingTextDirection
import io.github.mejdi14.textmotion.RotatingTextEffect
import io.github.mejdi14.textmotion.RotatingTextSplit
import io.github.mejdi14.textmotion.RotatingTextStaggerFrom
import io.github.mejdi14.textmotion.rememberRotatingTextState

private val RotatingTextCode = """
val state = rememberRotatingTextState()

Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Make it ", style = style)
    RotatingText(
        texts = listOf("Fast", "Smooth", "Native", "Delightful"),
        style = style,
        effect = RotatingTextEffect.Slide,         // Flip, Blur, Scale, Drop
        direction = RotatingTextDirection.Up,
        splitBy = RotatingTextSplit.Characters,    // Words, Lines, Custom(",")
        rotationIntervalMillis = 2000,
        staggerMillis = 25,
        staggerFrom = RotatingTextStaggerFrom.First, // Last, Center, Random, Index(n)
        highlight = true,                          // pill that fits the word
        highlightColor = Color(0xFFA855F7),
        pauseOnHover = true,
        state = state,                             // state.next() / previous() / jumpTo(i)
        onNext = { index -> },
    )
}
""".trimIndent()

private val Light = Color(0xFFE4E4E7)

val rotatingTextDemo = AnimationDemo(
    id = "rotating-text",
    name = "Rotating Text",
    description = "A list of phrases rotates in place with a sliding transition.",
    category = DemoCategory.Transition,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Light)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Make it ", style = style)
                RotatingText(
                    texts = listOf("Fast", "Smooth", "Native"),
                    style = style,
                    staggerMillis = 20,
                    highlight = true,
                )
            }
        }
    },
    screen = { RotatingTextScreen() },
    code = RotatingTextCode,
)

private val Effects = RotatingTextEffect.entries
private val Splits = listOf("Characters", "Words", "Lines")
private val Froms = listOf("First", "Last", "Center", "Random")

@Composable
private fun RotatingTextScreen() {
    var phrases by remember { mutableStateOf("Fast,Smooth,Native,Delightful") }
    var effect by remember { mutableStateOf(RotatingTextEffect.Slide) }
    var direction by remember { mutableStateOf(RotatingTextDirection.Up) }
    var split by remember { mutableStateOf("Characters") }
    var from by remember { mutableStateOf("First") }
    var interval by remember { mutableIntStateOf(2000) }
    var stagger by remember { mutableIntStateOf(25) }
    var blur by remember { mutableIntStateOf(0) }
    var loop by remember { mutableStateOf(true) }
    var auto by remember { mutableStateOf(true) }
    var hover by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(false) }
    var pill by remember { mutableStateOf(Color(0xFFA855F7)) }
    val state = rememberRotatingTextState()
    val texts = remember(phrases) { phrases.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Hello") } }
    val style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Light)

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Make it ", style = style)
                RotatingText(
                    texts = texts,
                    style = style,
                    effect = effect,
                    direction = direction,
                    splitBy = when (split) {
                        "Words" -> RotatingTextSplit.Words
                        "Lines" -> RotatingTextSplit.Lines
                        else -> RotatingTextSplit.Characters
                    },
                    rotationIntervalMillis = interval,
                    staggerMillis = stagger,
                    staggerFrom = when (from) {
                        "Last" -> RotatingTextStaggerFrom.Last
                        "Center" -> RotatingTextStaggerFrom.Center
                        "Random" -> RotatingTextStaggerFrom.Random
                        else -> RotatingTextStaggerFrom.First
                    },
                    loop = loop,
                    auto = auto,
                    pauseOnHover = hover,
                    blur = blur.dp,
                    highlight = highlight,
                    highlightColor = pill,
                    state = state,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NavButton("< Previous") { state.previous() }
            NavButton("Next >") { state.next() }
            NavButton("Reset") { state.reset() }
            Text("index ${state.currentIndex}", color = ShowcaseColors.TextSecondary, fontSize = 13.sp)
        }
        ControlPanel {
            TextControl("Phrases (comma separated)", phrases) { phrases = it }
            ChoiceControl("Effect", Effects, effect, { effect = it }) { it.name }
            ChoiceControl("Direction", RotatingTextDirection.entries, direction, { direction = it }) { it.name }
            ChoiceControl("Split by", Splits, split, { split = it })
            ChoiceControl("Stagger from", Froms, from, { from = it })
            IntSliderControl("Rotation interval (ms)", interval, { interval = it }, 500..5000)
            IntSliderControl("Stagger (ms per unit)", stagger, { stagger = it }, 0..150)
            IntSliderControl("Extra blur (dp)", blur, { blur = it }, 0..16)
            SwitchControl("Auto rotate", auto) { auto = it }
            SwitchControl("Loop", loop) { loop = it }
            SwitchControl("Pause on hover", hover) { hover = it }
            SwitchControl("Highlight pill", highlight) { highlight = it }
            ColorControl("Pill colour", pill, { pill = it })
        }
        CodeBlock(RotatingTextCode)
    }
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        color = ShowcaseColors.TextPrimary,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(shape)
            .background(ShowcaseColors.SurfaceRaised)
            .border(1.dp, ShowcaseColors.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
