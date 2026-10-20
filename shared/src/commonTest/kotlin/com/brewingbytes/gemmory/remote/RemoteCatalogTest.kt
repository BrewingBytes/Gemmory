package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.RemoteFixtures.KEY
import com.brewingbytes.gemmory.remote.RemoteFixtures.catalogJson
import com.brewingbytes.gemmory.remote.RemoteFixtures.grid
import com.brewingbytes.gemmory.remote.RemoteFixtures.picture
import com.brewingbytes.gemmory.remote.RemoteFixtures.sign
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RemoteCatalogTest {
    private val fox = grid(CanvasSize.BIG, AspectRatio.PORTRAIT, colors = 30)
    private val lanterns = grid(CanvasSize.MEDIUM, AspectRatio.LANDSCAPE, colors = 20)
    private val pictures = listOf(
        picture("fox-in-snow", fox, CanvasSize.BIG, AspectRatio.PORTRAIT, locked = false),
        picture("hot-air-lanterns", lanterns, CanvasSize.MEDIUM, AspectRatio.LANDSCAPE),
    )

    private fun check(bytes: ByteArray, sig: ByteArray = sign(bytes), appVersion: Int = 12, cached: Long? = null, reserved: Set<String> = setOf("starter", "lemons")) =
        RemoteCatalogs.check(bytes, sig, listOf(KEY), appVersion, cached, reserved)

    private fun refusal(c: CatalogCheck) = assertIs<CatalogCheck.Refused>(c).reason

    @Test
    fun aValidCatalogRoundTripsEveryField() {
        val ok = assertIs<CatalogCheck.Ok>(check(catalogJson(pictures)))
        assertEquals(pictures, ok.catalog.pictures)
        assertEquals(1_792_000_000, ok.catalog.issuedAt)
    }

    @Test
    fun aSignatureByAnotherKeyOrOverOtherBytesIsRefused() {
        val bytes = catalogJson(pictures)
        assertEquals(CatalogRefusal.BadSignature, refusal(check(bytes, sign(bytes, RemoteFixtures.OTHER_SEED))))
        val tampered = bytes.decodeToString().replace("\"locked\": true", "\"locked\": false").encodeToByteArray()
        assertEquals(CatalogRefusal.BadSignature, refusal(check(tampered, sign(bytes))))
        assertEquals(CatalogRefusal.BadSignature, refusal(check(bytes, "not base64!".encodeToByteArray())))
        assertEquals(CatalogRefusal.BadSignature, refusal(check(bytes, ByteArray(0))))
    }

    @Test
    fun anySecondKeyInTheListVerifies() {
        val bytes = catalogJson(pictures)
        val keys = listOf(Ed25519.publicKey(RemoteFixtures.OTHER_SEED), KEY)
        assertIs<CatalogCheck.Ok>(RemoteCatalogs.check(bytes, sign(bytes), keys, 12, null, emptySet()))
    }

    @Test
    fun gridsAreCheckedForHashFileCrcAndShape() {
        val entry = pictures[0]
        assertIs<GridCheck.Ok>(RemoteCatalogs.checkGrid(entry, fox))
        val flipped = fox.copyOf().also { it[100] = (it[100] + 1).toByte() }
        assertEquals(GridRefusal.BAD_HASH, assertIs<GridCheck.Refused>(RemoteCatalogs.checkGrid(entry, flipped)).reason)
        assertEquals(GridRefusal.BAD_HASH, assertIs<GridCheck.Refused>(RemoteCatalogs.checkGrid(entry, fox.copyOf(fox.size - 10))).reason)
        // Hash matches but the CRC listed is another grid's: the catalog was edited, or the grid re-frozen.
        assertEquals(GridRefusal.BAD_CRC, assertIs<GridCheck.Refused>(RemoteCatalogs.checkGrid(entry.copy(crc32 = entry.crc32 + 1), fox)).reason)
        assertEquals(GridRefusal.WRONG_SHAPE, assertIs<GridCheck.Refused>(RemoteCatalogs.checkGrid(entry.copy(colors = 29), fox)).reason)
        val junk = ByteArray(40) { 7 }
        assertEquals(GridRefusal.BAD_FILE, assertIs<GridCheck.Refused>(RemoteCatalogs.checkGrid(entry.copy(sha256 = Sha256.digest(junk).toHex()), junk)).reason)
    }

    @Test
    fun picturesForANewerAppAreLeftOutUnread() {
        val future = """{"id": "x", "minAppVersion": 40, "size": "giant"}"""
        val text = catalogJson(pictures).decodeToString().replaceFirst("\"pictures\": [", "\"pictures\": [\n    $future,")
        val bytes = text.encodeToByteArray()
        val ok = assertIs<CatalogCheck.Ok>(check(bytes, sign(bytes), appVersion = 12))
        assertEquals(listOf("fox-in-snow", "hot-air-lanterns"), ok.catalog.pictures.map { it.id })
        val older = catalogJson(listOf(pictures[0].copy(minAppVersion = 13), pictures[1]))
        assertEquals(listOf("hot-air-lanterns"), assertIs<CatalogCheck.Ok>(check(older, appVersion = 12)).catalog.pictures.map { it.id })
        assertEquals(2, assertIs<CatalogCheck.Ok>(check(older, appVersion = 13)).catalog.pictures.size)
    }

    /** #29: up to the size's More cap, and above Standard only for apps that take it (versionCode 12+). */
    @Test
    fun colorsAboveStandardNeedTheMoreColorsApp() {
        val rich = grid(CanvasSize.BIG, AspectRatio.PORTRAIT, colors = 60)
        val richPicture = picture("rich-fox", rich, CanvasSize.BIG, AspectRatio.PORTRAIT, minAppVersion = RemoteCatalogs.MORE_COLORS_MIN_APP)
        val ok = assertIs<CatalogCheck.Ok>(check(catalogJson(listOf(richPicture)), appVersion = 12)).catalog
        assertEquals(listOf(richPicture), ok.pictures)
        assertIs<GridCheck.Ok>(RemoteCatalogs.checkGrid(richPicture, rich))
        // An app before #29 never reads it, so its catalog stays whole.
        assertEquals(emptyList(), assertIs<CatalogCheck.Ok>(check(catalogJson(listOf(richPicture)), appVersion = 11)).catalog.pictures)
        // Listed for older apps, it would make them refuse the whole catalog, so it's malformed here too.
        val forOld = picture("rich-fox", grid(CanvasSize.BIG, AspectRatio.PORTRAIT, colors = 37), CanvasSize.BIG, AspectRatio.PORTRAIT, minAppVersion = 11)
        assertIs<CatalogRefusal.Malformed>(refusal(check(catalogJson(listOf(forOld)), appVersion = 12)))
        // Standard and below stay open to every app.
        val standard = picture("std-fox", grid(CanvasSize.BIG, AspectRatio.PORTRAIT, colors = 36), CanvasSize.BIG, AspectRatio.PORTRAIT, minAppVersion = 11)
        assertEquals(1, assertIs<CatalogCheck.Ok>(check(catalogJson(listOf(standard)), appVersion = 11)).catalog.pictures.size)
        val quickMore = picture("quick-more", grid(CanvasSize.QUICK, AspectRatio.SQUARE, colors = 25), CanvasSize.QUICK, AspectRatio.SQUARE)
        assertIs<CatalogRefusal.Malformed>(refusal(check(catalogJson(listOf(quickMore)))))
    }

    @Test
    fun anOlderCatalogThanTheCachedOneIsRefused() {
        val bytes = catalogJson(pictures, issuedAt = 100)
        assertEquals(CatalogRefusal.Rollback(100, 101), refusal(check(bytes, cached = 101)))
        assertIs<CatalogCheck.Ok>(check(bytes, cached = 100))
    }

    @Test
    fun aBundledIdOrARepeatedIdIsRefused() {
        assertEquals(CatalogRefusal.IdClash("lemons"), refusal(check(catalogJson(listOf(picture("lemons", fox, CanvasSize.BIG, AspectRatio.PORTRAIT))))))
        assertEquals(CatalogRefusal.IdClash("fox-in-snow"), refusal(check(catalogJson(pictures + pictures[0]))))
    }

    @Test
    fun malformedCatalogsAreRefused() {
        fun refusedText(text: String) = refusal(check(text.encodeToByteArray()))
        val good = catalogJson(pictures).decodeToString()
        assertIs<CatalogRefusal.Malformed>(refusedText("not json"))
        assertIs<CatalogRefusal.Malformed>(refusedText("[]"))
        assertIs<CatalogRefusal.Malformed>(refusedText(good.dropLast(1)))
        assertIs<CatalogRefusal.Malformed>(refusedText(good.replace("\"issuedAt\"", "\"issued\"")))
        assertEquals(CatalogRefusal.UnsupportedVersion(2), refusedText(good.replaceFirst("\"version\": 1", "\"version\": 2")))
        // Field by field: a wrong type, size, shape, path or date refuses the whole catalog (it's signed: a tool bug).
        val edits = listOf(
            "\"width\": 106" to "\"width\": 107",
            "\"colors\": 30" to "\"colors\": 61",
            "\"ratio\": \"3:4\"" to "\"ratio\": \"2:3\"",
            "\"size\": \"big\"" to "\"size\": \"huge\"",
            "\"grid\": \"grids/fox-in-snow.gmrg\"" to "\"grid\": \"https://evil.example/x.gmrg\"",
            "\"id\": \"fox-in-snow\"" to "\"id\": \"../fox\"",
            "\"addedAt\": \"2026-10-18\"" to "\"addedAt\": \"2026-13-40\"",
            "\"locked\": false" to "\"locked\": \"no\"",
            "\"minAppVersion\": 12" to "\"minAppVersion\": 12.5",
            "\"title\": \"Fox-in-snow\"" to "\"title\": \"\"",
        )
        for ((from, to) in edits) {
            check(good.contains(from)) { "fixture lacks $from" }
            assertIs<CatalogRefusal.Malformed>(refusedText(good.replaceFirst(from, to)), to)
        }
        assertIs<CatalogRefusal.Malformed>(refusal(check(ByteArray(RemoteCatalogs.MAX_CATALOG_BYTES + 1))))
    }

    /** T36: a dated drop names `availableFrom` and an app that knows drops; an undated entry is written as before. */
    @Test
    fun aDropsDateRoundTripsAndNeedsTheDropsApp() {
        val drop = picture("lighthouse", lanterns, CanvasSize.MEDIUM, AspectRatio.LANDSCAPE, minAppVersion = RemoteCatalogs.DROPS_MIN_APP, availableFrom = "2026-11-02")
        val bytes = catalogJson(pictures + drop)
        assertEquals(pictures + drop, assertIs<CatalogCheck.Ok>(check(bytes, appVersion = RemoteCatalogs.DROPS_MIN_APP)).catalog.pictures)
        assertFalse(catalogJson(pictures).decodeToString().contains("availableFrom"), "undated entries keep the 0.5.0 bytes")
        val good = bytes.decodeToString()
        for ((from, to) in listOf(
            "\"availableFrom\": \"2026-11-02\"" to "\"availableFrom\": \"2026-11-31\"",
            "\"availableFrom\": \"2026-11-02\"" to "\"availableFrom\": 20261102",
            "\"availableFrom\": \"2026-11-02\"" to "\"availableFrom\": null",
        )) {
            check(good.contains(from)) { "fixture lacks $from" }
            val edited = good.replaceFirst(from, to).encodeToByteArray()
            assertIs<CatalogRefusal.Malformed>(refusal(check(edited, appVersion = RemoteCatalogs.DROPS_MIN_APP)), to)
        }
        // Listed for an app that doesn't know drops, it would show early there, so it's malformed here too.
        val early = catalogJson(listOf(drop.copy(minAppVersion = RemoteCatalogs.DROPS_MIN_APP - 1)))
        assertIs<CatalogRefusal.Malformed>(refusal(check(early, appVersion = RemoteCatalogs.DROPS_MIN_APP)))
    }

    /**
     * R1 (eng D10): a 0.5.0 phone (versionCode 12) reading a catalog with dated drops sees only the undated pictures,
     * so nothing it lists is new or early. The catalog below is pinned text, as T44 will publish it.
     */
    @Test
    fun aZeroFiveZeroPhoneSeesNoDropInADatedCatalog() {
        val golden = """
            {
              "version": 1,
              "issuedAt": 1792000000,
              "pictures": [
                {"id": "harbor", "title": "Harbor", "ratio": "1:1", "size": "quick", "width": 56, "height": 56, "colors": 4,
                 "grid": "grids/harbor.gmrg", "sha256": "${"0".repeat(64)}", "crc32": "0a0b0c0d", "locked": true,
                 "minAppVersion": 12, "addedAt": "2026-10-18"},
                {"id": "lighthouse", "title": "Lighthouse", "ratio": "1:1", "size": "quick", "width": 56, "height": 56, "colors": 4,
                 "grid": "grids/lighthouse.gmrg", "sha256": "${"1".repeat(64)}", "crc32": "01020304", "locked": true,
                 "minAppVersion": 13, "addedAt": "2026-11-02", "availableFrom": "2026-11-02"}
              ]
            }
        """.trimIndent().encodeToByteArray()
        assertEquals(listOf("harbor"), assertIs<CatalogCheck.Ok>(RemoteCatalogs.parse(golden, appVersion = 12)).catalog.pictures.map { it.id })
        val now = assertIs<CatalogCheck.Ok>(RemoteCatalogs.parse(golden, appVersion = RemoteCatalogs.DROPS_MIN_APP)).catalog.pictures
        assertEquals(listOf(null, LocalDate(2026, 11, 2)), now.map { it.availableFrom })
    }

    /** T37: collections parse in order with their members, which need not be listed (newer app, withdrawn, bundled). */
    @Test
    fun collectionsParseWithUnknownMembersAndTheEventFlag() {
        val seaside = RemoteCollection("seaside", "Seaside", listOf("fox-in-snow", "lemons", "not-published-yet"))
        val week = RemoteCollection("autumn-week", "Autumn Week", listOf("hot-air-lanterns"), event = true)
        val bytes = catalogJson(pictures, collections = listOf(seaside, week))
        val catalog = assertIs<CatalogCheck.Ok>(check(bytes)).catalog
        assertEquals(listOf(seaside, week), catalog.collections)
        assertEquals(pictures, catalog.pictures)
        assertFalse(catalogJson(pictures).decodeToString().contains("collections"), "no collections, no key")
        assertFalse(bytes.decodeToString().substringBefore("autumn-week").contains("\"event\""), "event is written only when true")
    }

    /** T37 (eng D7): collection ids share one namespace apart from pictures and bundled ids, and are listed once. */
    @Test
    fun aCollectionIdThatClashesIsRefused() {
        fun clash(c: RemoteCollection, vararg more: RemoteCollection) =
            assertIs<CatalogRefusal.IdClash>(refusal(check(catalogJson(pictures, collections = listOf(c) + more)))).id
        assertEquals("fox-in-snow", clash(RemoteCollection("fox-in-snow", "Foxes", listOf("fox-in-snow"))))
        assertEquals("lemons", clash(RemoteCollection("lemons", "Lemons", listOf("fox-in-snow"))))
        assertEquals("seaside", clash(RemoteCollection("seaside", "A", listOf("fox-in-snow")), RemoteCollection("seaside", "B", listOf("lemons"))))
    }

    @Test
    fun aMalformedCollectionRefusesTheCatalog() {
        val good = catalogJson(pictures, collections = listOf(RemoteCollection("seaside", "Seaside", listOf("fox-in-snow", "lemons")))).decodeToString()
        for ((from, to) in listOf(
            "\"id\": \"seaside\"" to "\"id\": \"Sea Side\"",
            "\"title\": \"Seaside\"" to "\"title\": \"\"",
            "\"lemons\"\n" to "\"fox-in-snow\"\n",
            "\"members\": [" to "\"members\": 3, \"x\": [",
        )) {
            check(good.contains(from)) { "fixture lacks $from" }
            assertIs<CatalogRefusal.Malformed>(refusal(check(good.replaceFirst(from, to).encodeToByteArray())), to)
        }
        val empty = good.replace(Regex("\"members\": \\[[^\\]]*\\]"), "\"members\": []")
        assertIs<CatalogRefusal.Malformed>(refusal(check(empty.encodeToByteArray())))
        val badEvent = good.replaceFirst("\"title\": \"Seaside\"", "\"title\": \"Seaside\", \"event\": \"yes\"")
        assertIs<CatalogRefusal.Malformed>(refusal(check(badEvent.encodeToByteArray())))
        assertIs<CatalogRefusal.Malformed>(refusal(check(catalogJson(pictures).decodeToString().replaceFirst("\"pictures\"", "\"collections\": {}, \"pictures\"").encodeToByteArray())))
    }

    /** T37: a keepsake names its collection and an app that knows keepsakes; it round-trips through the tool's entry. */
    @Test
    fun aKeepsakeRoundTripsAndNeedsTheRewardsApp() {
        val keepsake = picture("harbour-lights", lanterns, CanvasSize.MEDIUM, AspectRatio.LANDSCAPE, minAppVersion = RemoteCatalogs.REWARDS_MIN_APP, rewardOf = "seaside")
        val bytes = catalogJson(pictures + keepsake)
        assertEquals(pictures + keepsake, assertIs<CatalogCheck.Ok>(check(bytes, appVersion = RemoteCatalogs.REWARDS_MIN_APP)).catalog.pictures)
        assertFalse(catalogJson(pictures).decodeToString().contains("rewardOf"), "ordinary entries keep their bytes")
        val early = catalogJson(listOf(keepsake.copy(minAppVersion = RemoteCatalogs.REWARDS_MIN_APP - 1)))
        assertIs<CatalogRefusal.Malformed>(refusal(check(early, appVersion = RemoteCatalogs.REWARDS_MIN_APP)))
        val badId = bytes.decodeToString().replace("\"rewardOf\": \"seaside\"", "\"rewardOf\": \"Sea side\"").encodeToByteArray()
        assertIs<CatalogRefusal.Malformed>(refusal(check(badId, appVersion = RemoteCatalogs.REWARDS_MIN_APP)))
        assertEquals(RemoteCatalogs.REWARDS_MIN_APP, RemoteCatalogs.lowestApp(keepsake.copy(minAppVersion = 0)))
    }

    /**
     * R1 (eng D10): 0.6.1 (versionCode 14) reading a catalog with collections and a keepsake lists every ordinary
     * picture as before and never the keepsake, so it can't show an unearned one. The catalog is pinned text.
     */
    @Test
    fun aZeroSixOnePhoneNeverSeesAKeepsake() {
        val golden = """
            {
              "version": 1,
              "issuedAt": 1792000000,
              "pictures": [
                {"id": "harbor", "title": "Harbor", "ratio": "1:1", "size": "quick", "width": 56, "height": 56, "colors": 4,
                 "grid": "grids/harbor.gmrg", "sha256": "${"0".repeat(64)}", "crc32": "0a0b0c0d", "locked": true,
                 "minAppVersion": 12, "addedAt": "2026-10-18"},
                {"id": "harbour-lights", "title": "Harbour Lights", "ratio": "1:1", "size": "quick", "width": 56, "height": 56, "colors": 4,
                 "grid": "grids/harbour-lights.gmrg", "sha256": "${"1".repeat(64)}", "crc32": "01020304", "locked": true,
                 "minAppVersion": 15, "addedAt": "2026-11-02", "rewardOf": "seaside"}
              ],
              "collections": [{"id": "seaside", "title": "Seaside", "members": ["harbor"]}]
            }
        """.trimIndent().encodeToByteArray()
        assertEquals(listOf("harbor"), assertIs<CatalogCheck.Ok>(RemoteCatalogs.parse(golden, appVersion = 14)).catalog.pictures.map { it.id })
        val now = assertIs<CatalogCheck.Ok>(RemoteCatalogs.parse(golden, appVersion = RemoteCatalogs.REWARDS_MIN_APP)).catalog
        assertEquals(listOf(null, "seaside"), now.pictures.map { it.rewardOf })
        assertEquals(listOf("seaside"), now.collections.map { it.id })
    }

    @Test
    fun jsonIsStrict() {
        for (bad in listOf("{\"a\":1,}", "{\"a\":1,\"a\":2}", "[01]", "\"\u0001\"", "{} {}", "[" .repeat(40) + "]".repeat(40), "tru")) {
            assertEquals(null, Json.parse(bad), bad)
        }
        assertEquals(Json.Arr(listOf(Json.Str("é\n\""), Json.Num("-1.5e3"), Json.Null)), Json.parse(""" [ "é\n\"", -1.5e3, null ] """))
    }
}
