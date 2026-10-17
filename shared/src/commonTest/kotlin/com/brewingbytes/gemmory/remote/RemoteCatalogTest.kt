package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.RemoteFixtures.KEY
import com.brewingbytes.gemmory.remote.RemoteFixtures.catalogJson
import com.brewingbytes.gemmory.remote.RemoteFixtures.grid
import com.brewingbytes.gemmory.remote.RemoteFixtures.picture
import com.brewingbytes.gemmory.remote.RemoteFixtures.sign
import kotlin.test.Test
import kotlin.test.assertEquals
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
            "\"colors\": 30" to "\"colors\": 40",
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

    @Test
    fun jsonIsStrict() {
        for (bad in listOf("{\"a\":1,}", "{\"a\":1,\"a\":2}", "[01]", "\"\u0001\"", "{} {}", "[" .repeat(40) + "]".repeat(40), "tru")) {
            assertEquals(null, Json.parse(bad), bad)
        }
        assertEquals(Json.Arr(listOf(Json.Str("é\n\""), Json.Num("-1.5e3"), Json.Null)), Json.parse(""" [ "é\n\"", -1.5e3, null ] """))
    }
}
