package io.github.mejdi14.textmotion.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.rangeOfComposedCharacterSequenceAtIndex

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal actual fun platformGraphemeBoundaries(text: String): IntArray? = try {
    val ns = NSString.create(string = text)
    val length = ns.length.toInt()
    val out = ArrayList<Int>()
    var i = 0
    out.add(0)
    while (i < length) {
        val next = ns.rangeOfComposedCharacterSequenceAtIndex(i.toULong()).useContents { (location + this.length).toInt() }
        i = if (next > i) next else i + 1
        out.add(i)
    }
    out.toIntArray()
} catch (e: Throwable) {
    null
}
