package com.brewingbytes.gemmory.telemetry

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Keeps file paths and URIs out of what reaches Crashlytics (AGENTS.md: nothing personal leaves the phone). A
 * platform exception's message can name a file (`/data/user/0/…/canvases/photo/…`) or the picked photo's
 * `content://` URI; those become `<path>` and `<uri>`.
 */
object Redaction {
    private val URI = Regex("""\b(?:content|file)://\S+""")

    /** An absolute path with at least two segments, not glued to a word ("BUNDLED/starter" stays). */
    private val PATH = Regex("""(?<![\w./:])/[^\s/'":,()\[\]]+/[^\s'":,()\[\]]*""")

    fun scrub(message: String): String = PATH.replace(URI.replace(message, "<uri>"), "<path>")

    /**
     * [error] as is when no message in its cause chain names a path or URI. Otherwise a copy of the chain: each link
     * keeps its stack trace and names its original class in a scrubbed message.
     */
    fun sanitize(error: Throwable): Throwable {
        val chain = mutableListOf<Throwable>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var t: Throwable? = error
        while (t != null && seen.add(t) && chain.size < MAX_DEPTH) {
            chain += t
            t = t.cause
        }
        if (chain.none { e -> e.message?.let { scrub(it) != it } == true }) return error
        return chain.foldRight(null as Throwable?) { e, cause -> RedactedException(e, cause) }!!
    }

    private const val MAX_DEPTH = 16
}

/** A scrubbed copy of [original] for Crashlytics, keeping its stack trace and naming its class. */
class RedactedException(original: Throwable, cause: Throwable?) :
    Exception(original.javaClass.name + original.message?.let { ": " + Redaction.scrub(it) }.orEmpty(), cause) {
    init {
        stackTrace = original.stackTrace
    }
}
