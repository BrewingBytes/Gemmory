package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.copy.StringsXml
import java.io.File

/**
 * `./gradlew :shared:generateIosStrings` (T63, eng D9): writes the iOS app's `InfoPlist.xcstrings` from the app's
 * strings files, so the words iOS shows outside the app (its name under the icon; from Stage 2 the Photos permission
 * text) come from the same place as every other word. `IosStringsTest` fails when the committed file is stale.
 */
object IosStrings {
    /** Info.plist key → the string that fills it, in every language. */
    val INFO_PLIST: Map<String, Entry> = mapOf(
        "CFBundleDisplayName" to Entry("app_name", "The app's name under its icon on the Home Screen."),
    )

    class Entry(val name: String, val comment: String)

    /** `<strings folder> <InfoPlist.xcstrings>` */
    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args[1])
        out.writeText(infoPlist(File(args[0])))
        println("wrote $out")
    }

    /** The catalog Xcode would write for [INFO_PLIST]: keys sorted, two-space indent, `" : "` separators. */
    fun infoPlist(strings: File): String {
        val languages = strings.listFiles { f -> f.isDirectory && File(f, "strings.xml").isFile }!!
            .map { StringsXml.parse(StringsXml.tagOf(it.name), File(it, "strings.xml").readText()) }
            .sortedBy { it.tag }
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"sourceLanguage\" : \"en\",\n")
        sb.append("  \"strings\" : {\n")
        val keys = INFO_PLIST.keys.sorted()
        keys.forEachIndexed { k, key ->
            val entry = INFO_PLIST.getValue(key)
            sb.append("    ${json(key)} : {\n")
            sb.append("      \"comment\" : ${json(entry.comment)},\n")
            sb.append("      \"extractionState\" : \"manual\",\n")
            sb.append("      \"localizations\" : {\n")
            languages.forEachIndexed { l, copy ->
                sb.append("        ${json(copy.tag)} : {\n")
                sb.append("          \"stringUnit\" : {\n")
                sb.append("            \"state\" : \"translated\",\n")
                sb.append("            \"value\" : ${json(copy.text(entry.name))}\n")
                sb.append("          }\n")
                sb.append("        }").append(if (l < languages.lastIndex) ",\n" else "\n")
            }
            sb.append("      }\n")
            sb.append("    }").append(if (k < keys.lastIndex) ",\n" else "\n")
        }
        sb.append("  },\n")
        sb.append("  \"version\" : \"1.0\"\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun json(s: String): String = buildString {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
