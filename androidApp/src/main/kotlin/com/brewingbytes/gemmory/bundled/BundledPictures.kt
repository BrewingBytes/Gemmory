package com.brewingbytes.gemmory.bundled

/**
 * A bundled picture: a frozen grid asset made once by `./gradlew :shared:freezeBundledGrid` and copied as is into
 * `canvases/bundled/<id>/` when the player first opens it (design D13, eng D11). [gridCrc32] is the asset's CRC,
 * pinned so a re-frozen asset is caught by BundledPicturesTest before it orphans saves. A [locked] picture opens
 * after a rewarded video (or VIP); its unlock is permanent (design doc tier table). Whether a picture is locked comes
 * from [BundledPictures.FREE], not from its row.
 */
data class BundledPicture(val id: String, val title: String, val asset: String, val gridCrc32: Int, val locked: Boolean = false)

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
        title = "The Starry Night",
        asset = "pictures/starter.gmrg",
        gridCrc32 = 0x099b53e8,
    )

    /** Quick pictures (~3k gems, 16 colors): CC0 and public-domain sources, then AI-generated tiles (#31; PROVENANCE.md). */
    private val QUICK = listOf(
        BundledPicture("wave", "The Great Wave", "pictures/wave.gmrg", 0x55fc9837),
        BundledPicture("sunrise", "Impression, Sunrise", "pictures/sunrise.gmrg", 0x2f65dc50),
        BundledPicture("annecy", "Lac d'Annecy", "pictures/annecy.gmrg", 0xd5aac9f8.toInt()),
        BundledPicture("tulip-bouquet", "Tulip Bouquet", "pictures/tulip-bouquet.gmrg", 0x6a74e643),
        BundledPicture("lemons", "Lemons on Blue Tiles", "pictures/lemons.gmrg", 0xe2ccbe38.toInt()),
        BundledPicture("coral-mandala", "Coral Mandala", "pictures/coral-mandala.gmrg", 0x150469a2),
        BundledPicture("beach-huts", "Beach Huts", "pictures/beach-huts.gmrg", 0xa3dfb7e4.toInt()),
        BundledPicture("rowing-boat", "Red Rowing Boat", "pictures/rowing-boat.gmrg", 0xf4076908.toInt()),
        BundledPicture("balloon", "Hot Air Balloon", "pictures/balloon.gmrg", 0x488cfdf4),
        BundledPicture("deco-sunrise", "Art Deco Sunrise", "pictures/deco-sunrise.gmrg", 0x57b87b58),
        BundledPicture("lighthouse", "Lighthouse", "pictures/lighthouse.gmrg", 0x528d7d14),
        BundledPicture("poppies", "Poppy Field", "pictures/poppies.gmrg", 0x04a14b03),
    )

    /** Medium pictures (~8k gems, 24 colors): CC0 and public-domain sources, then AI-generated tiles (#31; PROVENANCE.md). */
    private val MEDIUM = listOf(
        BundledPicture("macarons", "Macarons", "pictures/macarons.gmrg", 0x3dca2ec6),
        BundledPicture("lavender", "Lavender Rows", "pictures/lavender.gmrg", 0xe67c4228.toInt()),
        BundledPicture("almond-blossom", "Almond Blossom", "pictures/almond-blossom.gmrg", 0x5ec1070d),
        BundledPicture("water-lilies", "Water Lilies", "pictures/water-lilies.gmrg", 0x15d4bb2b),
        BundledPicture("mountain-lake", "Mountain Lake", "pictures/mountain-lake.gmrg", 0x0324a469),
        BundledPicture("chamonix", "Chamonix", "pictures/chamonix.gmrg", 0x05274a54),
        BundledPicture("santorini", "Santorini Terrace", "pictures/santorini.gmrg", 0xb2623a1e.toInt()),
        BundledPicture("cherry-river", "Cherry River", "pictures/cherry-river.gmrg", 0x87fef732.toInt()),
        BundledPicture("autumn-road", "Autumn Road", "pictures/autumn-road.gmrg", 0x5a302819),
        BundledPicture("tulip-field", "Tulip Field", "pictures/tulip-field.gmrg", 0x222c570f),
        BundledPicture("the-kiss", "The Kiss", "pictures/the-kiss.gmrg", 0x66c67fb9),
        BundledPicture("stained-glass-mandala", "Stained Glass Mandala", "pictures/stained-glass-mandala.gmrg", 0x0130d00d),
        BundledPicture("bougainvillea-street", "Bougainvillea Street", "pictures/bougainvillea-street.gmrg", 0x8946067b.toInt()),
        BundledPicture("riviera-hotel", "Riviera Hotel", "pictures/riviera-hotel.gmrg", 0x6b0ab171),
        BundledPicture("cherries", "Cherries and Milk Jug", "pictures/cherries.gmrg", 0x55c59736),
        BundledPicture("tuscan-hills", "Tuscan Hills", "pictures/tuscan-hills.gmrg", 0xaa90f883.toInt()),
        BundledPicture("market-stall", "Market Stall", "pictures/market-stall.gmrg", 0x1ded851b),
    )

    /** Big pictures (~15k gems, 36 colors): AI-generated photos (#8), CC0 and public-domain sources, then AI tiles (#31). */
    private val BIG = listOf(
        BundledPicture("puppy", "Golden Puppy", "pictures/puppy.gmrg", 0x4855ef0e),
        BundledPicture("cat", "Ginger Cat", "pictures/cat.gmrg", 0xa07064d5.toInt()),
        BundledPicture("couple", "Golden Hour", "pictures/couple.gmrg", 0x5dbac3b8),
        BundledPicture("sunflowers", "Sunflowers", "pictures/sunflowers.gmrg", 0x8f4f01c2.toInt()),
        BundledPicture("sunset", "Sail at Sunset", "pictures/sunset.gmrg", 0xd24e22ab.toInt()),
        BundledPicture("cottage", "Meadow Cottage", "pictures/cottage.gmrg", 0xf983e40c.toInt()),
        BundledPicture("plum-garden", "Plum Garden", "pictures/plum-garden.gmrg", 0x348af73d),
        BundledPicture("birch-forest", "Birch Forest", "pictures/birch-forest.gmrg", 0xd4761577.toInt()),
        BundledPicture("winter", "Winter", "pictures/winter.gmrg", 0x4cd05f92),
        BundledPicture("oranges-lemons", "Oranges and Lemons", "pictures/oranges-lemons.gmrg", 0x817424bb.toInt()),
        BundledPicture("oia", "Oia View", "pictures/oia.gmrg", 0xeb1efeae.toInt()),
        BundledPicture("lavender-hood", "Lavender and Mt Hood", "pictures/lavender-hood.gmrg", 0x92005528.toInt()),
        BundledPicture("flower-mandala", "Flower Mandala", "pictures/flower-mandala.gmrg", 0x27d77d02),
        BundledPicture("venice", "Venice at Sunset", "pictures/venice.gmrg", 0xda16812d.toInt()),
        BundledPicture("peonies", "Peonies", "pictures/peonies.gmrg", 0x989b4d3e.toInt()),
        BundledPicture("deco-peacock", "Art Deco Peacock", "pictures/deco-peacock.gmrg", 0xe2bf2689.toInt()),
        BundledPicture("japanese-garden", "Japanese Garden", "pictures/japanese-garden.gmrg", 0xc24cec43.toInt()),
        BundledPicture("northern-lights", "Northern Lights", "pictures/northern-lights.gmrg", 0x46c20b95),
        BundledPicture("misty-pier", "Misty Pier", "pictures/misty-pier.gmrg", 0x0215c44c),
    )

    /**
     * Gallery order: the starter, then Quick, Medium and Big (#31), the free ones first in each size (#32). The starter
     * is never locked. Sources and licenses: tools/bundled/PROVENANCE.md.
     */
    val ALL: List<BundledPicture> = listOf(STARTER) + listOf(QUICK, MEDIUM, BIG).flatMap { size ->
        size.map { it.copy(locked = it.id !in FREE) }.sortedBy { it.locked }
    }

    fun byId(id: String): BundledPicture? = ALL.firstOrNull { it.id == id }
}
