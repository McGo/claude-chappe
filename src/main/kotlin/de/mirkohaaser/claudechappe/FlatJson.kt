package de.mirkohaaser.claudechappe

/**
 * Minimal reader for the flat JSON objects written by the hook script.
 *
 * Deliberately dependency-free: the plugin has to keep working across IDE
 * versions, and a bundled JSON library is exactly the kind of thing that
 * disappears from a distribution between releases. Nested objects and arrays
 * are not supported - the status files do not contain any.
 */
internal object FlatJson {

    fun parse(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var i = text.indexOf('{')
        if (i < 0) return result
        i++
        while (i < text.length) {
            when {
                text[i].isWhitespace() || text[i] == ',' -> i++
                text[i] == '}' -> return result
                text[i] == '"' -> {
                    val key = StringBuilder()
                    i = readString(text, i, key)
                    i = skipTo(text, i, ':')
                    if (i < 0) return result
                    val value = StringBuilder()
                    i = readValue(text, i + 1, value)
                    result[key.toString()] = value.toString()
                }
                // Unexpected token - bail out with whatever was read so far.
                else -> return result
            }
        }
        return result
    }

    private fun skipTo(text: String, from: Int, ch: Char): Int {
        var i = from
        while (i < text.length && text[i] != ch) i++
        return if (i < text.length) i else -1
    }

    /** [from] points at the opening quote; returns the index after the closing quote. */
    private fun readString(text: String, from: Int, out: StringBuilder): Int {
        var i = from + 1
        while (i < text.length) {
            when (val c = text[i]) {
                '\\' -> {
                    i++
                    if (i >= text.length) break
                    when (val esc = text[i]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            val hex = text.substring(i + 1, minOf(i + 5, text.length))
                            hex.toIntOrNull(16)?.let { out.append(it.toChar()) }
                            i += 4
                        }
                        else -> out.append(esc)
                    }
                    i++
                }
                '"' -> return i + 1
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return i
    }

    private fun readValue(text: String, from: Int, out: StringBuilder): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) return i
        if (text[i] == '"') return readString(text, i, out)
        while (i < text.length && text[i] != ',' && text[i] != '}') {
            out.append(text[i])
            i++
        }
        // Trim trailing whitespace of a bare number or literal.
        while (out.isNotEmpty() && out.last().isWhitespace()) out.setLength(out.length - 1)
        return i
    }
}
