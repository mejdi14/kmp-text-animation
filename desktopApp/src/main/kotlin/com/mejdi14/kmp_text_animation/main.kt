package com.mejdi14.kmp_text_animation

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KMPTextAnimation",
    ) {
        App()
    }
}