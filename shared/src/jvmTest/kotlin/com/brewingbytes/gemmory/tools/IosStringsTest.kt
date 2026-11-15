package com.brewingbytes.gemmory.tools

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS app's InfoPlist.xcstrings is what the strings files make it (T63): rerun `:shared:generateIosStrings`. */
class IosStringsTest {
    private val strings = AndroidStringsFile.RES
    private val committed = File("../iosApp/iosApp/InfoPlist.xcstrings")

    @Test
    fun theCommittedCatalogIsUpToDate() {
        assertEquals(IosStrings.infoPlist(strings), committed.readText(), "stale: run ./gradlew :shared:generateIosStrings")
    }

    @Test
    fun everyLanguageTheAppOffersIsInTheCatalog() {
        val plist = File("../iosApp/iosApp/Info.plist").readText()
        val offered = Regex("""<key>CFBundleLocalizations</key>\s*<array>(.*?)</array>""", RegexOption.DOT_MATCHES_ALL)
            .find(plist)!!.groupValues[1].let { Regex("<string>([^<]+)</string>").findAll(it).map { m -> m.groupValues[1] }.toSet() }
        val text = committed.readText()
        assertEquals(12, offered.size)
        offered.forEach { assertTrue("\"$it\" : {" in text, "$it missing from InfoPlist.xcstrings") }
    }
}
