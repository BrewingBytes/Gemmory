package com.brewingbytes.gemmory.nav

import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.CanvasEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The navigation map (design D3): system back always goes one step up. */
class BackStackTest {
    private val photo = CanvasRef(CanvasKind.PHOTO, "photo-1")
    private val starter = CanvasRef(CanvasKind.BUNDLED, "starter")

    @Test
    fun galleryIsTheRootAndBackThereLeavesTheApp() {
        val stack = BackStack()
        assertEquals(listOf(Route.Gallery), stack.routes)
        assertFalse(stack.pop())
    }

    @Test
    fun photoFlowStepsBackOneAtATimeAndStartReplacesIt() {
        val stack = BackStack()
        stack.push(Route.Crop)
        stack.push(Route.Size)
        stack.push(Route.Preview)
        assertTrue(stack.pop())
        assertEquals(Route.Size, stack.top)
        stack.push(Route.Preview)
        stack.started(photo)
        assertEquals(listOf(Route.Gallery, Route.Canvas(photo)), stack.routes)
        assertTrue(stack.pop())
        assertEquals(Route.Gallery, stack.top)
    }

    @Test
    fun tilesOpenTheCanvasOrTheCompletionStill() = runTest {
        val stack = BackStack()
        stack.openTile(starter) { false }
        assertEquals(Route.Canvas(starter), stack.top)
        stack.pop()
        stack.openTile(starter) { true }
        assertEquals(Route.Completion(starter, fresh = false), stack.top)
    }

    @Test
    fun aTileOpensWhatTheCanvasIsNotWhatTheTileLastShowed() = runTest {
        // #50: the canvas was finished, but the gallery still drew it from before; it must open as the still.
        val stack = BackStack()
        val asked = mutableListOf<CanvasRef>()
        stack.openTile(photo) { asked += it; true }
        assertEquals(listOf(photo), asked)
        assertEquals(listOf(Route.Gallery, Route.Completion(photo, fresh = false)), stack.routes)
    }

    @Test
    fun nothingOpensIfThePlayerLeftWhileTheCanvasWasRead() = runTest {
        val stack = BackStack()
        val read = CompletableDeferred<Boolean>()
        val tap = launch(start = CoroutineStart.UNDISPATCHED) { stack.openTile(photo) { read.await() } }
        stack.push(Route.Settings)
        read.complete(false)
        tap.join()
        assertEquals(listOf(Route.Gallery, Route.Settings), stack.routes)
    }

    @Test
    fun finishingReplacesTheCanvasSoDoneAndBackGoToTheGallery() = runTest {
        val stack = BackStack()
        stack.openTile(starter) { false }
        stack.finished(starter)
        assertEquals(listOf(Route.Gallery, Route.Completion(starter, fresh = true)), stack.routes)
        stack.pop()
        assertEquals(listOf(Route.Gallery), stack.routes)
    }

    @Test
    fun finishedIsIgnoredUnlessThatCanvasIsOnTop() = runTest {
        val stack = BackStack()
        stack.openTile(photo) { false }
        stack.finished(starter)
        assertEquals(Route.Canvas(photo), stack.top)
    }

    @Test
    fun playAgainOpensTheCanvasWithTheGalleryBelow() = runTest {
        val stack = BackStack()
        stack.openTile(starter) { true }
        stack.playAgain(starter)
        assertEquals(listOf(Route.Gallery, Route.Canvas(starter)), stack.routes)
    }

    @Test
    fun settingsIsOneStepAboveTheGallery() {
        val stack = BackStack()
        stack.push(Route.Settings)
        stack.push(Route.Settings)
        assertEquals(listOf(Route.Gallery, Route.Settings), stack.routes)
    }

    @Test
    fun dropPhotoFlowLeavesTheRest() {
        val stack = BackStack(listOf(Route.Gallery, Route.Crop, Route.Size))
        stack.dropPhotoFlow()
        assertEquals(listOf(Route.Gallery), stack.routes)
    }

    @Test
    fun savedStateRoundTripsAndAFreshCompletionRestoresAsTheStill() {
        val stack = BackStack()
        stack.push(Route.Crop)
        stack.started(photo)
        stack.finished(photo)
        val saved = stack.routes.map(Route::encode)
        assertEquals(listOf("gallery", "completion:PHOTO:photo-1"), saved)
        assertEquals(listOf(Route.Gallery, Route.Completion(photo, fresh = false)), BackStack(saved.mapNotNull(Route::decode)).routes)
        for (r in listOf(Route.Settings, Route.Crop, Route.Size, Route.Preview, Route.Canvas(starter))) {
            assertEquals(r, Route.decode(Route.encode(r)))
        }
    }

    @Test
    fun unreadableSavedRoutesAreDroppedAndTheGalleryIsAlwaysTheRoot() {
        assertEquals(null, Route.decode("canvas:PHOTO:Bad/Id"))
        assertEquals(null, Route.decode("canvas:NOPE:x"))
        assertEquals(null, Route.decode("gallery:extra"))
        assertEquals(listOf(Route.Gallery, Route.Settings), BackStack(listOf(Route.Settings)).routes)
    }

    /** T36: the canvas keeps where it was opened from across process death; a route saved before T36 is the gallery's. */
    @Test
    fun theCanvasEntryIsSavedAndAnOldRouteReadsAsTheGallerys() = runTest {
        val stack = BackStack()
        stack.openTile(starter, CanvasEntry.TODAY_CARD) { false }
        assertEquals(Route.Canvas(starter, CanvasEntry.TODAY_CARD), stack.top)
        assertEquals(Route.Canvas(starter, CanvasEntry.TODAY_CARD), Route.decode(Route.encode(stack.top)))
        assertEquals(Route.Canvas(starter, CanvasEntry.GALLERY), Route.decode("canvas:BUNDLED:starter"))
        assertEquals(null, Route.decode("canvas:BUNDLED:starter:NOPE"))
        assertEquals(null, Route.decode("canvas:BUNDLED:starter:GALLERY:x"))
        // The last gem turns it into its completion whatever the entry.
        stack.finished(starter)
        assertEquals(Route.Completion(starter, fresh = true), stack.top)
    }
}
