package com.brewingbytes.gemmory.bundled

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.copy.Words

/**
 * A bundled picture: a frozen grid asset made once by `./gradlew :shared:freezeBundledGrid` and copied as is into
 * `canvases/bundled/<id>/` when the player first opens it (design D13, eng D11). [gridCrc32] is the asset's CRC,
 * pinned so a re-frozen asset is caught by BundledPicturesTest before it orphans saves. A [locked] picture opens
 * after a rewarded video (or VIP); its unlock is permanent (design doc tier table). Whether a picture is locked comes
 * from [BundledPictures.FREE], not from its row. Titles are strings named `title_<id>` (T49 X4). Common since T64.
 */
data class BundledPicture(val id: String, val titleKey: String, val asset: String, val gridCrc32: Int, val locked: Boolean = false) {
    /** Its title in the app's language (T49 X4): [titleKey], `title_<id>` in `strings.xml`. */
    fun title(words: Words): String = words.text(titleKey)
}

object BundledPictures {
    /**
     * The pictures a free player can open without a video, besides the starter (#32, founder 2026-10-16): two of each
     * size. Every other picture is locked, including any added later. Edit this set to change what's free;
     * BundledPicturesTest checks the ids exist and that each size keeps at least two.
     */
    val FREE = setOf(
        "lemons", "balloon", // Quick
        "cherries", "tuscan-hills", // Medium
        "puppy", "cat", // Big
    )

    /** The free starter picture (design D13): 24×32, 8 colors, about 5 minutes. Always the first Pictures tile. */
    val STARTER = BundledPicture(
        id = "starter",
        titleKey = "title_starter",
        asset = "pictures/starter.gmrg",
        gridCrc32 = 0x099b53e8,
    )

    /** Quick pictures (~3k gems, 16 colors): CC0 and public-domain sources, then AI-generated tiles (#31; PROVENANCE.md). */
    private val QUICK = listOf(
        BundledPicture("wave", "title_wave", "pictures/wave.gmrg", 0x55fc9837),
        BundledPicture("sunrise", "title_sunrise", "pictures/sunrise.gmrg", 0x2f65dc50),
        BundledPicture("annecy", "title_annecy", "pictures/annecy.gmrg", 0xd5aac9f8.toInt()),
        BundledPicture("tulip-bouquet", "title_tulip_bouquet", "pictures/tulip-bouquet.gmrg", 0x6a74e643),
        BundledPicture("lemons", "title_lemons", "pictures/lemons.gmrg", 0xe2ccbe38.toInt()),
        BundledPicture("coral-mandala", "title_coral_mandala", "pictures/coral-mandala.gmrg", 0x150469a2),
        BundledPicture("beach-huts", "title_beach_huts", "pictures/beach-huts.gmrg", 0xa3dfb7e4.toInt()),
        BundledPicture("rowing-boat", "title_rowing_boat", "pictures/rowing-boat.gmrg", 0xf4076908.toInt()),
        BundledPicture("balloon", "title_balloon", "pictures/balloon.gmrg", 0x488cfdf4),
        BundledPicture("deco-sunrise", "title_deco_sunrise", "pictures/deco-sunrise.gmrg", 0x57b87b58),
        BundledPicture("lighthouse", "title_lighthouse", "pictures/lighthouse.gmrg", 0x528d7d14),
        BundledPicture("poppies", "title_poppies", "pictures/poppies.gmrg", 0x04a14b03),
    )

