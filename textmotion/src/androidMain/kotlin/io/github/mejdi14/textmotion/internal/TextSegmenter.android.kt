package io.github.mejdi14.textmotion.internal

import android.icu.text.BreakIterator

internal actual fun platformGraphemeBoundaries(text: String): IntArray? = try {
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    val out = ArrayList<Int>()
    var b = it.first()
    while (b != BreakIterator.DONE) { out.add(b); b = it.next() }
    out.toIntArray()
} catch (e: Throwable) {
    null
}
