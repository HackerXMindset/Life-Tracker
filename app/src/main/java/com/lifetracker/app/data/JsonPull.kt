package com.lifetracker.app.data

import java.io.IOException
import java.io.Reader

/**
 * A small reader for JSON that goes through the text once, without loading it whole, so a file of hundreds of megabytes (a
 * Google Timeline export) can be read. It is forgiving: commas and colons are not checked. Plain code, so it can be tested.
 */
class JsonPull(private val reader: Reader) {
    enum class Token { BEGIN_OBJECT, END_OBJECT, BEGIN_ARRAY, END_ARRAY, NAME, STRING, NUMBER, BOOLEAN, NULL, END }

    private val buf = CharArray(8192)
    private var len = 0
    private var pos = 0
    private var peeked: Token? = null

    /** The text of the last NAME, STRING, NUMBER or BOOLEAN token. */
    var text: String = ""
        private set

    // For each open object: true while a key is expected next.
    private val keys = ArrayList<Boolean>()
    private val kinds = ArrayList<Char>()

    private fun read(): Int {
        if (pos >= len) {
            len = reader.read(buf, 0, buf.size)
            pos = 0
            if (len <= 0) {
                len = 0
                return -1
            }
        }
        return buf[pos++].code
    }

    private fun back() {
        if (pos > 0) pos--
    }

    private fun skipSpace(): Int {
        while (true) {
            val c = read()
            if (c == -1) return -1
            if (c == 0xFEFF) continue
            if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code || c == ','.code || c == ':'.code) continue
            return c
        }
    }

    private fun valueDone() {
        if (kinds.isNotEmpty() && kinds.last() == '{') keys[keys.size - 1] = true
    }

    /** Looks at the next token without taking it. */
    fun peek(): Token {
        peeked?.let { return it }
        val t = advance()
        peeked = t
        return t
    }

    /** Takes the next token. */
    fun next(): Token {
        val t = peek()
        peeked = null
        return t
    }

    private fun advance(): Token {
        val c = skipSpace()
        if (c == -1) return Token.END
        return when (c.toChar()) {
            '{' -> {
                kinds += '{'
                keys += true
                Token.BEGIN_OBJECT
            }
            '}' -> {
                if (kinds.isNotEmpty()) {
                    kinds.removeAt(kinds.size - 1)
                    keys.removeAt(keys.size - 1)
                }
                valueDone()
                Token.END_OBJECT
            }
            '[' -> {
                kinds += '['
                keys += false
                Token.BEGIN_ARRAY
            }
            ']' -> {
                if (kinds.isNotEmpty()) {
                    kinds.removeAt(kinds.size - 1)
                    keys.removeAt(keys.size - 1)
                }
                valueDone()
                Token.END_ARRAY
            }
            '"' -> {
                text = readString()
                if (kinds.isNotEmpty() && kinds.last() == '{' && keys.last()) {
                    keys[keys.size - 1] = false
                    Token.NAME
                } else {
                    valueDone()
                    Token.STRING
                }
            }
            else -> {
                val sb = StringBuilder()
                sb.append(c.toChar())
                while (true) {
                    val d = read()
                    if (d == -1) break
                    val ch = d.toChar()
                    if (ch == ',' || ch == '}' || ch == ']' || ch == ':' || ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                        back()
                        break
                    }
                    sb.append(ch)
                }
                text = sb.toString()
                valueDone()
                when (text) {
                    "true", "false" -> Token.BOOLEAN
                    "null" -> Token.NULL
                    else -> Token.NUMBER
                }
            }
        }
    }

    private fun readString(): String {
        val sb = StringBuilder()
        while (true) {
            val c = read()
            if (c == -1) throw IOException("The file ends in the middle of a text.")
            when (c.toChar()) {
                '"' -> return sb.toString()
                '\\' -> {
                    val e = read()
                    when (e.toChar()) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'u' -> {
                            var code = 0
                            repeat(4) {
                                val h = read()
                                code = code * 16 + (Character.digit(if (h == -1) '0' else h.toChar(), 16).coerceAtLeast(0))
                            }
                            sb.append(code.toChar())
                        }
                        else -> if (e != -1) sb.append(e.toChar())
                    }
                }
                else -> sb.append(c.toChar())
            }
        }
    }

    /** Reads an object: calls [member] for each name; it must take exactly one value (or call [skip]). Anything else is skipped. */
    fun obj(member: (String) -> Unit) {
        if (peek() != Token.BEGIN_OBJECT) {
            skip()
            return
        }
        next()
        while (true) {
            when (peek()) {
                Token.END_OBJECT -> {
                    next()
                    return
                }
                Token.END -> return
                Token.NAME -> {
                    next()
                    member(text)
                }
                else -> skip()
            }
        }
    }

    /** Reads an array: calls [item] for each element; it must take exactly one value. Anything else is skipped. */
    fun arr(item: () -> Unit) {
        if (peek() != Token.BEGIN_ARRAY) {
            skip()
            return
        }
        next()
        while (true) {
            when (peek()) {
                Token.END_ARRAY -> {
                    next()
                    return
                }
                Token.END -> return
                else -> item()
            }
        }
    }

    /** Skips one value, however deep. */
    fun skip() {
        var depth = 0
        while (true) {
            when (next()) {
                Token.BEGIN_OBJECT, Token.BEGIN_ARRAY -> depth++
                Token.END_OBJECT, Token.END_ARRAY -> depth--
                Token.END -> return
                else -> Unit
            }
            if (depth <= 0) return
        }
    }

    /** The next value as text (strings, numbers, true/false), or null for null or for an object or array (which is skipped). */
    fun str(): String? = when (peek()) {
        Token.STRING, Token.NUMBER, Token.BOOLEAN -> {
            next()
            text
        }
        else -> {
            skip()
            null
        }
    }

    fun dbl(): Double? = str()?.toDoubleOrNull()
}
