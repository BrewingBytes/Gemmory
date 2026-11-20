package com.brewingbytes.gemmory.play

import platform.posix.CLOCK_UPTIME_RAW
import platform.posix.clock_gettime_nsec_np

internal actual fun frameClockNanos(): Long = clock_gettime_nsec_np(CLOCK_UPTIME_RAW.toUInt()).toLong()
