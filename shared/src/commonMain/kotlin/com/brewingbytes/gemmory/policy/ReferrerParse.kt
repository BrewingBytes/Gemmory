package com.brewingbytes.gemmory.policy

/** The `utm_*` fields of a Play install referrer (T46); each null when absent or not a plain token. */
data class Referrer(val source: String?, val medium: String?, val campaign: String?, val content: String?) {
    companion object {
        val NONE = Referrer(null, null, null, null)
    }
}

/**
 * Reads Play's install referrer (T46, for T50's picture id): `utm_source=share&utm_medium=bundled&…`, the value of
 * `ShareLink.url`'s `referrer` parameter once Play has decoded it. Only the four `utm_*` keys are kept, each value
 * percent-decoded once and kept only when it's a plain token ([A-Za-z0-9._-], at most [MAX_VALUE] chars, like a
 * picture id); anything else, a crafted string included, is dropped. Input past [MAX_INPUT] chars is ignored.
 */
object ReferrerParse {
    const val MAX_INPUT = 1024
    const val MAX_VALUE = 100

    private val TOKEN = Regex("[A-Za-z0-9._-]{1,$MAX_VALUE}")

    fun parse(raw: String?): Referrer {
        if (raw.isNullOrEmpty()) return Referrer.NONE
        val fields = HashMap<String, String>()
        for (pair in raw.take(MAX_INPUT).split('&')) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val key = pair.substring(0, eq)
            if (key !in KEYS || key in fields) continue
            val value = decode(pair.substring(eq + 1)) ?: continue
            if (TOKEN.matches(value)) fields[key] = value
        }
        return Referrer(fields[SOURCE], fields[MEDIUM], fields[CAMPAIGN], fields[CONTENT])
    }

    /** Percent-decoding (and `+` as a space) once; null when an escape is malformed. */
    private fun decode(value: String): String? {
        if ('%' !in value && '+' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '+' -> out.append(' ')
                c == '%' -> {
                    if (i + 2 >= value.length) return null
                    val code = value.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                    out.append(code.toChar())
                    i += 2
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private const val SOURCE = "utm_source"
    private const val MEDIUM = "utm_medium"
    private const val CAMPAIGN = "utm_campaign"
    private const val CONTENT = "utm_content"
    private val KEYS = setOf(SOURCE, MEDIUM, CAMPAIGN, CONTENT)
}
