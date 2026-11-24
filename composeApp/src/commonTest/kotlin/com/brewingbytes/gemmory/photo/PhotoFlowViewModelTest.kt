package com.brewingbytes.gemmory.photo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.SavedStateHandle
import com.brewingbytes.gemmory.app.AppGraph
import com.brewingbytes.gemmory.app.PlatformServices
import com.brewingbytes.gemmory.converter.FaceCheck
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.storage.KeyValueStore
import com.brewingbytes.gemmory.storage.MemoryFileStore
import com.brewingbytes.gemmory.storage.MemoryStore
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The photo flow's ViewModel in shared code (iOS Stage 1c, T64): the decode goes through the platform's
 * [PhotoDecoder] and the draft through the cache [FileStore]. These paths draw no pixels, so the test runs on the JVM
 * too; the decode, crop and convert with real photos stay in Android's connected tests (`StartSingleFlightTest`,
 * `MoreColorsFlowTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoFlowViewModelTest {
    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aPhotoThatCantBeOpenedSaysSoAndKeepsNoDraft() = runTest(main) {
        val services = FakeServices()
        val vm = PhotoFlowViewModel(AppGraph(services), SavedStateHandle())
        vm.onPhotoPicked("content://media/picker/7")
        val state = vm.state.first { !it.decoding }

        assertTrue(state.decodeFailed)
        assertEquals(null, state.nav)
        assertFalse(vm.hasDraft)
        assertEquals(listOf("content://media/picker/7" to 1024), services.decoded, "decoded straight to 1024 px on the long edge")
        vm.onDecodeMessageShown()
        assertFalse(vm.state.value.decodeFailed)
    }

    @Test
    fun aDraftClearedWhileTheAppWasDeadGoesBackToTheGallery() = runTest(main) {
        val services = FakeServices()
        val handle = SavedStateHandle(mapOf("draft" to "photo-1", "ratio" to "PORTRAIT"))
        val vm = PhotoFlowViewModel(AppGraph(services), handle)

        assertEquals(PhotoNav.Lost, vm.state.first { it.nav != null }.nav)
        assertFalse(vm.hasDraft, "the saved state is cleared with it")
        assertFalse(handle.contains("ratio"))
    }

    @Test
    fun leavingTheFlowDeletesTheDraftPhoto() = runTest(main) {
        val services = FakeServices()
        services.cache.mkdirs("photo-draft")
        services.cache.writeAtomic("photo-draft/photo-1.png", byteArrayOf(1))
        val vm = PhotoFlowViewModel(AppGraph(services), SavedStateHandle())
        vm.clearDraft()

        assertFalse(services.cache.exists("photo-draft"))
    }

    @Test
    fun theDefaultTitleIsMyPhotoWithTheDay() {
        val words = Words.load("en") { """<resources><string name="photo_default_title_dated">My photo · %1${'$'}s</string></resources>""" }
        // 2026-10-13T12:00:00Z: the 13th in every zone from UTC-11 to UTC+11.
        assertEquals("My photo · 13 Oct", PhotoTitles.default(words, 1_791_892_800_000))
    }

    private class FakeServices : PlatformServices {
        val decoded = mutableListOf<Pair<String, Int>>()
        override val cache = MemoryFileStore()
        private val stores = mutableMapOf<String, MemoryStore>()

        override fun keyValue(name: String): KeyValueStore = stores.getOrPut(name) { MemoryStore(name) }

        override val files: FileStore get() = error("not used")

        override val reporter = NonFatalReporter { }

        override fun nowMillis(): Long = 0L

        override fun words(): Words = error("not used")

        override val photos = object : PhotoDecoder {
            override fun decode(uri: String, maxEdgePx: Int): ImageBitmap? {
                decoded += uri to maxEdgePx
                return null
            }

            override fun crop(image: ImageBitmap, rect: IntRect) = error("not used")

            override fun jpeg(image: ImageBitmap, quality: Int) = error("not used")
        }

        override val faces = object : FaceFinder {
            override fun warmUp() = Unit

            override suspend fun check(photo: ImageBitmap) = FaceCheck.NO_FACE
        }

        override val analytics get() = error("not used")

        override val store get() = error("not used")

        override val timelapses get() = error("not used")

        override val completion get() = error("not used")

        override val play get() = error("not used")

        override val appScope get() = error("not used")

        override val remote get() = error("not used")

        override val todays get() = error("not used")

        override val collections get() = error("not used")

        override val streak get() = error("not used")

        override val access get() = error("not used")

        override val progress get() = error("not used")

        override fun progressSharing() = error("not used")
    }
}
