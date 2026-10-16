package com.brewingbytes.gemmory.game

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

/**
 * The finish celebration (#57): on a fresh finish the sweep becomes a sparkle that glints every gem in turn along its
 * diagonal, then at the chime a burst of gem confetti in the picture's own colors falls through the dimmed room, while
 * a haptic crescendo rises under the sweep and peaks on the chime. Pure timing and physics only: the completion screen
 * draws it, and the save log, the time-lapse and the share frame know nothing of it.
 */
object FinishCelebration {
    /** The sparkle runs in the sweep's 2.4s; the confetti falls through the 2s hold that follows it. */
    const val SWEEP_MS = 2_400
    const val CONFETTI_MS = 2_000

    // Sparkle: each tile glints for [GLINT_WINDOW] of the sweep, starting where the diagonal reaches it, nudged by up
    // to [GLINT_JITTER] so the wave twinkles instead of moving as a straight band. Both are fractions of the sweep.
    private const val GLINT_WINDOW = 0.09f
    private const val GLINT_JITTER = 0.05f

    /** A tile with jitter above this also shows a small 4-point facet star (about one in seven). */
    const val STAR_JITTER = 0.86f

    /**
     * Cells per sparkle tile side, so a glint is never smaller than [minTilePx]: on a Big canvas at fit zoom a gem is
     * only a few pixels, and a per-gem glint reads as noise.
     */
    fun tileCells(cellPx: Float, minTilePx: Float): Int =
        if (cellPx <= 0f) 1 else ceil(minTilePx / cellPx).toInt().coerceAtLeast(1)

    /** A stable 0..1 value per tile, so the twinkle is the same on every frame. */
    fun jitter(index: Int): Float = ((index.toLong() * 2_654_435_761L).mod(1_000L)) / 1_000f

    /**
     * How strongly tile ([col], [row]) of a [cols]×[rows] tiling glints at sweep [progress] (0..1): 0 before the wave
     * reaches it, up to 1 at its middle, back to 0 after. Every tile glints once, and all have finished at progress 1.
     */
    fun glint(progress: Float, col: Int, row: Int, cols: Int, rows: Int): Float {
        val diagonal = if (cols + rows <= 2) 0f else (col + row).toFloat() / (cols + rows - 2)
        val start = diagonal * (1f - GLINT_JITTER - GLINT_WINDOW) + jitter(row * cols + col) * GLINT_JITTER
        val t = (progress - start) / GLINT_WINDOW
        return if (t <= 0f || t >= 1f) 0f else sin(t * PI)
    }

    /**
     * One confetti gem, in dp from the room's top-left, launched [delayMs] after the chime. [slot] is a palette slot of
     * the picture.
     */
    class Gem(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val size: Float,
        val angle: Float,
        val spin: Float,
        val slot: Int,
        val delayMs: Int,
    )

    /** Where a gem is at a moment: [tumble] is its horizontal scale (-1..1) as it flips, [alpha] its fade. */
    class GemAt(val x: Float, val y: Float, val angle: Float, val tumble: Float, val size: Float, val alpha: Float)

    /**
     * The confetti for a picture drawn at ([pictureLeft], [pictureTop]) size [pictureWidth]×[pictureHeight] in a room
     * [roomWidth] wide, all in dp. Most gems spill out from under the picture's bottom edge into the room below; the
     * rest rain from above the top of the screen and pass behind the picture (the screen draws them behind it, never
     * over it). Colors are picked in proportion to [counts], the picture's gems per palette slot.
     */
    fun confetti(
        counts: IntArray,
        pictureLeft: Float,
        pictureTop: Float,
        pictureWidth: Float,
        pictureHeight: Float,
        roomWidth: Float,
        seed: Int,
    ): List<Gem> {
        val total = counts.sum()
        if (total <= 0) return emptyList()
        val random = Random(seed)
        val count = (BASE_COUNT * (roomWidth / BASE_ROOM_DP).coerceIn(1f, 2f)).toInt()
        val pops = (count * POP_SHARE).toInt()
        fun slot(): Int {
            var left = random.nextInt(total)
            counts.forEachIndexed { slot, n ->
                left -= n
                if (left < 0) return slot
            }
            return counts.indexOfLast { it > 0 }
        }
        fun between(from: Float, to: Float) = from + random.nextFloat() * (to - from)
        return List(count) { i ->
            if (i < pops) {
                val side = if (random.nextBoolean()) 1f else -1f
                Gem(
                    x = pictureLeft + random.nextFloat() * pictureWidth,
                    y = pictureTop + pictureHeight - 6f,
                    vx = side * between(60f, 280f),
                    vy = -between(0f, 140f),
                    size = between(4f, 9f),
                    angle = between(0f, TWO_PI),
                    spin = between(-5f, 5f),
                    slot = slot(),
                    delayMs = random.nextInt(120),
                )
            } else {
                Gem(
                    x = random.nextFloat() * roomWidth,
                    y = -between(10f, 70f),
                    vx = between(-20f, 20f),
                    vy = between(40f, 130f),
                    size = between(4f, 9f),
                    angle = between(0f, TWO_PI),
                    spin = between(-4f, 4f),
                    slot = slot(),
                    delayMs = 100 + random.nextInt(500),
                )
            }
        }
    }

