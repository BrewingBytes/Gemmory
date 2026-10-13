package com.brewingbytes.gemmory.converter

/** Crop ratios offered in the photo flow and used by bundled pictures (design doc R3-11). */
enum class AspectRatio(val width: Int, val height: Int, val label: String) {
    SQUARE(1, 1, "1:1"),
    PORTRAIT(3, 4, "3:4"),
    LANDSCAPE(4, 3, "4:3"),
}

/** Canvas sizes shown in the size picker, with their palette caps (design doc R3-11). */
enum class CanvasSize(val label: String, val paletteCap: Int) {
    QUICK("Quick", 16),
    MEDIUM("Medium", 24),
    BIG("Big", 36),
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

        fun of(size: CanvasSize, ratio: AspectRatio): GridSpec {
            val (w, h) = DIMENSIONS.getValue(size).getValue(ratio)
            return GridSpec("${size.name.lowercase()}-${ratio.label}", w, h, size.paletteCap)
        }

        /** The 9 specs the size picker can produce (3 sizes × 3 ratios). */
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
