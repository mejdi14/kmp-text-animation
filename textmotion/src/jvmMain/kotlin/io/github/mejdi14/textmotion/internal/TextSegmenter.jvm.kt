package io.github.mejdi14.textmotion.internal

import java.text.BreakIterator

/** JDK 20+ implements extended grapheme clusters (emoji ZWJ sequences) in BreakIterator. */
internal actual fun platformGraphemeBoundaries(text: String): IntArray? = try {
    val version = Runtime.version().feature()
    if (version < 20) null else {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val out = ArrayList<Int>()
        var b = it.first()
        while (b != BreakIterator.DONE) { out.add(b); b = it.next() }
        out.toIntArray()
    }
} catch (e: Throwable) {
    null
}
