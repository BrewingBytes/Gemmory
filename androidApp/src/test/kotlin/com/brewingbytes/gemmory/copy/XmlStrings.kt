package com.brewingbytes.gemmory.copy

import com.brewingbytes.gemmory.R
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [Strings] read from `res/values[-xx]/strings.xml` on disk (T48, #135): JVM tests have no Android resources (no
 * Robolectric), so the copy tests run against the real XML and pin the English word for word. Ids map to names through
 * the generated `R` class. Unescapes the way aapt does for what our file uses (`\'`, `\"`, `\n`, `\@`, `\?`, `\\`,
 * `\uXXXX`) and refuses anything it doesn't model (leading or trailing spaces, quoted strings).
 */
class XmlStrings(folder: String = "values", override val locale: Locale = Locale.ENGLISH) : Strings {
    val strings: Map<String, String>
    val plurals: Map<String, Map<String, String>>

    init {
        val root = parse(File("src/main/res/$folder/strings.xml"))
        strings = root.children("string").associate { it.getAttribute("name") to unescape(it.textContent) }
        plurals = root.children("plurals").associate { p ->
            p.getAttribute("name") to p.children("item").associate { it.getAttribute("quantity") to unescape(it.textContent) }
        }
    }

    override fun text(id: Int, vararg args: Any): String {
        val raw = strings[name(R.string::class.java, id)] ?: error("no string ${name(R.string::class.java, id)} in $locale")
        return if (args.isEmpty()) raw else String.format(locale, raw, *args)
    }

    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val name = name(R.plurals::class.java, id)
        val items = plurals[name] ?: error("no plurals $name in $locale")
        // English rules (the only language until T49): "one" for exactly 1, "other" for everything else.
        val raw = (if (count == 1) items["one"] else null) ?: items.getValue("other")
        return String.format(locale, raw, *args)
    }

    companion object {
        /** The English copy, read once. */
        val en: XmlStrings by lazy { XmlStrings() }

        private val names = HashMap<Pair<Class<*>, Int>, String>()

        fun name(type: Class<*>, id: Int): String = synchronized(names) {
            names.getOrPut(type to id) {
                type.fields.firstOrNull { it.getInt(null) == id }?.name ?: error("no ${type.simpleName} id $id")
            }
        }

        fun parse(file: File): Element = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

        fun Element.children(tag: String): List<Element> {
            val nodes = getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.parentNode == this || tag == "item" }
        }

        fun unescape(raw: String): String {
            require(raw == raw.trim() && !raw.startsWith("\"")) { "unmodelled whitespace or quotes: <$raw>" }
            val out = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c != '\\') {
                    out.append(c)
                    i++
                    continue
                }
                val next = raw[i + 1]
                when (next) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> {
                        out.append(raw.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    else -> out.append(next)
                }
                i += 2
            }
            return out.toString()
        }
    }
}
