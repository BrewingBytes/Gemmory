package com.brewingbytes.gemmory.tools

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One `res/values[-xx]/strings.xml` of the app, read the way aapt reads ours (T49): every `<string>` and every
 * `<plurals>` item, unescaped, keyed by name (`name` or `name/quantity`). The app's own `XmlStrings` does the same for
 * its JVM tests; this one serves `:shared`'s tools tests, which can't see the app's classes.
 */
class AndroidStringsFile(val folder: File) {
    /** `values` → en, `values-pt-rBR` → pt-BR. */
    val tag: String = folder.name.removePrefix("values").removePrefix("-").replace("-r", "-").ifEmpty { "en" }

    val language: String get() = tag.substringBefore('-')

    val texts: Map<String, String> by lazy {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(folder, "strings.xml")).documentElement
        val out = LinkedHashMap<String, String>()
        root.elements("string").forEach { out[it.getAttribute("name")] = unescape(it.textContent) }
        root.elements("plurals").forEach { p ->
            p.elements("item").forEach { out["${p.getAttribute("name")}/${it.getAttribute("quantity")}"] = unescape(it.textContent) }
        }
        out
    }

    companion object {
        val RES = File("../androidApp/src/main/res")

        /** Every language's strings file, English first. */
        fun all(): List<AndroidStringsFile> = RES.listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
            .sortedBy { it.name }
            .map(::AndroidStringsFile)

        private fun Element.elements(tag: String): List<Element> {
            val nodes = childNodes
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == tag }
        }

        fun unescape(raw: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c != '\\' || i + 1 >= raw.length) {
                    out.append(c)
                    i++
                    continue
                }
                when (val next = raw[i + 1]) {
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
