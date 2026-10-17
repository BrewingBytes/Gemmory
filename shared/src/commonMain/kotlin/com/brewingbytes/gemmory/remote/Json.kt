package com.brewingbytes.gemmory.remote

/** A parsed JSON value. Numbers keep their text so integers are read exactly. */
sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json

    data class Arr(val items: List<Json>) : Json

    data class Str(val value: String) : Json

    data class Num(val text: String) : Json

    data class Bool(val value: Boolean) : Json

    data object Null : Json

    companion object {
        /** Deeper than any catalog; bounds the recursion a hostile file could ask for. */
        const val MAX_DEPTH = 16

        /** Strict RFC 8259 parse of the whole [text]; null when it isn't exactly one JSON value or a key repeats. */
        fun parse(text: String): Json? = try {
            Reader(text).run { value(0).also { skipSpace(); if (pos != text.length) fail() } }
        } catch (_: MalformedException) {
            null
        }

        /** [Json] back to text, keys in the order given, two-space indent: the publishing tool's stable output. */
        fun write(value: Json, indent: String = ""): String = when (value) {
            is Obj -> if (value.fields.isEmpty()) "{}" else value.fields.entries.joinToString(",\n", "{\n", "\n$indent}") {
                "$indent  ${quote(it.key)}: ${write(it.value, "$indent  ")}"
            }
            is Arr -> if (value.items.isEmpty()) "[]" else value.items.joinToString(",\n", "[\n", "\n$indent]") {
                "$indent  ${write(it, "$indent  ")}"
            }
            is Str -> quote(value.value)
            is Num -> value.text
            is Bool -> value.value.toString()
            Null -> "null"
        }

        private fun quote(s: String): String = buildString {
            append('"')
            for (c in s) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
            append('"')
        }
    }

    private class MalformedException : Exception()

    private class Reader(val text: String) {
        var pos = 0

        fun fail(): Nothing = throw MalformedException()

        fun skipSpace() {
            while (pos < text.length && text[pos] in " \t\n\r") pos++
        }

        fun value(depth: Int): Json {
            if (depth > MAX_DEPTH) fail()
            skipSpace()
            if (pos >= text.length) fail()
            return when (text[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> Str(string())
                't' -> literal("true", Bool(true))
                'f' -> literal("false", Bool(false))
                'n' -> literal("null", Null)
                else -> number()
            }
        }

        private fun literal(word: String, value: Json): Json {
            if (!text.startsWith(word, pos)) fail()
            pos += word.length
            return value
        }

        private fun obj(depth: Int): Obj {
            pos++
            val fields = LinkedHashMap<String, Json>()
            skipSpace()
            if (peek() == '}') return Obj(fields).also { pos++ }
            while (true) {
                skipSpace()
                if (peek() != '"') fail()
                val key = string()
                skipSpace()
                expect(':')
                if (fields.put(key, value(depth + 1)) != null) fail()
                skipSpace()
                when (next()) {
                    ',' -> continue
                    '}' -> return Obj(fields)
                    else -> fail()
                }
            }
        }

        private fun arr(depth: Int): Arr {
            pos++
            val items = ArrayList<Json>()
            skipSpace()
            if (peek() == ']') return Arr(items).also { pos++ }
            while (true) {
                items += value(depth + 1)
                skipSpace()
                when (next()) {
                    ',' -> continue
                    ']' -> return Arr(items)
                    else -> fail()
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = next()
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> when (next()) {
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail()
                            val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail()
                            out.append(code.toChar())
                            pos += 4
                        }
                        else -> fail()
                    }
                    c < ' ' -> fail()
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Num {
            val start = pos
            if (peek() == '-') pos++
            if (peek() == '0') {
                pos++
            } else {
                if (peek() !in '1'..'9') fail()
                while (peek() in '0'..'9') pos++
            }
            if (peek() == '.') {
                pos++
                if (peek() !in '0'..'9') fail()
                while (peek() in '0'..'9') pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++
                if (peek() == '+' || peek() == '-') pos++
                if (peek() !in '0'..'9') fail()
                while (peek() in '0'..'9') pos++
            }
            return Num(text.substring(start, pos))
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

        private fun next(): Char = if (pos < text.length) text[pos++] else fail()

        private fun expect(c: Char) {
            if (next() != c) fail()
        }
    }
}
