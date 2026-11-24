package com.mejdi14.kmp_text_animation.showcase.demos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.AnimationDemo
import com.mejdi14.kmp_text_animation.showcase.DemoCategory
import com.mejdi14.kmp_text_animation.showcase.controls.DemoStage
import com.mejdi14.kmp_text_animation.showcase.ShowcaseColors

fun placeholderDemo(
    id: String,
    name: String,
    description: String,
    category: DemoCategory,
): AnimationDemo = AnimationDemo(
    id = id,
    name = name,
    description = description,
    category = category,
    implemented = false,
    preview = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(name, color = ShowcaseColors.TextMuted, fontSize = 26.sp)
        }
    },
    screen = {
        DemoStage(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Coming soon", color = ShowcaseColors.TextMuted, fontSize = 20.sp)
        }
    },
    code = "// Coming soon",
)