    /** Medium pictures (~8k gems, 24 colors): CC0 and public-domain sources, then AI-generated tiles (#31; PROVENANCE.md). */
    private val MEDIUM = listOf(
        BundledPicture("macarons", "title_macarons", "pictures/macarons.gmrg", 0x3dca2ec6),
        BundledPicture("lavender", "title_lavender", "pictures/lavender.gmrg", 0xe67c4228.toInt()),
        BundledPicture("almond-blossom", "title_almond_blossom", "pictures/almond-blossom.gmrg", 0x5ec1070d),
        BundledPicture("water-lilies", "title_water_lilies", "pictures/water-lilies.gmrg", 0x15d4bb2b),
        BundledPicture("mountain-lake", "title_mountain_lake", "pictures/mountain-lake.gmrg", 0x0324a469),
        BundledPicture("chamonix", "title_chamonix", "pictures/chamonix.gmrg", 0x05274a54),
        BundledPicture("santorini", "title_santorini", "pictures/santorini.gmrg", 0xb2623a1e.toInt()),
        BundledPicture("cherry-river", "title_cherry_river", "pictures/cherry-river.gmrg", 0x87fef732.toInt()),
        BundledPicture("autumn-road", "title_autumn_road", "pictures/autumn-road.gmrg", 0x5a302819),
        BundledPicture("tulip-field", "title_tulip_field", "pictures/tulip-field.gmrg", 0x222c570f),
        BundledPicture("the-kiss", "title_the_kiss", "pictures/the-kiss.gmrg", 0x66c67fb9),
        BundledPicture("stained-glass-mandala", "title_stained_glass_mandala", "pictures/stained-glass-mandala.gmrg", 0x0130d00d),
        BundledPicture("bougainvillea-street", "title_bougainvillea_street", "pictures/bougainvillea-street.gmrg", 0x8946067b.toInt()),
        BundledPicture("riviera-hotel", "title_riviera_hotel", "pictures/riviera-hotel.gmrg", 0x6b0ab171),
        BundledPicture("cherries", "title_cherries", "pictures/cherries.gmrg", 0x55c59736),
        BundledPicture("tuscan-hills", "title_tuscan_hills", "pictures/tuscan-hills.gmrg", 0xaa90f883.toInt()),
        BundledPicture("market-stall", "title_market_stall", "pictures/market-stall.gmrg", 0x1ded851b),
    )

    /** Big pictures (~15k gems, 36 colors): AI-generated photos (#8), CC0 and public-domain sources, then AI tiles (#31). */
    private val BIG = listOf(
        BundledPicture("puppy", "title_puppy", "pictures/puppy.gmrg", 0x4855ef0e),
        BundledPicture("cat", "title_cat", "pictures/cat.gmrg", 0xa07064d5.toInt()),
        BundledPicture("couple", "title_couple", "pictures/couple.gmrg", 0x5dbac3b8),
        BundledPicture("sunflowers", "title_sunflowers", "pictures/sunflowers.gmrg", 0x8f4f01c2.toInt()),
        BundledPicture("sunset", "title_sunset", "pictures/sunset.gmrg", 0xd24e22ab.toInt()),
        BundledPicture("cottage", "title_cottage", "pictures/cottage.gmrg", 0xf983e40c.toInt()),
        BundledPicture("plum-garden", "title_plum_garden", "pictures/plum-garden.gmrg", 0x348af73d),
        BundledPicture("birch-forest", "title_birch_forest", "pictures/birch-forest.gmrg", 0xd4761577.toInt()),
        BundledPicture("winter", "title_winter", "pictures/winter.gmrg", 0x4cd05f92),
        BundledPicture("oranges-lemons", "title_oranges_lemons", "pictures/oranges-lemons.gmrg", 0x817424bb.toInt()),
        BundledPicture("oia", "title_oia", "pictures/oia.gmrg", 0xeb1efeae.toInt()),
        BundledPicture("lavender-hood", "title_lavender_hood", "pictures/lavender-hood.gmrg", 0x92005528.toInt()),
        BundledPicture("flower-mandala", "title_flower_mandala", "pictures/flower-mandala.gmrg", 0x27d77d02),
        BundledPicture("venice", "title_venice", "pictures/venice.gmrg", 0xda16812d.toInt()),
        BundledPicture("peonies", "title_peonies", "pictures/peonies.gmrg", 0x989b4d3e.toInt()),
        BundledPicture("deco-peacock", "title_deco_peacock", "pictures/deco-peacock.gmrg", 0xe2bf2689.toInt()),
        BundledPicture("japanese-garden", "title_japanese_garden", "pictures/japanese-garden.gmrg", 0xc24cec43.toInt()),
        BundledPicture("northern-lights", "title_northern_lights", "pictures/northern-lights.gmrg", 0x46c20b95),
        BundledPicture("misty-pier", "title_misty_pier", "pictures/misty-pier.gmrg", 0x0215c44c),
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