    /**
     * [gem] at [ms] after the chime, or null before its launch and once it has faded out. Every gem fades out together
     * over the last [FADE_MS] of [CONFETTI_MS], so the room is clear when the text fades in.
     */
    fun at(gem: Gem, ms: Float): GemAt? {
        val t = (ms - gem.delayMs) / 1_000f
        if (t < 0f) return null
        val alpha = ((CONFETTI_MS - ms) / FADE_MS).coerceIn(0f, 1f)
        if (alpha <= 0f) return null
        val spun = gem.spin * t
        return GemAt(
            x = gem.x + gem.vx * t,
            y = gem.y + gem.vy * t + 0.5f * GRAVITY * t * t,
            angle = gem.angle + spun,
            tumble = cos(spun * 0.7f),
            size = gem.size,
            alpha = alpha,
        )
    }

    /** One step of the haptic crescendo: wait [delayMs], then a tick at [scale] (0..1). */
    class Tick(val delayMs: Int, val scale: Float)

    /**
     * The crescendo as primitive ticks: they speed up and swell across the sweep, so the last one lands on the chime,
     * where the peak follows. At most [MAX_TICKS], well inside a composition's size limit.
     */
    fun crescendoTicks(): List<Tick> {
        // Gaps shrink linearly from FIRST_GAP to LAST_GAP, scaled so they add up to the sweep; rounding the running
        // total keeps every gap within 1ms of its share.
        val n = MAX_TICKS
        val gaps = List(n) { i -> FIRST_GAP_MS + (LAST_GAP_MS - FIRST_GAP_MS) * i / (n - 1f) }
        val scale = SWEEP_MS / gaps.sum()
        var total = 0f
        var spent = 0
        return gaps.mapIndexed { i, gap ->
            total += gap * scale
            val delay = (if (i == n - 1) SWEEP_MS else round(total).toInt()) - spent
            spent += delay
            val p = (i + 1f) / n
            Tick(delay, MIN_TICK + (MAX_TICK - MIN_TICK) * p * p)
        }
    }

    /**
     * The crescendo as a one-shot amplitude waveform for vibrators without primitives: a rise across the sweep that
     * pulses faster as it climbs, a short peak on the chime, then a quick fall. Amplitudes are 1..255.
     */
    fun crescendoWaveform(): Pair<LongArray, IntArray> {
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        val steps = SWEEP_MS / STEP_MS
        for (i in 0 until steps) {
            val p = (i + 0.5f) / steps
            val pulse = 0.5f + 0.5f * sin(p * PI * (6f + 10f * p))
            val level = (0.08f + 0.55f * p * p) * (0.6f + 0.4f * pulse)
            timings += STEP_MS.toLong()
            amplitudes += (level * 255).toInt().coerceIn(1, 255)
        }
        timings += PEAK_MS.toLong()
        amplitudes += 255
        val fallSteps = FALL_MS / STEP_MS
        for (i in 1..fallSteps) {
            timings += STEP_MS.toLong()
            amplitudes += (255 * (1f - 0.9f * i / fallSteps)).toInt().coerceIn(1, 255)
        }
        return timings.toLongArray() to amplitudes.toIntArray()
    }

    private const val PI = kotlin.math.PI.toFloat()
    private const val TWO_PI = 2 * PI
    private const val GRAVITY = 520f
    private const val FADE_MS = 400f
    private const val BASE_COUNT = 180
    private const val BASE_ROOM_DP = 360f
    private const val POP_SHARE = 0.6f
    const val MAX_TICKS = 18
    private const val FIRST_GAP_MS = 260f
    private const val LAST_GAP_MS = 50f
    private const val MIN_TICK = 0.1f
    private const val MAX_TICK = 0.7f
    private const val STEP_MS = 50
    private const val PEAK_MS = 60
    private const val FALL_MS = 400
}
