package com.mejdi14.kmp_text_animation.showcase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.controls.CodeBlock

@Composable
fun DemoScreen(demo: AnimationDemo, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(shape)
                    .background(ShowcaseColors.Surface)
                    .border(BorderStroke(1.dp, ShowcaseColors.Border), shape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = ShowcaseColors.TextPrimary, fontSize = 18.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(demo.name, color = ShowcaseColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(demo.description, color = ShowcaseColors.TextSecondary, fontSize = 14.sp)
            }
        }

        // The demo's own controls and stage live inside screen().
        demo.screen()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Usage", color = ShowcaseColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            CodeBlock(demo.code)
        }
    }
}
