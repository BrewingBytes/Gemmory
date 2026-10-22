package com.brewingbytes.gemmory.gallery

import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue

/** The small photo canvas [seedFirstFinish] finishes. */
val FIRST_FINISH = CanvasRef(CanvasKind.PHOTO, "first-finish")

/**
 * The player's first finish (T45, DR4), for tests of what waits for it (the Today card, the Tomorrow line): a small
 * photo canvas finished through the store, so it sits in My Photos' Finished section and leaves Pictures, today's pick
 * and the streak alone. Call it before the gallery shows.
 */
fun GemmoryApp.seedFirstFinish() = runBlocking {
    store.create(FIRST_FINISH, A11yTestSupport.pattern(4, 3, 2))
    val opened = store.open(FIRST_FINISH) as CanvasStore.Opened.Ready
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val session = store.start(opened)
        while (!session.isFinished) session.placeNext()
        session.close()
    }
    assertTrue(store.isFinished(FIRST_FINISH), "the seeded first finish is saved")
}
