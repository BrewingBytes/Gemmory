package com.brewingbytes.gemmory.clips

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** #152 review: a clip's before/after labels are in its language ("After" was English in every language). */
@RunWith(AndroidJUnit4::class)
class ClipStillsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bothLabelsFollowTheLanguage() {
        assertEquals("Before" to "After", ClipStills.labels(ClipLanguages.context(context, "en")))
        assertEquals("Vorher" to "Nachher", ClipStills.labels(ClipLanguages.context(context, "de")))
    }
}
