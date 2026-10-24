package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.remote.CatalogCheck
import com.brewingbytes.gemmory.remote.CryptoAgainstJdkTest
import com.brewingbytes.gemmory.remote.GridCheck
import com.brewingbytes.gemmory.remote.RemoteCatalogs
import com.brewingbytes.gemmory.remote.RemoteCollection
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.EdECPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The publishing tool (#43): what it writes is exactly what the app accepts, and it refuses what the app would. */
class RemotePictureToolTest {
    private val root = Files.createTempDirectory("remote-tool").toFile()
    private val dir = File(root, "pictures/v1")
    private val provenance = File(root, "PROVENANCE.md")
    private val source = File("../tools/bundled/starter-starry-night.jpg")

    private fun keyFile(pair: KeyPair): File = File(root, "key-${pair.hashCode()}.pem").apply {
        writeText("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----\n")
    }

    private val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val trusted = listOf(CryptoAgainstJdkTest.rawPublicKey(pair.public as EdECPublicKey))

    private fun request(
        id: String,
        key: File = keyFile(pair),
        now: Long = 1_792_000_000,
        reserved: Set<String> = setOf("starter"),
        spec: String = "quick-3:4",
        minAppVersion: Int? = 12,
        locked: Boolean = true,
        colors: ColorChoice = ColorChoice.STANDARD,
        availableFrom: LocalDate? = null,
        rewardOf: String? = null,
        mystery: Boolean = false,
    ) = RemotePictureTool.Request(
        dir = dir, id = id, title = "Night $id", source = source, spec = spec, focus = 0.5, locked = locked,
        minAppVersion = minAppVersion, addedAt = LocalDate(2026, 10, 18), nowSeconds = now, signingKey = key,
        provenance = "starry.jpg (Wikimedia Commons) | Vincent van Gogh, 1889 | Public domain", provenanceFile = provenance,
        reviewPng = File(root, "$id.review.png"), reservedIds = reserved, trustedKeys = trusted, colors = colors,
        availableFrom = availableFrom, rewardOf = rewardOf, mystery = mystery,
    )

    private fun check() = RemoteCatalogs.check(
        File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, 12, null, setOf("starter"),
    )

    @Test
    fun publishWritesWhatTheAppAccepts() {
        val picture = RemotePictureTool.publish(request("night-one"))
        val catalog = assertIs<CatalogCheck.Ok>(check()).catalog
        assertEquals(listOf(picture), catalog.pictures)
        assertEquals(48 to 64, picture.width to picture.height)
        assertIs<GridCheck.Ok>(RemoteCatalogs.checkGrid(picture, File(dir, "grids/night-one.gmrg").readBytes()))
        assertTrue(File(root, "night-one.review.png").exists())
        assertTrue(provenance.readText().contains("| Night night-one | `night-one` | starry.jpg (Wikimedia Commons) |"))

        RemotePictureTool.publish(request("night-two", now = 1_700_000_000))
        val two = assertIs<CatalogCheck.Ok>(check()).catalog
        assertEquals(listOf("night-one", "night-two"), two.pictures.map { it.id })
        assertTrue(two.issuedAt > catalog.issuedAt, "issuedAt only moves forward, even with a clock behind")
        assertEquals(2, provenance.readLines().count { it.startsWith("| Night ") }, "rows appended, none lost")
    }

    /** #29: More colors converts at the size's More cap, and refuses an app version older apps would choke on. */
    @Test
    fun moreColorsNeedsTheMoreColorsApp() {
        val error = assertFailsWith<IllegalArgumentException> {
            RemotePictureTool.publish(request("night-rich", spec = "big-3:4", minAppVersion = 11, colors = ColorChoice.MORE))
        }
        assertTrue("-PminAppVersion=12" in error.message.orEmpty(), error.message)
        assertFalse(File(dir, "catalog.json").exists())

        val picture = RemotePictureTool.publish(request("night-rich", spec = "big-3:4", colors = ColorChoice.MORE))
        assertTrue(picture.colors in 37..60, "${picture.colors} colors")
        assertEquals(listOf(picture), assertIs<CatalogCheck.Ok>(check()).catalog.pictures)
        assertTrue(provenance.readText().contains("colors more), focus 0.5"))
    }

    /** T36: a dated drop names its date and an app that knows drops; it sorts by the day it opens. */
    @Test
    fun aDatedDropNeedsTheDropsApp() {
        val date = LocalDate(2026, 11, 2)
        val error = assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(request("night-drop", availableFrom = date)) }
        assertTrue("-PminAppVersion=${RemoteCatalogs.DROPS_MIN_APP}" in error.message.orEmpty(), error.message)
        assertFalse(File(dir, "catalog.json").exists())

        val picture = RemotePictureTool.publish(request("night-drop", minAppVersion = RemoteCatalogs.DROPS_MIN_APP, availableFrom = date))
        assertEquals(date, picture.availableFrom)
        assertEquals(date, picture.addedAt)
        val catalog = File(dir, "catalog.json").readBytes()
        val forDrops = RemoteCatalogs.check(catalog, File(dir, "catalog.json.sig").readBytes(), trusted, RemoteCatalogs.DROPS_MIN_APP, null, setOf("starter"))
        assertEquals(listOf(picture), assertIs<CatalogCheck.Ok>(forDrops).catalog.pictures)
        assertEquals(emptyList(), assertIs<CatalogCheck.Ok>(check()).catalog.pictures, "a 0.5.0 phone never sees it")
    }

    /** D14 (T44): left out, minAppVersion is the lowest app that reads the picture right. */
    @Test
    fun theVersionIsStampedFromTheFieldsUsed() {
        val date = LocalDate(2026, 11, 2)
        val plain = RemotePictureTool.publish(request("night-plain", minAppVersion = null))
        val rich = RemotePictureTool.publish(request("night-rich", spec = "big-3:4", minAppVersion = null, colors = ColorChoice.MORE))
        val drop = RemotePictureTool.publish(request("night-drop", minAppVersion = null, availableFrom = date))
        val both = RemotePictureTool.publish(request("night-both", spec = "big-3:4", minAppVersion = null, colors = ColorChoice.MORE, availableFrom = LocalDate(2026, 11, 3)))
        assertEquals(RemoteCatalogs.REMOTE_MIN_APP, plain.minAppVersion)
        assertEquals(RemoteCatalogs.MORE_COLORS_MIN_APP, rich.minAppVersion)
        assertEquals(RemoteCatalogs.DROPS_MIN_APP, drop.minAppVersion)
        assertEquals(RemoteCatalogs.DROPS_MIN_APP, both.minAppVersion)
        assertEquals(date, drop.addedAt, "addedAt = availableFrom")
        assertTrue(provenance.readText().contains("min app ${RemoteCatalogs.DROPS_MIN_APP}"))
        assertEquals(14, RemotePictureTool.publish(request("night-later", minAppVersion = 14)).minAppVersion, "a version may raise the stamp")
    }

    /** Fields an older app reads the same way never raise the stamp: the lock, and collections (T37, eng E6). */
    @Test
    fun fieldsAnOlderAppReadsRightDoNotRaiseIt() {
        val free = RemotePictureTool.publish(request("night-free", minAppVersion = null, locked = false))
        val locked = RemotePictureTool.publish(
            listOf(request("night-locked", minAppVersion = null, spec = "big-1:1")),
            listOf(RemoteCollection("nights", "Nights", listOf("night-free", "night-locked"))),
        ).single()
        assertEquals(listOf(RemoteCatalogs.REMOTE_MIN_APP, RemoteCatalogs.REMOTE_MIN_APP), listOf(free.minAppVersion, locked.minAppVersion))
        val old = RemoteCatalogs.check(
            File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, RemoteCatalogs.REMOTE_MIN_APP, null, setOf("starter"),
        )
        assertEquals(listOf("night-free", "night-locked"), assertIs<CatalogCheck.Ok>(old).catalog.pictures.map { it.id }, "the first remote app sees them")
    }

    /** T44: a batch is one catalog; a failure anywhere in it writes nothing at all. */
    @Test
    fun aBatchWritesEverythingOrNothing() {
        val tile = javax.imageio.ImageIO.read(source)
        val sourceFile = File(root, "tools/night-a.jpg")
        assertFailsWith<IllegalArgumentException> {
            RemotePictureTool.publish(
                listOf(
                    request("night-a", minAppVersion = null).withImage(tile, sourceFile),
                    request("Bad Id", minAppVersion = null),
                ),
            )
        }
        val stranger = keyFile(KeyPairGenerator.getInstance("Ed25519").generateKeyPair())
        assertFailsWith<IllegalStateException> {
            RemotePictureTool.publish(listOf(request("night-a", key = stranger, minAppVersion = null).withImage(tile, sourceFile), request("night-b", key = stranger, minAppVersion = null)))
        }
        assertFalse(dir.exists(), "no catalog, grid or folder")
        assertFalse(sourceFile.exists(), "no source")
        assertFalse(provenance.exists(), "no provenance row")
        assertFalse(File(root, "night-a.review.png").exists(), "no review")

        val published = RemotePictureTool.publish(listOf(request("night-a", minAppVersion = null).withImage(tile, sourceFile), request("night-b", minAppVersion = null)))
        assertEquals(listOf("night-a", "night-b"), assertIs<CatalogCheck.Ok>(check()).catalog.pictures.map { it.id })
        assertEquals(published, assertIs<CatalogCheck.Ok>(check()).catalog.pictures)
        assertTrue(sourceFile.exists() && javax.imageio.ImageIO.read(sourceFile) != null, "the tile's source is written once it verifies")
        assertEquals(2, provenance.readLines().count { it.startsWith("| Night ") })
    }

    /** CEO C4: one drop opens per date, counting the ones already published. */
    @Test
    fun twoDropsOnOneDateAreRefused() {
        val date = LocalDate(2026, 11, 2)
        assertFailsWith<IllegalStateException> {
            RemotePictureTool.publish(listOf(request("night-a", minAppVersion = null, availableFrom = date), request("night-b", minAppVersion = null, availableFrom = date)))
        }
        RemotePictureTool.publish(request("night-a", minAppVersion = null, availableFrom = date))
        val error = assertFailsWith<IllegalStateException> { RemotePictureTool.publish(request("night-b", minAppVersion = null, availableFrom = date)) }
        assertTrue("two drops on 2026-11-02" in error.message.orEmpty(), error.message)
    }

    /** R1 (eng D10) with D14: a 0.5.0 phone (12) reading a stamped batch sees only its undated pictures. */
    @Test
    fun anOldAppSeesNoStampedDropEarly() {
        RemotePictureTool.publish(
            listOf(
                request("night-plain", minAppVersion = null),
                request("night-drop", minAppVersion = null, availableFrom = LocalDate(2026, 11, 2)),
                request("night-next", minAppVersion = null, availableFrom = LocalDate(2026, 11, 3)),
            ),
        )
        assertEquals(listOf("night-plain"), assertIs<CatalogCheck.Ok>(check()).catalog.pictures.map { it.id })
        val drops = RemoteCatalogs.check(
            File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, RemoteCatalogs.DROPS_MIN_APP, null, setOf("starter"),
        )
        assertEquals(3, assertIs<CatalogCheck.Ok>(drops).catalog.pictures.size)
    }

    private fun RemotePictureTool.Request.withImage(image: java.awt.image.BufferedImage, file: File) = RemotePictureTool.Request(
        dir, id, title, file, spec, focus, locked, minAppVersion, addedAt, nowSeconds, signingKey, provenance, provenanceFile,
        reviewPng, reservedIds, trustedKeys, colors, availableFrom, sourceImage = image,
    )

    @Test
    fun anIdIsNeverReusedOrABundledOne() {
        RemotePictureTool.publish(request("night-one"))
        assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(request("night-one")) }
        assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(request("starter")) }
        assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(request("Bad Id")) }
    }

    @Test
    fun aKeyTheAppDoesNotTrustWritesNothing() {
        val stranger = keyFile(KeyPairGenerator.getInstance("Ed25519").generateKeyPair())
        val error = assertFailsWith<IllegalStateException> { RemotePictureTool.publish(request("night-one", key = stranger)) }
        assertTrue("isn't one the app trusts" in error.message.orEmpty())
        assertFalse(File(dir, "catalog.json").exists())
        assertFalse(File(dir, "grids/night-one.gmrg").exists())
        assertFalse(provenance.exists())
    }

    /** T37: a keepsake is stamped for the rewards app, needs its collection and no date, and an older app never lists it. */
    @Test
    fun aKeepsakeIsStampedAndNeedsItsCollection() {
        RemotePictureTool.publish(request("night-one"))
        val nights = RemoteCollection("nights", "Nights", listOf("night-one", "starter"))
        val keepsake = RemotePictureTool.publish(listOf(request("night-keepsake", minAppVersion = null, rewardOf = "nights")), listOf(nights)).single()
        assertEquals(RemoteCatalogs.REWARDS_MIN_APP, keepsake.minAppVersion)
        assertEquals("nights", keepsake.rewardOf)
        val at14 = RemoteCatalogs.check(File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, 14, null, setOf("starter"))
        assertEquals(listOf("night-one"), assertIs<CatalogCheck.Ok>(at14).catalog.pictures.map { it.id }, "0.6.1 never lists the keepsake")
        assertEquals(listOf(nights), assertIs<CatalogCheck.Ok>(at14).catalog.collections)

        assertFailsWith<IllegalStateException> { RemotePictureTool.publish(request("night-orphan", minAppVersion = null, rewardOf = "nowhere")) }
        assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(request("night-low", minAppVersion = 14, rewardOf = "nights")) }
        assertFailsWith<IllegalArgumentException> {
            RemotePictureTool.publish(request("night-dated", minAppVersion = null, rewardOf = "nights", availableFrom = LocalDate(2026, 11, 2)))
        }
        assertFailsWith<IllegalStateException>("one keepsake a collection") { RemotePictureTool.publish(request("night-second", minAppVersion = null, rewardOf = "nights")) }
        assertFalse(File(dir, "grids/night-orphan.gmrg").exists(), "a refused publish writes nothing")
    }

    /** T43: a mystery is a dated drop stamped for the mystery app; 0.7.0 (versionCode 15) never lists it. */
    @Test
    fun aMysteryIsStampedAndNeedsADate() {
        val date = LocalDate(2026, 11, 2)
        val mystery = RemotePictureTool.publish(listOf(request("night-plain", minAppVersion = null), request("night-mystery", minAppVersion = null, availableFrom = date, mystery = true)))[1]
        assertEquals(RemoteCatalogs.MYSTERY_MIN_APP, mystery.minAppVersion)
        assertTrue(mystery.mystery)
        val at15 = RemoteCatalogs.check(File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, 15, null, setOf("starter"))
        assertEquals(listOf("night-plain"), assertIs<CatalogCheck.Ok>(at15).catalog.pictures.map { it.id }, "0.7.0 never lists the mystery")

        val undated = assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(request("night-undated", minAppVersion = null, mystery = true)) }
        assertTrue("availableFrom" in undated.message.orEmpty(), undated.message)
        val low = assertFailsWith<IllegalArgumentException> {
            RemotePictureTool.publish(request("night-low", minAppVersion = 15, availableFrom = LocalDate(2026, 11, 3), mystery = true))
        }
        assertTrue("-PminAppVersion=${RemoteCatalogs.MYSTERY_MIN_APP}" in low.message.orEmpty(), low.message)
        assertFalse(File(dir, "grids/night-low.gmrg").exists(), "a refused publish writes nothing")
    }

    /** T37: a republish keeps the published collections; a batch adds new ones at the end or replaces one in place. */
    @Test
    fun collectionsAreKeptAndReplacedInPlace() {
        val a = RemoteCollection("first", "First", listOf("night-one"))
        val b = RemoteCollection("second", "Second", listOf("night-one"))
        RemotePictureTool.publish(listOf(request("night-one")), listOf(a, b))
        RemotePictureTool.publish(request("night-two"))
        assertEquals(listOf(a, b), assertIs<CatalogCheck.Ok>(check()).catalog.collections, "a plain publish keeps them")
        val a2 = a.copy(members = listOf("night-one", "night-two"))
        val c = RemoteCollection("third", "Third", listOf("night-two"))
        RemotePictureTool.publish(listOf(request("night-three")), listOf(c, a2))
        assertEquals(listOf(a2, b, c), assertIs<CatalogCheck.Ok>(check()).catalog.collections)
        RemotePictureTool.withdraw(dir, "night-three", 1_792_000_100, keyFile(pair), setOf("starter"), trusted)
        assertEquals(listOf(a2, b, c), assertIs<CatalogCheck.Ok>(check()).catalog.collections, "withdraw keeps them")
        // A typo in a member would leave it "coming" for ever, and a collection can't take a picture's id.
        assertFailsWith<IllegalStateException> { RemotePictureTool.publish(listOf(request("night-four")), listOf(RemoteCollection("typo", "Typo", listOf("nigth-one")))) }
        assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(listOf(request("night-five")), listOf(RemoteCollection("night-one", "Clash", listOf("night-two")))) }
    }

    /** T38: an event is 7 remote drops on 7 consecutive dates with exactly one exclusive; any other shape is refused. */
    @Test
    fun anEventIsSevenConsecutiveDropsWithOneExclusive() {
        val drops = (1..7).map { request("week-$it", minAppVersion = null, availableFrom = LocalDate(2026, 11, it + 1)) }
        val week = RemoteCollection("golden-week", "Golden Week", (1..7).map { "week-$it" }, event = true)
        val exclusive = request("week-exclusive", minAppVersion = null, rewardOf = "golden-week")
        val noExclusive = assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(drops, listOf(week)) }
        assertTrue("exclusive" in noExclusive.message.orEmpty(), noExclusive.message)
        val six = assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(drops + exclusive, listOf(week.copy(members = week.members.take(6)))) }
        assertTrue("7 members" in six.message.orEmpty(), six.message)
        val gap = drops.take(6) + request("week-7", minAppVersion = null, availableFrom = LocalDate(2026, 11, 9))
        val gapError = assertFailsWith<IllegalArgumentException> { RemotePictureTool.publish(gap + exclusive, listOf(week)) }
        assertTrue("consecutive" in gapError.message.orEmpty(), gapError.message)
        val undated = drops.take(6) + request("week-7")
        val undatedError = assertFailsWith<IllegalStateException> { RemotePictureTool.publish(undated + exclusive, listOf(week)) }
        assertTrue("availableFrom" in undatedError.message.orEmpty(), undatedError.message)
        assertFalse(File(dir, "catalog.json").exists(), "a refused publish writes nothing")

        RemotePictureTool.publish(drops + exclusive, listOf(week))
        val latest = { RemoteCatalogs.check(File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, Int.MAX_VALUE, null, setOf("starter")) }
        assertEquals(listOf(week), assertIs<CatalogCheck.Ok>(latest()).catalog.collections)
        // A later publish and a withdraw carry the flag through.
        RemotePictureTool.publish(request("night-one"))
        RemotePictureTool.withdraw(dir, "night-one", 1_792_000_100, keyFile(pair), setOf("starter"), trusted)
        assertTrue(assertIs<CatalogCheck.Ok>(latest()).catalog.collections.single().event)
    }

    /** T49 X4: setTitles adds translated titles to published entries, re-signed, and later publishes and withdraws keep them. */
    @Test
    fun translatedTitlesAreSetAndKept() {
        RemotePictureTool.publish(request("night-one"))
        RemotePictureTool.publish(request("night-two"))
        RemotePictureTool.setTitles(dir, mapOf("night-one" to mapOf("de" to "Nacht eins", "ro" to "Noapte ţ")), 1_792_000_100, keyFile(pair), setOf("starter"), trusted)
        RemotePictureTool.setTitles(dir, mapOf("night-one" to mapOf("ja" to "夜")), 1_792_000_200, keyFile(pair), setOf("starter"), trusted)
        val titles = { assertIs<CatalogCheck.Ok>(check()).catalog.pictures.first { it.id == "night-one" }.titles }
        assertEquals(mapOf("de" to "Nacht eins", "ro" to "Noapte ț", "ja" to "夜"), titles(), "a later run adds a language; ro normalized")
        RemotePictureTool.publish(request("night-three", now = 1_792_000_300))
        RemotePictureTool.withdraw(dir, "night-two", 1_792_000_400, keyFile(pair), setOf("starter"), trusted)
        assertEquals("夜", titles()["ja"])
        assertFailsWith<IllegalStateException> { RemotePictureTool.setTitles(dir, mapOf("nope" to mapOf("de" to "x")), 1_792_000_500, keyFile(pair), setOf("starter"), trusted) }
        assertFailsWith<IllegalStateException> { RemotePictureTool.setTitles(dir, mapOf("night-one" to mapOf("nl" to "x")), 1_792_000_500, keyFile(pair), setOf("starter"), trusted) }
    }

    @Test
    fun withdrawRemovesTheEntryAndItsGrid() {
        RemotePictureTool.publish(request("night-one"))
        RemotePictureTool.publish(request("night-two"))
        RemotePictureTool.withdraw(dir, "night-one", 1_792_000_100, keyFile(pair), setOf("starter"), trusted)
        assertEquals(listOf("night-two"), assertIs<CatalogCheck.Ok>(check()).catalog.pictures.map { it.id })
        assertFalse(File(dir, "grids/night-one.gmrg").exists())
    }

    /** T39: the review switch re-signs the catalog with a later issuedAt, and later publishes and withdraws keep it. */
    @Test
    fun theReviewSwitchFlipsAndStays() {
        RemotePictureTool.publish(request("night-one"))
        RemotePictureTool.publish(request("night-two"))
        val before = assertIs<CatalogCheck.Ok>(check()).catalog
        assertFalse(before.features.review)
        assertFalse("features" in File(dir, "catalog.json").readText(), "no switch on, no features key")

        RemotePictureTool.setFeature(dir, "review", true, 1_700_000_000, keyFile(pair), setOf("starter"), trusted)
        val on = assertIs<CatalogCheck.Ok>(check()).catalog
        assertTrue(on.features.review)
        assertTrue(on.issuedAt > before.issuedAt, "a phone's anti-rollback takes it even with a clock behind")
        assertEquals(before.pictures, on.pictures)

        RemotePictureTool.publish(request("night-three", now = 1_792_000_200))
        RemotePictureTool.withdraw(dir, "night-one", 1_792_000_300, keyFile(pair), setOf("starter"), trusted)
        assertTrue(assertIs<CatalogCheck.Ok>(check()).catalog.features.review)

        RemotePictureTool.setFeature(dir, "review", false, 1_792_000_400, keyFile(pair), setOf("starter"), trusted)
        assertFalse(assertIs<CatalogCheck.Ok>(check()).catalog.features.review)
        assertFailsWith<IllegalStateException> { RemotePictureTool.setFeature(dir, "holdout", true, 1_792_000_500, keyFile(pair), setOf("starter"), trusted) }
    }

    @Test
    fun theContentCheckIsSpelledOut() {
        assertTrue(RemotePictureTool.CONTENT_CHECK.any { "PEGI 3" in it })
        assertTrue(RemotePictureTool.CONTENT_CHECK.any { "adults" in it })
    }
}
