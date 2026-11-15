package com.brewingbytes.gemmory.copy

/**
 * One language's copy read from its `values[-xx]/strings.xml` (T63, eng D23 / OV-6 option B): the files under
 * composeApp's `composeResources/files/strings/` are the one source of the app's words. Android reads them as resources
 * (`R.string`); iOS, which has no Android resources, reads them through this.
 *
 * Reads what our files use, the way aapt does: `<string>` and `<plurals>` with their `<item>`s, comments, the five
 * XML entities and numeric character references, and the backslash escapes `\'`, `\"`, `\n`, `\t`, `\@`, `\?`, `\\`
 * and `\uXXXX`. Formats `%1$s`, `%1$d`, `%s`, `%d` and `%%` as `String.format` does for our languages (Compose
 * resources show `%%` as it is, the Stage 0 finding that chose this reader). Refuses anything else, so a new kind of
 * markup fails a test instead of reaching a screen.
 */
class StringsXml(
    /** The language tag of the file ("en", "pt-BR"): its plural rules. */
    val tag: String,
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
) {
    val language: String get() = tag.substringBefore('-')

    /** The string [name] with [args] put in. */
    fun text(name: String, vararg args: Any): String {
        val raw = strings[name] ?: error("no string $name in $tag")
        return if (args.isEmpty()) raw else format(raw, args)
    }

    /** The `<plurals>` item [language] uses for [count], with [args] put in; a missing quantity falls back to "other". */
    fun plural(name: String, count: Int, vararg args: Any): String {
        val items = plurals[name] ?: error("no plurals $name in $tag")
        val raw = items[PluralRules.quantity(language, count)] ?: items["other"] ?: error("no other in plurals $name in $tag")
        return format(raw, args)
    }

    /** This language's copy with [base]'s for every name it lacks, as Android falls back to `values/`. */
    fun over(base: StringsXml): StringsXml = StringsXml(tag, base.strings + strings, base.plurals + plurals)

    companion object {
        /** `values` → "en", `values-pt-rBR` → "pt-BR". */
        fun tagOf(folder: String): String = folder.removePrefix("values").removePrefix("-").replace("-r", "-").ifEmpty { "en" }

        /** "en" → `values`, "pt-BR" → `values-pt-rBR`. */
        fun folderOf(tag: String): String = when (tag) {
            "en" -> "values"
            else -> "values-" + tag.replace("-", "-r")
        }

        /** Parses one `strings.xml` written in [tag]. */
        fun parse(tag: String, xml: String): StringsXml {
            val strings = LinkedHashMap<String, String>()
            val plurals = LinkedHashMap<String, Map<String, String>>()
            val reader = Reader(xml)
            reader.skipProlog()
            reader.expectOpen("resources")
            while (true) {
                reader.skipSpaceAndComments()
                if (reader.atClose("resources")) break
                val (tagName, attrs) = reader.open()
                val name = attrs["name"] ?: reader.fail("<$tagName> without a name")
                when (tagName) {
                    "string" -> strings[name] = unescape(reader.textUntilClose("string"))
                    "plurals" -> {
                        val items = LinkedHashMap<String, String>()
                        while (true) {
                            reader.skipSpaceAndComments()
                            if (reader.atClose("plurals")) break
                            val (itemTag, itemAttrs) = reader.open()
                            if (itemTag != "item") reader.fail("<$itemTag> in <plurals name=\"$name\">")
                            val quantity = itemAttrs["quantity"] ?: reader.fail("<item> without a quantity in $name")
                            items[quantity] = unescape(reader.textUntilClose("item"))
                        }
                        plurals[name] = items
                    }
                    else -> reader.fail("unmodelled <$tagName>")
                }
            }
            return StringsXml(tag, strings, plurals)
        }

        /** aapt's backslash escapes, for what our files use. */
        fun unescape(raw: String): String {
            require(raw == raw.trim() && !raw.startsWith("\"")) { "unmodelled whitespace or quotes: <$raw>" }
            val out = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c != '\\') {
                    out.append(c)
                    i++
                    continue
                }
                require(i + 1 < raw.length) { "a trailing backslash: <$raw>" }
                when (val next = raw[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> {
                        out.append(raw.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    '\'', '"', '@', '?', '\\' -> out.append(next)
                    else -> throw IllegalArgumentException("unmodelled escape \\$next: <$raw>")
                }
                i += 2
            }
            return out.toString()
        }

        /** `String.format` for our specifiers: `%n$s`, `%n$d`, `%s`, `%d`, `%%`. */
        fun format(raw: String, args: Array<out Any>): String {
            val out = StringBuilder(raw.length + 16)
            var next = 0
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c != '%') {
                    out.append(c)
                    i++
                    continue
                }
                require(i + 1 < raw.length) { "a trailing % in <$raw>" }
                if (raw[i + 1] == '%') {
                    out.append('%')
                    i += 2
                    continue
                }
                var j = i + 1
                while (j < raw.length && raw[j].isDigit()) j++
                val index = if (j > i + 1 && j < raw.length && raw[j] == '$') {
                    raw.substring(i + 1, j).toInt().also { j++ } - 1
                } else {
                    j = i + 1
                    next++
                }
                require(j < raw.length) { "a cut-off specifier in <$raw>" }
                require(index in args.indices) { "argument ${index + 1} missing for <$raw>" }
                val arg = args[index]
                when (raw[j]) {
                    's' -> out.append(arg.toString())
                    'd' -> {
                        require(arg is Int || arg is Long || arg is Short || arg is Byte) { "%d given ${arg::class.simpleName} in <$raw>" }
                        out.append(arg.toString())
                    }
                    else -> throw IllegalArgumentException("unmodelled specifier %${raw[j]} in <$raw>")
                }
                i = j + 1
            }
            return out.toString()
        }
    }

    /** Just enough of an XML reader for a strings file: no namespaces, no DTD, no mixed content inside a string. */
    private class Reader(private val s: String) {
        private var i = 0

        fun fail(why: String): Nothing = throw IllegalArgumentException("strings.xml at ${line()}: $why")

        private fun line(): Int = s.substring(0, i.coerceAtMost(s.length)).count { it == '\n' } + 1

        private fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun skipSpaceAndComments() {
            while (true) {
                skipSpace()
                if (s.startsWith("<!--", i)) {
                    val end = s.indexOf("-->", i + 4)
                    if (end < 0) fail("an unclosed comment")
                    i = end + 3
                } else {
                    return
                }
            }
        }

        fun skipProlog() {
            if (s.startsWith("﻿")) i = 1
            skipSpace()
            if (s.startsWith("<?xml", i)) {
                val end = s.indexOf("?>", i)
                if (end < 0) fail("an unclosed XML declaration")
                i = end + 2
            }
            skipSpaceAndComments()
        }

        fun expectOpen(tag: String) {
            val (name, _) = open()
            if (name != tag) fail("expected <$tag>, found <$name>")
        }

        fun atClose(tag: String): Boolean {
            val close = "</$tag"
            if (!s.startsWith(close, i)) return false
            i += close.length
            skipSpace()
            if (i >= s.length || s[i] != '>') fail("a broken </$tag>")
            i++
            return true
        }

        /** Reads `<name attr="v" …>` and returns its name and attributes. */
        fun open(): Pair<String, Map<String, String>> {
            if (i >= s.length || s[i] != '<' || s.startsWith("</", i)) fail("expected an opening tag")
            i++
            val name = word()
            val attrs = LinkedHashMap<String, String>()
            while (true) {
                skipSpace()
                if (i >= s.length) fail("an unclosed <$name>")
                if (s[i] == '>') {
                    i++
                    return name to attrs
                }
                if (s[i] == '/') fail("an empty <$name/>")
                val key = word()
                skipSpace()
                if (i >= s.length || s[i] != '=') fail("attribute $key without a value")
                i++
                skipSpace()
                val quote = s.getOrNull(i)
                if (quote != '"' && quote != '\'') fail("attribute $key not quoted")
                val end = s.indexOf(quote, i + 1)
                if (end < 0) fail("attribute $key unclosed")
                attrs[key] = entities(s.substring(i + 1, end))
                i = end + 1
            }
        }

        private fun word(): String {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "_-:.")) i++
            if (i == start) fail("expected a name")
            return s.substring(start, i)
        }

        /** The text up to `</tag>`, entities resolved; markup inside fails. */
        fun textUntilClose(tag: String): String {
            val close = "</$tag>"
            val end = s.indexOf(close, i)
            if (end < 0) fail("an unclosed <$tag>")
            val body = s.substring(i, end)
            if ('<' in body) fail("markup inside <$tag>: <$body>")
            i = end + close.length
            return entities(body)
        }

        private fun entities(text: String): String {
            if ('&' !in text) return text
            val out = StringBuilder(text.length)
            var k = 0
            while (k < text.length) {
                val c = text[k]
                if (c != '&') {
                    out.append(c)
                    k++
                    continue
                }
                val semi = text.indexOf(';', k)
                if (semi < 0) fail("a bare & in <$text>")
                val name = text.substring(k + 1, semi)
                when {
                    name == "amp" -> out.append('&')
                    name == "lt" -> out.append('<')
                    name == "gt" -> out.append('>')
                    name == "quot" -> out.append('"')
                    name == "apos" -> out.append('\'')
                    name.startsWith("#x") -> appendCodePoint(out, name.substring(2).toInt(16))
                    name.startsWith("#") -> appendCodePoint(out, name.substring(1).toInt())
                    else -> fail("unknown entity &$name;")
                }
                k = semi + 1
            }
            return out.toString()
        }

        private fun appendCodePoint(out: StringBuilder, cp: Int) {
            if (cp < 0x10000) {
                out.append(cp.toChar())
            } else {
                val v = cp - 0x10000
                out.append((0xD800 + (v shr 10)).toChar())
                out.append((0xDC00 + (v and 0x3FF)).toChar())
            }
        }
    }
}
