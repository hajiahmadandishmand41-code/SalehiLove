package com.salehilove.core.common

/**
 * Minimal, dependency-free JSON parser and writer used by the data layer and
 * the backup codec. Supports exactly the JSON grammar we produce plus a
 * lenient reader: objects (LinkedHashMap to preserve order), arrays, strings,
 * longs, doubles, booleans and null.
 *
 * The parser is a straightforward recursive-descent implementation with strict
 * input validation; the writer escapes control characters and quotes. Both are
 * unit-tested incl. round trips and Persian text.
 */
object Json {

    class JsonException(message: String) : Exception(message)

    // ---- Writing -------------------------------------------------------------

    fun write(value: Any?): String = StringBuilder().also { writeInto(value, it) }.toString()

    private fun writeInto(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(value, out)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long -> out.append(value.toString())
            is Double -> {
                if (value.isNaN() || value.isInfinite()) throw JsonException("cannot write non-finite number")
                out.append(value.toString())
            }
            is Float -> writeInto(value.toDouble(), out)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(k.toString(), out)
                    out.append(':')
                    writeInto(v, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) {
                    if (!first) out.append(',')
                    first = false
                    writeInto(v, out)
                }
                out.append(']')
            }
            is Array<*> -> writeInto(value.toList(), out)
            else -> throw JsonException("unsupported json value type ${value::class.java}")
        }
    }

    private fun writeString(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> {
                    if (c.code < 0x20) {
                        out.append("\\u")
                        out.append(String.format("%04x", c.code))
                    } else {
                        out.append(c)
                    }
                }
            }
        }
        out.append('"')
    }

    // ---- Parsing -------------------------------------------------------------

    fun parse(text: String): Any? {
        val p = Parser(text)
        val value = p.parseValue()
        p.skipWhitespace()
        if (!p.atEnd()) throw JsonException("trailing characters at ${p.pos}")
        return value
    }

    private class Parser(val text: String) {
        var pos = 0

        fun atEnd() = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw JsonException("unexpected end of input")
            return when (val c = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> {
                    if (c == '-' || c in '0'..'9') parseNumber()
                    else throw JsonException("unexpected character '$c' at $pos")
                }
            }
        }

        private fun parseLiteral(word: String, value: Any?): Any? {
            if (pos + word.length > text.length || text.substring(pos, pos + word.length) != word) {
                throw JsonException("invalid literal at $pos")
            }
            pos += word.length
            return value
        }

        private fun parseObject(): LinkedHashMap<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw JsonException("expected key string at $pos")
                val key = parseString()
                skipWhitespace()
                if (peek() != ':') throw JsonException("expected ':' at $pos")
                pos++
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw JsonException("expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseArray(): ArrayList<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw JsonException("expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("unterminated string")
                val c = text[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw JsonException("unterminated escape")
                        when (val e = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw JsonException("bad unicode escape")
                                val hex = text.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: throw JsonException("bad unicode escape '\\u$hex'")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonException("invalid escape '\\$e' at ${pos - 1}")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && text[pos] in '0'..'9') pos++
            var isDouble = false
            if (!atEnd() && text[pos] == '.') {
                isDouble = true
                pos++
                while (!atEnd() && text[pos] in '0'..'9') pos++
            }
            if (!atEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
                isDouble = true
                pos++
                if (!atEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
                while (!atEnd() && text[pos] in '0'..'9') pos++
            }
            val token = text.substring(start, pos)
            if (token.isEmpty() || token == "-") throw JsonException("invalid number at $start")
            return if (isDouble) {
                token.toDoubleOrNull() ?: throw JsonException("invalid number '$token'")
            } else {
                token.toLongOrNull() ?: (token.toDoubleOrNull() ?: throw JsonException("invalid number '$token'"))
            }
        }

        private fun expect(c: Char) {
            if (atEnd() || text[pos] != c) throw JsonException("expected '$c' at $pos")
            pos++
        }

        private fun peek(): Char {
            if (atEnd()) throw JsonException("unexpected end of input")
            return text[pos]
        }
    }
}
