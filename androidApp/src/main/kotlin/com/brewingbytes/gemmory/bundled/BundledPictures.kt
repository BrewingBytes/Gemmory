package com.brewingbytes.gemmory.bundled

import androidx.annotation.StringRes
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.copy.Strings

/**
 * A bundled picture: a frozen grid asset made once by `./gradlew :shared:freezeBundledGrid` and copied as is into
 * `canvases/bundled/<id>/` when the player first opens it (design D13, eng D11). [gridCrc32] is the asset's CRC,
 * pinned so a re-frozen asset is caught by BundledPicturesTest before it orphans saves. A [locked] picture opens
 * after a rewarded video (or VIP); its unlock is permanent (design doc tier table). Whether a picture is locked comes
 * from [BundledPictures.FREE], not from its row. Titles are string resources, `title_<id>` (T49 X4).
 */
data class BundledPicture(val id: String, @StringRes val titleRes: Int, val asset: String, val gridCrc32: Int, val locked: Boolean = false) {
    /** Its title in the app's language (T49 X4): `title_<id>` in `strings.xml`. */
    fun title(strings: Strings): String = strings.text(titleRes)
}

object BundledPictures {
    /**
     * The pictures a free player can open without a video, besides the starter (#32, founder 2026-10-16): two of each
     * size. Every other picture is locked, including any added later. Edit this set to change what's free;
     * BundledPicturesTest checks the ids exist and that each size keeps at least two.
     */
    internal val FREE = setOf(
        "lemons", "balloon", // Quick
        "cherries", "tuscan-hills", // Medium
        "puppy", "cat", // Big
    )

    /** The free starter picture (design D13): 24×32, 8 colors, about 5 minutes. Always the first Pictures tile. */
    val STARTER = BundledPicture(
        id = "starter",
        titleRes = R.string.title_starter,
        asset = "pictures/starter.gmrg",
        gridCrc32 = 0x099b53e8,
    )

    /** Quick pictures (~3k gems, 16 colors): CC0 and public-domain sources, then AI-generated tiles (#31; PROVENANCE.md). */
    private val QUICK = listOf(
        BundledPicture("wave", R.string.title_wave, "pictures/wave.gmrg", 0x55fc9837),
        BundledPicture("sunrise", R.string.title_sunrise, "pictures/sunrise.gmrg", 0x2f65dc50),
        BundledPicture("annecy", R.string.title_annecy, "pictures/annecy.gmrg", 0xd5aac9f8.toInt()),
        BundledPicture("tulip-bouquet", R.string.title_tulip_bouquet, "pictures/tulip-bouquet.gmrg", 0x6a74e643),
        BundledPicture("lemons", R.string.title_lemons, "pictures/lemons.gmrg", 0xe2ccbe38.toInt()),
        BundledPicture("coral-mandala", R.string.title_coral_mandala, "pictures/coral-mandala.gmrg", 0x150469a2),
        BundledPicture("beach-huts", R.string.title_beach_huts, "pictures/beach-huts.gmrg", 0xa3dfb7e4.toInt()),
        BundledPicture("rowing-boat", R.string.title_rowing_boat, "pictures/rowing-boat.gmrg", 0xf4076908.toInt()),
        BundledPicture("balloon", R.string.title_balloon, "pictures/balloon.gmrg", 0x488cfdf4),
        BundledPicture("deco-sunrise", R.string.title_deco_sunrise, "pictures/deco-sunrise.gmrg", 0x57b87b58),
        BundledPicture("lighthouse", R.string.title_lighthouse, "pictures/lighthouse.gmrg", 0x528d7d14),
        BundledPicture("poppies", R.string.title_poppies, "pictures/poppies.gmrg", 0x04a14b03),
    )

