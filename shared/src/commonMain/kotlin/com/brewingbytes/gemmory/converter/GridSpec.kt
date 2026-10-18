package com.brewingbytes.gemmory.converter

/** Crop ratios offered in the photo flow and used by bundled pictures (design doc R3-11). */
enum class AspectRatio(val width: Int, val height: Int, val label: String) {
    SQUARE(1, 1, "1:1"),
    PORTRAIT(3, 4, "3:4"),
    LANDSCAPE(4, 3, "4:3"),
}

/**
 * The color-count choice on Preview (#29). [STANDARD] is the size's original cap; [MORE] is VIP, or one rewarded
 * video per photo canvas.
 */
enum class ColorChoice(val label: String) {
    FEWER("Fewer"),
    STANDARD("Standard"),
    MORE("More"),
    ;

    /** The analytics value (`fewer` | `standard` | `more`). */
    val param: String get() = name.lowercase()
}

/**
 * Canvas sizes shown in the size picker (design doc R3-11). [paletteCap] is the Standard cap, the only one before
 * #29, which bundled pictures and every app before versionCode 12 use; [fewerCap] and [moreCap] are the other two
 * color choices.
 */
enum class CanvasSize(val label: String, val fewerCap: Int, val paletteCap: Int, val moreCap: Int) {
    QUICK("Quick", 12, 16, 24),
    MEDIUM("Medium", 16, 24, 40),
    BIG("Big", 24, 36, 60),
    ;

    fun cap(choice: ColorChoice): Int = when (choice) {
        ColorChoice.FEWER -> fewerCap
        ColorChoice.STANDARD -> paletteCap
        ColorChoice.MORE -> moreCap
    }
}

/** Grid dimensions and palette cap that a conversion targets. */
data class GridSpec(
    val id: String,
    val width: Int,
    val height: Int,
    val paletteCap: Int,
) {
    init {
        require(width > 0 && height > 0) { "grid must be at least 1×1, was ${width}×$height" }
        require(paletteCap > 0) { "palette cap must be positive, was $paletteCap" }
    }

    val cellCount: Int get() = width * height

    companion object {
        private val DIMENSIONS: Map<CanvasSize, Map<AspectRatio, Pair<Int, Int>>> = mapOf(
            CanvasSize.QUICK to mapOf(
                AspectRatio.SQUARE to (56 to 56),
                AspectRatio.PORTRAIT to (48 to 64),
                AspectRatio.LANDSCAPE to (64 to 48),
            ),
            CanvasSize.MEDIUM to mapOf(
                AspectRatio.SQUARE to (90 to 90),
                AspectRatio.PORTRAIT to (78 to 104),
                AspectRatio.LANDSCAPE to (104 to 78),
            ),
            CanvasSize.BIG to mapOf(
                AspectRatio.SQUARE to (122 to 122),
                AspectRatio.PORTRAIT to (106 to 142),
                AspectRatio.LANDSCAPE to (142 to 106),
            ),
        )

        fun of(size: CanvasSize, ratio: AspectRatio, choice: ColorChoice = ColorChoice.STANDARD): GridSpec {
            val (w, h) = DIMENSIONS.getValue(size).getValue(ratio)
            return GridSpec("${size.name.lowercase()}-${ratio.label}", w, h, size.cap(choice))
        }

        /** The 9 specs the size picker can produce (3 sizes × 3 ratios), at the Standard colors. */
        val PICKER: List<GridSpec> = CanvasSize.entries.flatMap { size ->
            AspectRatio.entries.map { ratio -> of(size, ratio) }
        }

        /**
         * Build-time only (design D13): produces the free starter picture's frozen grid.
         * It is never offered in the size picker and is not part of [PICKER].
         */
        val STARTER: GridSpec = GridSpec("starter-3:4", 24, 32, 8)
    }
}
