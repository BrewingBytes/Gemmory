package com.brewingbytes.gemmory.bundled

/**
 * A bundled picture: a frozen grid asset made once by `./gradlew :shared:freezeBundledGrid` and copied as is into
 * `canvases/bundled/<id>/` when the player first opens it (design D13, eng D11). [gridCrc32] is the asset's CRC,
 * pinned so a re-frozen asset is caught by BundledPicturesTest before it orphans saves. A [locked] picture opens
 * after a rewarded video (or VIP); its unlock is permanent (design doc tier table, ~10 of ~30).
 */
data class BundledPicture(val id: String, val title: String, val asset: String, val gridCrc32: Int, val locked: Boolean = false)

object BundledPictures {
    /** The free starter picture (design D13): 24×32, 8 colors, about 5 minutes. Always the first Pictures tile. */
    val STARTER = BundledPicture(
        id = "starter",
        title = "The Starry Night",
        asset = "pictures/starter.gmrg",
        gridCrc32 = 0x099b53e8,
    )

    /** Gallery order. Sources and licenses: tools/bundled/PROVENANCE.md. */
    val ALL: List<BundledPicture> = listOf(STARTER)

    fun byId(id: String): BundledPicture? = ALL.firstOrNull { it.id == id }
}
