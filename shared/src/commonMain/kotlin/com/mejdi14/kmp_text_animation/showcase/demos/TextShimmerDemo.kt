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
import com.mejdi14.kmp_text_animation.showcase.controls.CodeBlock
import com.mejdi14.kmp_text_animation.showcase.controls.ColorControl
import com.mejdi14.kmp_text_animation.showcase.controls.ControlPanel
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.controls.DurationControl
import com.mejdi14.kmp_text_animation.showcase.controls.PlaybackControls
import com.mejdi14.kmp_text_animation.showcase.controls.SliderControl
import com.mejdi14.kmp_text_animation.showcase.controls.TextControl
import io.github.mejdi14.textmotion.TextShimmer
import io.github.mejdi14.textmotion.rememberTextAnimationController

private val TextShimmerCode = """
TextShimmer(
    text = "Generating response…",
    style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    durationMillis = 2000,
    spread = 2f,
    baseColor = Color(0xFF71717A),
    highlightColor = Color.White,
)
""".trimIndent()

private val Base = Color(0xFF71717A)

val textShimmerDemo = AnimationDemo(
    id = "text-shimmer",
    name = "Text Shimmer",
    description = "A soft band of light sweeps across the text on a loop.",
    category = DemoCategory.Continuous,
    implemented = true,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextShimmer(
                "Text Shimmer",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                baseColor = Base, highlightColor = Color.White,
            )
        }
    },
    screen = { TextShimmerScreen() },
    code = TextShimmerCode,
)

@Composable
private fun TextShimmerScreen() {
    var text by remember { mutableStateOf("Generating response…") }
    var duration by remember { mutableIntStateOf(2000) }
    var spread by remember { mutableFloatStateOf(2f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var base by remember { mutableStateOf(Base) }
    var highlight by remember { mutableStateOf(Color.White) }
    val controller = rememberTextAnimationController()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoStage {
            TextShimmer(
                text,
                style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
                durationMillis = duration, spread = spread, baseColor = base, highlightColor = highlight,
                speed = speed, controller = controller,
            )
        }
        PlaybackControls(controller)
        ControlPanel {
            TextControl("Text", text) { text = it }
            DurationControl("Duration", duration, { duration = it }, 500..6000)
            SliderControl("Spread (dp per character)", spread, { spread = it }, 0.5f..8f)
            SliderControl("Speed", speed, { speed = it }, 0.25f..3f)
            ColorControl("Base colour", base, { base = it })
            ColorControl("Highlight colour", highlight, { highlight = it })
        }
        CodeBlock(TextShimmerCode)
    }
}
