package com.brewingbytes.gemmory.copy

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * T48 (#135): no English left in the code. Every word a player sees lives in `res/values/strings.xml`, so T49 can
 * translate it; this fails on a string literal that reads like words (two words, or one capitalized word) in the app's
 * main sources and in `shared/commonMain`. Logs, errors, test tags, preference keys and the like are left out by the
 * line they're on; the few literals that are words but never shown are listed in [ALLOWED] with why. The debug-only
 * founder clip tool (`src/debug`) isn't checked.
 */
class NoHardcodedCopyTest {
    @Test
    fun noUserFacingEnglishInTheAppsCode() {
        val found = scan(File("src/main/kotlin")) + scan(File("../shared/src/commonMain/kotlin"))
        assertEquals(emptyList(), found, "move these into res/values/strings.xml (or add a line to ALLOWED with why)")
    }

    private fun scan(root: File): List<String> = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
        val path = file.relativeTo(root).invariantSeparatorsPath
        if (ALLOWED_FILES.any { path.endsWith(it) }) return@flatMap emptySequence()
        // Block comments and raw strings (shader code) out, keeping the line numbers.
        val code = file.readText()
            .replace(BLOCK_COMMENT) { "\n".repeat(it.value.count { c -> c == '\n' }) }
            .replace(RAW_STRING) { "\n".repeat(it.value.count { c -> c == '\n' }) }
        code.lines().asSequence().withIndex().flatMap { (i, line) ->
            val noComment = withoutLineComment(line)
            if (SKIP_LINE.containsMatchIn(noComment)) return@flatMap emptySequence()
            LITERAL.findAll(noComment).map { it.groupValues[1] }
                .filter { literal ->
                    val words = TEMPLATE.replace(literal, "").trim()
                    WORDS.containsMatchIn(words) && literal !in ALLOWED && !HEX.matches(words)
                }
                .map { "$path:${i + 1}: \"$it\"" }
        }
    }.toList()

    /** The line without a `//` comment, leaving a `//` inside a string (a URL) alone. */
    private fun withoutLineComment(line: String): String {
        var inString = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && inString -> i++
                c == '"' -> inString = !inString
                !inString && c == '/' && line.getOrNull(i + 1) == '/' -> return line.substring(0, i)
            }
            i++
        }
        return line
    }

    companion object {
        private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val RAW_STRING = Regex("\"\"\".*?\"\"\"", RegexOption.DOT_MATCHES_ALL)
        private val LITERAL = Regex(""""((?:\\.|[^"\\])*)"""")
        private val TEMPLATE = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")

        /** Two words, or one capitalized word ("Share", "Done."). */
        private val WORDS = Regex("""[A-Za-z]+['’]?[A-Za-z]* +[A-Za-z]+|^[A-Z][a-z]+[.!?…]?$""")
        private val HEX = Regex("""[0-9a-f ]+""")

        /** Lines whose literals are never shown: logs, errors and reports, tags, keys, fonts, vector and GL names. */
        private val SKIP_LINE = Regex(
            listOf(
                """testTag\(""", """Tag\(""", """_TAG\b""", """\bTAG\b""", """Log\.[a-z]\(""", """\blog\(""",
                """error\(""", """require\(""", """check\(""", """checkNotNull""", """Exception\(""", """report""",
                """toString\(\)""", """label = """", """@Suppress""", """fontVariationSettings""", """fontFeatureSettings""",
                """ImageVector\.Builder\(""", """SemanticsPropertyKey""", """glGet""", """RequestProperty""", """getHeaderField""",
                """Regex\(""", """IAB""", """failed\(""",
            ).joinToString("|"),
        )

        /** Never shown, though they read like words. */
        private val ALLOWED = setOf(
            // AtomicFiles: matched against the system's error message to tell a full disk.
            "No space left",
            // PhotoTitles: a date pattern, formatted in the app's language.
            "d MMM",
            // Reminders: a time skeleton ("Hm" / "hm"), formatted in the app's language.
            "Hm",
            // RemotePictures: a non-fatal report's reason.
            "disk full",
            "write failed",
            // RemoteCatalog (shared): why a catalog was refused, logged and reported only.
            "too large",
            "not a JSON object",
        )

        /** Whole files left out: ReleaseAdIds' messages are the release build's own checks, never on a phone. */
        private val ALLOWED_FILES = listOf("policy/ReleaseAdIds.kt")
    }
}
