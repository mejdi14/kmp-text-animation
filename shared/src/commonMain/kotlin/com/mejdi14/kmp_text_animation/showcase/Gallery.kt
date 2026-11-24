package com.mejdi14.kmp_text_animation.showcase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Gallery(onOpen: (AnimationDemo) -> Unit) {
    var filter by remember { mutableStateOf<DemoCategory?>(null) }
    val demos = DemoRegistry.demos.filter { filter == null || it.category == filter }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TextMotion", color = ShowcaseColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Text animations for Compose Multiplatform",
                    color = ShowcaseColors.TextSecondary,
                    fontSize = 15.sp,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Chips wrap by scrolling is avoided: few short labels fit most widths.
                FilterChipItem("All", filter == null) { filter = null }
                DemoCategory.entries.forEach { c ->
                    FilterChipItem(c.name, filter == c) { filter = c }
                }
            }
        }
        items(demos, key = { it.id }) { demo ->
            DemoCard(demo, onClick = { onOpen(demo) })
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) ShowcaseColors.Accent else ShowcaseColors.Surface)
            .border(BorderStroke(1.dp, if (selected) ShowcaseColors.Accent else ShowcaseColors.Border), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            maxLines = 1,
            color = if (selected) ShowcaseColors.TextPrimary else ShowcaseColors.TextSecondary,
        )
    }
}

@Composable
private fun DemoCard(demo: AnimationDemo, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShowcaseColors.Surface)
            .border(1.dp, ShowcaseColors.Border, shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(ShowcaseColors.Background)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            demo.preview()
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    demo.name,
                    color = ShowcaseColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!demo.implemented) {
                    Text(
                        "Soon",
                        color = ShowcaseColors.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ShowcaseColors.SurfaceRaised)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(demo.description, color = ShowcaseColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
