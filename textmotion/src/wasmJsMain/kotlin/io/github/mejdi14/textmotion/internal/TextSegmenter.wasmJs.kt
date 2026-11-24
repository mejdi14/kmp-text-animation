package io.github.mejdi14.textmotion.internal

/** Uses Intl.Segmenter (all evergreen browsers); returns null when it is unavailable. */
internal actual fun platformGraphemeBoundaries(text: String): IntArray? {
    val raw = segmentGraphemes(text)
    if (raw.isEmpty()) return null
    val starts = raw.split(',').map { it.toInt() }
    return (starts + text.length).toIntArray()
}

private fun segmentGraphemes(text: String): String = js("(function(t){try{if(typeof Intl===\"undefined\"||!Intl.Segmenter)return \"\";var s=new Intl.Segmenter(undefined,{granularity:\"grapheme\"});var o=[];var it=s.segment(t)[Symbol.iterator]();var r=it.next();while(!r.done){o.push(r.value.index);r=it.next();}return o.join(\",\");}catch(e){return \"\";}})(text)")