    /** Medium pictures (~8k gems, 24 colors): CC0 and public-domain sources, then AI-generated tiles (#31; PROVENANCE.md). */
    private val MEDIUM = listOf(
        BundledPicture("macarons", R.string.title_macarons, "pictures/macarons.gmrg", 0x3dca2ec6),
        BundledPicture("lavender", R.string.title_lavender, "pictures/lavender.gmrg", 0xe67c4228.toInt()),
        BundledPicture("almond-blossom", R.string.title_almond_blossom, "pictures/almond-blossom.gmrg", 0x5ec1070d),
        BundledPicture("water-lilies", R.string.title_water_lilies, "pictures/water-lilies.gmrg", 0x15d4bb2b),
        BundledPicture("mountain-lake", R.string.title_mountain_lake, "pictures/mountain-lake.gmrg", 0x0324a469),
        BundledPicture("chamonix", R.string.title_chamonix, "pictures/chamonix.gmrg", 0x05274a54),
        BundledPicture("santorini", R.string.title_santorini, "pictures/santorini.gmrg", 0xb2623a1e.toInt()),
        BundledPicture("cherry-river", R.string.title_cherry_river, "pictures/cherry-river.gmrg", 0x87fef732.toInt()),
        BundledPicture("autumn-road", R.string.title_autumn_road, "pictures/autumn-road.gmrg", 0x5a302819),
        BundledPicture("tulip-field", R.string.title_tulip_field, "pictures/tulip-field.gmrg", 0x222c570f),
        BundledPicture("the-kiss", R.string.title_the_kiss, "pictures/the-kiss.gmrg", 0x66c67fb9),
        BundledPicture("stained-glass-mandala", R.string.title_stained_glass_mandala, "pictures/stained-glass-mandala.gmrg", 0x0130d00d),
        BundledPicture("bougainvillea-street", R.string.title_bougainvillea_street, "pictures/bougainvillea-street.gmrg", 0x8946067b.toInt()),
        BundledPicture("riviera-hotel", R.string.title_riviera_hotel, "pictures/riviera-hotel.gmrg", 0x6b0ab171),
        BundledPicture("cherries", R.string.title_cherries, "pictures/cherries.gmrg", 0x55c59736),
        BundledPicture("tuscan-hills", R.string.title_tuscan_hills, "pictures/tuscan-hills.gmrg", 0xaa90f883.toInt()),
        BundledPicture("market-stall", R.string.title_market_stall, "pictures/market-stall.gmrg", 0x1ded851b),
    )

    /** Big pictures (~15k gems, 36 colors): AI-generated photos (#8), CC0 and public-domain sources, then AI tiles (#31). */
    private val BIG = listOf(
        BundledPicture("puppy", R.string.title_puppy, "pictures/puppy.gmrg", 0x4855ef0e),
        BundledPicture("cat", R.string.title_cat, "pictures/cat.gmrg", 0xa07064d5.toInt()),
        BundledPicture("couple", R.string.title_couple, "pictures/couple.gmrg", 0x5dbac3b8),
        BundledPicture("sunflowers", R.string.title_sunflowers, "pictures/sunflowers.gmrg", 0x8f4f01c2.toInt()),
        BundledPicture("sunset", R.string.title_sunset, "pictures/sunset.gmrg", 0xd24e22ab.toInt()),
        BundledPicture("cottage", R.string.title_cottage, "pictures/cottage.gmrg", 0xf983e40c.toInt()),
        BundledPicture("plum-garden", R.string.title_plum_garden, "pictures/plum-garden.gmrg", 0x348af73d),
        BundledPicture("birch-forest", R.string.title_birch_forest, "pictures/birch-forest.gmrg", 0xd4761577.toInt()),
        BundledPicture("winter", R.string.title_winter, "pictures/winter.gmrg", 0x4cd05f92),
        BundledPicture("oranges-lemons", R.string.title_oranges_lemons, "pictures/oranges-lemons.gmrg", 0x817424bb.toInt()),
        BundledPicture("oia", R.string.title_oia, "pictures/oia.gmrg", 0xeb1efeae.toInt()),
        BundledPicture("lavender-hood", R.string.title_lavender_hood, "pictures/lavender-hood.gmrg", 0x92005528.toInt()),
        BundledPicture("flower-mandala", R.string.title_flower_mandala, "pictures/flower-mandala.gmrg", 0x27d77d02),
        BundledPicture("venice", R.string.title_venice, "pictures/venice.gmrg", 0xda16812d.toInt()),
        BundledPicture("peonies", R.string.title_peonies, "pictures/peonies.gmrg", 0x989b4d3e.toInt()),
        BundledPicture("deco-peacock", R.string.title_deco_peacock, "pictures/deco-peacock.gmrg", 0xe2bf2689.toInt()),
        BundledPicture("japanese-garden", R.string.title_japanese_garden, "pictures/japanese-garden.gmrg", 0xc24cec43.toInt()),
        BundledPicture("northern-lights", R.string.title_northern_lights, "pictures/northern-lights.gmrg", 0x46c20b95),
        BundledPicture("misty-pier", R.string.title_misty_pier, "pictures/misty-pier.gmrg", 0x0215c44c),
    )

    /**
     * Catalog order: the starter, then Quick, Medium and Big (#31). The gallery shows the ones a player can open first,
     * in this order, then the locked ones (#58, `picturesInOrder`). The starter is never locked. Sources and licenses:
     * tools/bundled/PROVENANCE.md.
     */
    val ALL: List<BundledPicture> = listOf(STARTER) + (QUICK + MEDIUM + BIG).map { it.copy(locked = it.id !in FREE) }

    fun byId(id: String): BundledPicture? = ALL.firstOrNull { it.id == id }

    /** A catalog picture's size, which places remote pictures beside it (#43); null for the starter. */
    fun canvasSizeOf(id: String): CanvasSize? = when {
        QUICK.any { it.id == id } -> CanvasSize.QUICK
        MEDIUM.any { it.id == id } -> CanvasSize.MEDIUM
        BIG.any { it.id == id } -> CanvasSize.BIG
        else -> null
    }
}
