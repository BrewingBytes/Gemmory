package com.brewingbytes.gemmory.timelapse

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FallbackChainTest {
    private class DiskFullError : Exception("ENOSPC")

    /** Scripted backend: what each target's probe and encode do, and which partial files exist. */
    private class FakeBackend(
        val supported: Set<EncodeTarget> = EncodeTarget.CHAIN.toSet(),
        val encodeErrors: Map<EncodeTarget, Exception> = emptyMap(),
        val pictureError: Exception? = null,
        val probeError: Exception? = null,
        val cancelAtFrame: Int? = null,
        val free: Long = Long.MAX_VALUE,
    ) : EncodeBackend {
        val partials = mutableSetOf<String>()
        val calls = mutableListOf<String>()

        override fun probe(target: EncodeTarget): String? {
            calls += "probe $target"
            probeError?.let { throw it }
            return if (target in supported) "c2.fake.avc" else null
        }

        override fun encode(target: EncodeTarget, codec: String, onFrame: (Int, Int) -> Unit, checkCancelled: () -> Unit) {
            calls += "encode $target"
            partials += "$target"
            for (frame in 1..FramePlan.TOTAL_FRAMES) {
                checkCancelled()
                if (frame == cancelAtFrame) throw CancellationException("cancelled")
                if (frame == 200) encodeErrors[target]?.let { throw it }
                onFrame(frame, FramePlan.TOTAL_FRAMES)
            }
        }

        override fun renderPicture() {
            calls += "picture"
            partials += "picture"
            pictureError?.let { throw it }
        }

        override fun discard(target: EncodeTarget?) {
            partials -= target?.toString() ?: "picture"
        }

        override fun isDiskFull(error: Throwable): Boolean = error is DiskFullError || error.cause is DiskFullError

        override fun freeBytes(): Long = free
    }

    private val reports = mutableListOf<AttemptFailure>()

    private fun run(backend: FakeBackend, progress: MutableList<Int> = mutableListOf()): ChainOutcome =
        FallbackChain(backend, { reports += it }).run({ done, _ -> progress += done }, {})

    @Test
    fun fullHdWorks() {
        val backend = FakeBackend()
        val progress = mutableListOf<Int>()
        assertEquals(ChainOutcome.Video(EncodeTarget.FULL_HD), run(backend, progress))
        assertEquals(listOf("probe 1080x1920@30", "encode 1080x1920@30"), backend.calls)
        assertEquals((1..450).toList(), progress)
        assertTrue(reports.isEmpty())
        assertEquals(setOf("1080x1920@30"), backend.partials, "the finished partial is left to be published")
    }

    @Test
    fun probeRejectsFullHdSoHdIsUsedWithoutAReport() {
        val backend = FakeBackend(supported = setOf(EncodeTarget.HD))
        assertEquals(ChainOutcome.Video(EncodeTarget.HD), run(backend))
        assertEquals(listOf("probe 1080x1920@30", "probe 720x1280@30", "encode 720x1280@30"), backend.calls)
        assertTrue(reports.isEmpty(), "an unsupported size is a normal skip, not a failure")
    }

    @Test
    fun configureFailureDeletesThePartialReportsAndFallsBack() {
        val error = EncodeFailure(EncodeStage.CONFIGURE, IllegalStateException("codec"))
        val backend = FakeBackend(encodeErrors = mapOf(EncodeTarget.FULL_HD to error))
        assertEquals(ChainOutcome.Video(EncodeTarget.HD), run(backend))
        assertEquals(setOf("720x1280@30"), backend.partials, "the 1080 partial is gone")
        assertEquals(listOf(AttemptFailure(EncodeTarget.FULL_HD, "c2.fake.avc", EncodeStage.CONFIGURE, error)), reports)
    }

    @Test
    fun encodeAndMuxFailuresBothFallBackThenThePictureIsMade() {
        val backend = FakeBackend(
            encodeErrors = mapOf(
                EncodeTarget.FULL_HD to EncodeFailure(EncodeStage.ENCODE, IllegalStateException()),
                EncodeTarget.HD to EncodeFailure(EncodeStage.MUX, IllegalStateException()),
            ),
        )
        assertEquals(ChainOutcome.Picture, run(backend))
        assertEquals(setOf("picture"), backend.partials)
        assertEquals(listOf(EncodeStage.ENCODE, EncodeStage.MUX), reports.map { it.stage })
        assertEquals(listOf(EncodeTarget.FULL_HD, EncodeTarget.HD), reports.map { it.target })
    }

    @Test
    fun anUnclassifiedErrorCountsAsAnEncodeFailure() {
        val backend = FakeBackend(encodeErrors = mapOf(EncodeTarget.FULL_HD to RuntimeException("boom")))
        assertEquals(ChainOutcome.Video(EncodeTarget.HD), run(backend))
        assertEquals(EncodeStage.ENCODE, reports.single().stage)
    }

    @Test
    fun noVideoTargetSupportedGivesThePicture() {
        val backend = FakeBackend(supported = emptySet())
        assertEquals(ChainOutcome.Picture, run(backend))
        assertEquals(listOf("probe 1080x1920@30", "probe 720x1280@30", "picture"), backend.calls)
    }

    @Test
    fun aThrowingProbeIsReportedAndSkipped() {
        val backend = FakeBackend(probeError = IllegalArgumentException("bad caps"))
        assertEquals(ChainOutcome.Picture, run(backend))
        assertEquals(listOf(EncodeStage.PROBE, EncodeStage.PROBE), reports.map { it.stage })
    }

    @Test
    fun withoutThePictureNoVideoIsFailedAndNoPictureIsMade() {
        // The feed cut (T51) is a video or nothing.
        val backend = FakeBackend(encodeErrors = mapOf(EncodeTarget.FULL_HD to RuntimeException("a"), EncodeTarget.HD to RuntimeException("b")))
        assertEquals(ChainOutcome.Failed, FallbackChain(backend, { reports += it }, picture = false).run({ _, _ -> }, {}))
        assertEquals(listOf("probe 1080x1920@30", "encode 1080x1920@30", "probe 720x1280@30", "encode 720x1280@30"), backend.calls)
        assertTrue(backend.partials.isEmpty())
        assertEquals(2, reports.size)
    }

    @Test
    fun everythingFailingIsFailedWithNoFileLeft() {
        val backend = FakeBackend(supported = emptySet(), pictureError = IllegalStateException("png"))
        assertEquals(ChainOutcome.Failed, run(backend))
        assertTrue(backend.partials.isEmpty())
        assertEquals(EncodeStage.PICTURE, reports.single().stage)
    }

    @Test
    fun diskFullStopsTheChainAtOnce() {
        val backend = FakeBackend(encodeErrors = mapOf(EncodeTarget.FULL_HD to EncodeFailure(EncodeStage.MUX, DiskFullError())))
        assertEquals(ChainOutcome.DiskFull, run(backend))
        assertEquals(listOf("probe 1080x1920@30", "encode 1080x1920@30"), backend.calls, "no 720 try, no picture")
        assertTrue(backend.partials.isEmpty())
        assertEquals(1, reports.size)
    }

    @Test
    fun tooLittleSpaceFor1080SkipsTo720Silently() {
        // 15 MB free: 1080 needs ~25.2 MB, 720 ~12.6 MB.
        val backend = FakeBackend(free = 15_000_000)
        assertEquals(25_200_000, EncodeTarget.FULL_HD.neededBytes)
        assertEquals(12_600_000, EncodeTarget.HD.neededBytes)
        assertEquals(ChainOutcome.Video(EncodeTarget.HD), run(backend))
        assertEquals(listOf("probe 720x1280@30", "encode 720x1280@30"), backend.calls)
        assertTrue(reports.isEmpty(), "not enough room for a size is a skip, not a failure")
    }

    @Test
    fun tooLittleSpaceForTheSmallestIsDiskFull() {
        val backend = FakeBackend(free = 5_000_000)
        assertEquals(ChainOutcome.DiskFull, run(backend))
        assertTrue(backend.calls.isEmpty(), "nothing is probed, encoded or drawn")
        assertTrue(reports.isEmpty())
    }

    @Test
    fun diskFullWhileMakingThePicture() {
        val backend = FakeBackend(supported = emptySet(), pictureError = DiskFullError())
        assertEquals(ChainOutcome.DiskFull, run(backend))
        assertTrue(backend.partials.isEmpty())
    }

    @Test
    fun cancelDeletesThePartialAndNeitherFallsBackNorReports() {
        val backend = FakeBackend(cancelAtFrame = 100)
        assertFailsWith<CancellationException> { run(backend) }
        assertTrue(backend.partials.isEmpty())
        assertEquals(listOf("probe 1080x1920@30", "encode 1080x1920@30"), backend.calls)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun cancelBeforeThePictureMakesNothing() {
        val backend = FakeBackend(supported = emptySet())
        var checks = 0
        assertFailsWith<CancellationException> {
            FallbackChain(backend, { reports += it }).run({ _, _ -> }, { if (++checks == 3) throw CancellationException() })
        }
        assertEquals(listOf("probe 1080x1920@30", "probe 720x1280@30"), backend.calls)
        assertTrue(backend.partials.isEmpty())
    }
}
