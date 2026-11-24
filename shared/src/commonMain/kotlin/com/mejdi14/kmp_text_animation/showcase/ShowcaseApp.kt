package com.mejdi14.kmp_text_animation.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

object ShowcaseColors {
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF111115)
    val SurfaceRaised = Color(0xFF16161B)
    val Border = Color(0xFF1F1F25)
    val Accent = Color(0xFF8B5CF6)
    val TextPrimary = Color(0xFFFAFAFA)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextMuted = Color(0xFF6B6B76)
}

private val ShowcaseColorScheme = darkColorScheme(
    primary = ShowcaseColors.Accent,
    onPrimary = Color.White,
    background = ShowcaseColors.Background,
    onBackground = ShowcaseColors.TextPrimary,
    surface = ShowcaseColors.Surface,
    onSurface = ShowcaseColors.TextPrimary,
    surfaceVariant = ShowcaseColors.SurfaceRaised,
    onSurfaceVariant = ShowcaseColors.TextSecondary,
    outline = ShowcaseColors.Border,
    outlineVariant = ShowcaseColors.Border,
)

@Composable
fun ShowcaseApp() {
    MaterialTheme(colorScheme = ShowcaseColorScheme) {
        var selectedId by remember { mutableStateOf<String?>(null) }
        Box(
            Modifier
                .fillMaxSize()
                .background(ShowcaseColors.Background)
                .safeDrawingPadding(),
        ) {
            val demo = selectedId?.let { DemoRegistry.byId(it) }
            if (demo == null) {
                Gallery(onOpen = { selectedId = it.id })
            } else {
                DemoScreen(demo = demo, onBack = { selectedId = null })
            }
        }
    }
}
