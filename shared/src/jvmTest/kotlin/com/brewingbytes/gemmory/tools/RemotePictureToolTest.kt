package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.remote.CatalogCheck
import com.brewingbytes.gemmory.remote.CryptoAgainstJdkTest
import com.brewingbytes.gemmory.remote.GridCheck
import com.brewingbytes.gemmory.remote.RemoteCatalogs
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

    private fun request(id: String, key: File = keyFile(pair), now: Long = 1_792_000_000, reserved: Set<String> = setOf("starter")) =
        RemotePictureTool.Request(
            dir = dir, id = id, title = "Night $id", source = source, spec = "quick-3:4", focus = 0.5, locked = true,
            minAppVersion = 12, addedAt = LocalDate(2026, 10, 18), nowSeconds = now, signingKey = key,
            provenance = "starry.jpg (Wikimedia Commons) | Vincent van Gogh, 1889 | Public domain", provenanceFile = provenance,
            reviewPng = File(root, "$id.review.png"), reservedIds = reserved, trustedKeys = trusted,
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

    @Test
    fun withdrawRemovesTheEntryAndItsGrid() {
        RemotePictureTool.publish(request("night-one"))
        RemotePictureTool.publish(request("night-two"))
        RemotePictureTool.withdraw(dir, "night-one", 1_792_000_100, keyFile(pair), setOf("starter"), trusted)
        assertEquals(listOf("night-two"), assertIs<CatalogCheck.Ok>(check()).catalog.pictures.map { it.id })
        assertFalse(File(dir, "grids/night-one.gmrg").exists())
    }

    @Test
    fun theContentCheckIsSpelledOut() {
        assertTrue(RemotePictureTool.CONTENT_CHECK.any { "PEGI 3" in it })
        assertTrue(RemotePictureTool.CONTENT_CHECK.any { "adults" in it })
    }
}
