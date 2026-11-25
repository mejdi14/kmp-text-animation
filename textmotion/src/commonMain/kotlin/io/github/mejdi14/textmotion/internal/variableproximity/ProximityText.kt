package io.github.mejdi14.textmotion.internal.variableproximity

import androidx.compose.ui.text.style.TextAlign
import io.github.mejdi14.textmotion.internal.containsRtl
import io.github.mejdi14.textmotion.internal.graphemes
import io.github.mejdi14.textmotion.internal.needsShapingContext

/**
 * Text split into proximity pieces. Letters (graphemes) react to the pointer; whitespace pieces
 * keep their resting width (in the reference spaces are separate, non-reacting spans). Words in
 * scripts whose shaping depends on neighbours (Arabic, Indic…) stay one piece so they keep their
 * joined forms; such a word reacts as a whole.
 */
internal class ProximityText(text: String) {
    val pieces: List<String>
    /** [KIND_LETTER], [KIND_SPACE] or [KIND_NEWLINE] per piece. */
    val kinds: IntArray
    /** Word index of each piece (-1 for whitespace). */
    val wordOf: IntArray
    /** First piece of each word. */
    val wordStart: IntArray
    /** End (exclusive) piece of each word. */
    val wordEnd: IntArray
    /** Whether each word runs right-to-left. */
    val wordRtl: BooleanArray
    /** Paragraph base direction from the first strong character (null when there is none). */
    val baseRtl: Boolean?

    init {
        val p = ArrayList<String>()
        val k = ArrayList<Int>()
        val w = ArrayList<Int>()
        val ws = ArrayList<Int>()
        val we = ArrayList<Int>()
        val wr = ArrayList<Boolean>()
        val word = StringBuilder()
        val wordGraphemes = ArrayList<String>()
        fun flushWord() {
            if (wordGraphemes.isEmpty()) return
            val s = word.toString()
            val index = ws.size
            ws += p.size
            if (needsShapingContext(s)) { p += s; k += KIND_LETTER; w += index }
            else for (g in wordGraphemes) { p += g; k += KIND_LETTER; w += index }
            we += p.size
            wr += containsRtl(s)
            word.clear(); wordGraphemes.clear()
        }
        for (g in graphemes(text)) {
            when {
                g == "\n" || g == "\r\n" || g == "\r" -> { flushWord(); p += "\n"; k += KIND_NEWLINE; w += -1 }
                g.isNotEmpty() && g.all { it.isWhitespace() } -> { flushWord(); p += g; k += KIND_SPACE; w += -1 }
                else -> { word.append(g); wordGraphemes += g }
            }
        }
        flushWord()
        pieces = p
        kinds = k.toIntArray()
        wordOf = w.toIntArray()
        wordStart = ws.toIntArray()
        wordEnd = we.toIntArray()
        wordRtl = wr.toBooleanArray()
        baseRtl = firstStrongRtl(text)
    }

    val size: Int get() = pieces.size

    fun isLetter(i: Int): Boolean = kinds[i] == KIND_LETTER

    companion object {
        const val KIND_LETTER: Int = 0
        const val KIND_SPACE: Int = 1
        const val KIND_NEWLINE: Int = 2

        /** Direction of the first strong character: true = RTL, false = LTR, null = none. */
        fun firstStrongRtl(text: CharSequence): Boolean? {
            for (ch in text) {
                val c = ch.code
                if (c in 0x0590..0x08FF || c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF) return true
                if (ch.isLetter()) return false
            }
            return null
        }
    }
}

/**
 * Lines fixed at the resting settings (the reference measures natural wrapping once with the
 * `from` settings, then freezes the breaks so swelling letters never reflow the paragraph).
 *
 * @property order piece indices of all lines in visual (left-to-right) order, concatenated.
 * @property lineStart start offset of each line in [order] (size = lines + 1).
 * @property restWidth resting width of each line.
 */
internal class ProximityLines(val order: IntArray, val lineStart: IntArray, val restWidth: FloatArray) {
    val count: Int get() = restWidth.size
    val maxRestWidth: Float get() = restWidth.maxOrNull() ?: 0f

    companion object {
        /**
         * Greedy word wrapping at [maxWidth] using resting widths. Breaks only between words (words
         * are unbreakable, like the reference's `nowrap` word spans) and at explicit newlines;
         * whitespace at a soft break is dropped. With [rtl] base direction each line is mirrored,
         * keeping left-to-right words' letters in reading order.
         */
        fun build(text: ProximityText, width: (Int) -> Float, maxWidth: Float, rtl: Boolean): ProximityLines {
            val order = ArrayList<Int>(text.size)
            val starts = ArrayList<Int>()
            val widths = ArrayList<Float>()
            val line = ArrayList<Int>()
            var lineW = 0f
            val pendingSpaces = ArrayList<Int>()
            var pendingW = 0f

            fun emit() {
                starts += order.size
                widths += lineW
                if (rtl) appendMirrored(text, line, order) else order.addAll(line)
                line.clear(); lineW = 0f
            }

            var i = 0
            while (i < text.size) {
                when (text.kinds[i]) {
                    ProximityText.KIND_NEWLINE -> {
                        line.addAll(pendingSpaces); lineW += pendingW
                        pendingSpaces.clear(); pendingW = 0f
                        emit()
                        i++
                    }
                    ProximityText.KIND_SPACE -> { pendingSpaces += i; pendingW += width(i); i++ }
                    else -> {
                        val wi = text.wordOf[i]
                        val end = text.wordEnd[wi]
                        var wordW = 0f
                        for (j in i until end) wordW += width(j)
                        val hasContent = line.isNotEmpty()
                        if (hasContent && lineW + pendingW + wordW > maxWidth + 0.5f) {
                            pendingSpaces.clear(); pendingW = 0f
                            emit()
                        }
                        line.addAll(pendingSpaces); lineW += pendingW
                        pendingSpaces.clear(); pendingW = 0f
                        for (j in i until end) line += j
                        lineW += wordW
                        i = end
                    }
                }
            }
            line.addAll(pendingSpaces); lineW += pendingW
            emit()
            starts += order.size
            return ProximityLines(order.toIntArray(), starts.toIntArray(), widths.toFloatArray())
        }

        /** Appends [line] (logical order) to [out] mirrored for a right-to-left paragraph. */
        private fun appendMirrored(text: ProximityText, line: List<Int>, out: MutableList<Int>) {
            var k = line.size - 1
            while (k >= 0) {
                val idx = line[k]
                val wi = text.wordOf[idx]
                if (wi < 0 || text.wordRtl[wi]) { out += idx; k--; continue }
                // A left-to-right word inside an RTL paragraph keeps its letters in reading order.
                var s = k
                while (s - 1 >= 0 && text.wordOf[line[s - 1]] == wi) s--
                for (m in s..k) out += line[m]
                k = s - 1
            }
        }

        /** Left offset of a line of width [lineWidth] inside [boxWidth] for [align]. */
        fun alignOffset(align: TextAlign, rtl: Boolean, boxWidth: Float, lineWidth: Float): Float {
            val free = boxWidth - lineWidth
            return when (align) {
                TextAlign.Left -> 0f
                TextAlign.Right -> free
                TextAlign.Center -> free / 2f
                TextAlign.End -> if (rtl) 0f else free
                else -> if (rtl) free else 0f // Start, Justify, Unspecified
            }
        }
    }
}
