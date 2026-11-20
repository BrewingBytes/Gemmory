package com.brewingbytes.gemmory.play

/**
 * The monotonic clock the frame clock's `withFrameNanos` times come from, in nanoseconds, so an effect started now and
 * drawn at a frame time compare on one clock: Android's `System.nanoTime` (Choreographer), iOS's uptime (the display
 * link's `CACurrentMediaTime`).
 */
internal expect fun frameClockNanos(): Long

/** [frameClockNanos] in milliseconds: the uptime a press and its release are timed on. */
internal fun uptimeMillis(): Long = frameClockNanos() / 1_000_000
