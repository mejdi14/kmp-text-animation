package com.mejdi14.kmp_text_animation

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform