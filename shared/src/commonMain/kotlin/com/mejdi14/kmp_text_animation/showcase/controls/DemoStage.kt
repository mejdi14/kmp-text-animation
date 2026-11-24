package com.mejdi14.kmp_text_animation.showcase.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mejdi14.kmp_text_animation.showcase.ShowcaseColors

@Composable
fun DemoStage(
    modifier: Modifier = Modifier,
    minHeight: Dp = 220.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .clip(shape)
            .background(ShowcaseColors.Surface)
            .border(1.dp, ShowcaseColors.Border, shape)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
