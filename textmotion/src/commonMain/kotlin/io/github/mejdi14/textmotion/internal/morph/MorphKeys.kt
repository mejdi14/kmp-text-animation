package io.github.mejdi14.textmotion.internal.morph

/**
 * Identity of each piece, as in Motion Primitives' TextMorph: `"<lowercased piece><n>"` where n
 * is the 1-based occurrence of that (lowercased) piece so far, e.g. "hello" → h1 e1 l1 l2 o1.
 * The original splits UTF-16 units; TextMotion splits grapheme clusters (or words for scripts
 * that need shaping context), which yields the same keys for BMP text without combining marks.
 *
 * @param prefix distinguishes granularities so a character never matches a word.
 */
internal fun morphKeys(pieces: List<String>, ignoreCase: Boolean = true, prefix: String = ""): List<String> {
    val counts = HashMap<String, Int>()
    return pieces.map { piece ->
        val id = if (ignoreCase) piece.lowercase() else piece
        val n = (counts[id] ?: 0) + 1
        counts[id] = n
        "$prefix$id\u0000$n"
    }
}

/**
 * For each key of [next], the index of the same key in [previous] or -1 when it is new.
 * Keys are unique within each list (guaranteed by [morphKeys]).
 */
internal fun matchKeys(previous: List<String>, next: List<String>): IntArray {
    val index = HashMap<String, Int>(previous.size * 2)
    previous.forEachIndexed { i, k -> index[k] = i }
    return IntArray(next.size) { index[next[it]] ?: -1 }
}
