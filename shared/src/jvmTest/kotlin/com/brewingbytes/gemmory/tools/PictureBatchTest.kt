package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.remote.Titles
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.remote.CatalogCheck
import com.brewingbytes.gemmory.remote.CryptoAgainstJdkTest
import com.brewingbytes.gemmory.remote.RemoteCatalogs
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.interfaces.EdECPublicKey
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The batch run (T44): sheets + CSV in, one signed dated publish out, or nothing at all. */
class PictureBatchTest {
    private val root = Files.createTempDirectory("picture-batch").toFile()
    private val batchDir = File(root, "batches/2026-11-a").apply { mkdirs() }
    private val dir = File(root, "pictures/v1")
    private val sources = File(root, "tools/remote")
    private val provenance = File(sources, "PROVENANCE.md")
    private val previews = File(root, "build/previews")
    private val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val trusted = listOf(CryptoAgainstJdkTest.rawPublicKey(pair.public as EdECPublicKey))
    private val key = File(root, "key.pem").apply {
        writeText("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----\n")
    }
    private val today = LocalDate(2026, 10, 31)

    private val settings = PictureBatch.Settings(
        batchDir = batchDir, dir = dir, sourcesDir = sources, provenanceFile = provenance, signingKey = key,
        reservedIds = setOf("starter"), today = today, nowSeconds = 1_793_000_000, previewDir = previews, trustedKeys = trusted,
    )

    private val header = "sheet,tile,id,title,size,availableFrom,subject,generatedOn,contentChecked"

    private fun rows(checked: (Int) -> String = { "yes" }) = (1..6).joinToString("\n") { t ->
        "sheet-1.png,$t,garden-$t,\"Garden, No. $t\",quick-1:1,2026-11-0$t,a quiet garden $t,2026-10-30,${checked(t)}"
    }

    private fun write(csv: String) {
        ImageIO.write(SheetSplitterTest.sheet(1536, 1024, columns = 3, rows = 2), "png", File(batchDir, "sheet-1.png"))
        File(batchDir, PictureBatch.CSV).writeText("$header\n$csv\n")
    }

    @Test
    fun aCheckedBatchPublishesSixDatedPictures() {
        write(rows())
        val published = PictureBatch.run(settings)
        assertEquals((1..6).map { "garden-$it" }, published.pictures.map { it.id })
        assertTrue(published.pictures.all { it.minAppVersion == RemoteCatalogs.DROPS_MIN_APP && it.locked && it.addedAt == it.availableFrom })
        assertEquals("Garden, No. 1", published.pictures.first().title)
        assertEquals(DropCalendar.Result.Fail(6), published.calendar, "6 days from tomorrow: below 7, so the batch says so")

        val catalog = RemoteCatalogs.check(File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, RemoteCatalogs.DROPS_MIN_APP, null, setOf("starter"))
        assertEquals(published.pictures, assertIs<CatalogCheck.Ok>(catalog).catalog.pictures)
        assertTrue((1..6).all { File(sources, "garden-$it.jpg").exists() && File(sources, "garden-$it.review.png").exists() })
        val text = provenance.readText()
        assertEquals(6, text.lines().count { it.startsWith("| Garden, No. ") })
        assertTrue(text.contains("| `garden-3.jpg`: a quiet garden 3, tile 3 of `2026-11-a/sheet-1.png`, a 2×3 sheet by ChatGPT image generation (OpenAI), 2026-10-30, prompt `prompts/sheet-2x3.md` | AI-generated for BrewingBytes | OpenAI terms"), text)
    }

    /** T37: a `rewardOf` row is a collection's keepsake, `collections.csv` adds the collection, and the runway skips it. */
    @Test
    fun aBatchPublishesACollectionWithItsKeepsake() {
        ImageIO.write(SheetSplitterTest.sheet(1536, 1024, columns = 3, rows = 2), "png", File(batchDir, "sheet-1.png"))
        val dated = (1..5).joinToString("\n") { t -> "sheet-1.png,$t,garden-$t,Garden $t,quick-1:1,2026-11-0$t,a quiet garden $t,2026-10-30,yes," }
        File(batchDir, PictureBatch.CSV).writeText(
            "$header,rewardOf\n$dated\nsheet-1.png,6,garden-keepsake,Garden Gate,quick-1:1,,a garden gate,2026-10-30,yes,gardens\n",
        )
        File(batchDir, PictureBatch.COLLECTIONS_CSV).writeText("id,title,members\ngardens,Gardens,garden-1;garden-2;garden-3;garden-4;garden-5\n")
        val published = PictureBatch.run(settings)
        val keepsake = published.pictures.single { it.rewardOf != null }
        assertEquals("garden-keepsake" to RemoteCatalogs.REWARDS_MIN_APP, keepsake.id to keepsake.minAppVersion)
        assertNull(keepsake.availableFrom)
        assertEquals(DropCalendar.Result.Fail(5), published.calendar, "the keepsake isn't a drop")
        val catalog = RemoteCatalogs.check(File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, Int.MAX_VALUE, null, setOf("starter"))
        assertEquals(listOf("gardens"), assertIs<CatalogCheck.Ok>(catalog).catalog.collections.map { it.id })
        assertEquals((1..5).map { "garden-$it" }, assertIs<CatalogCheck.Ok>(catalog).catalog.collections.single().members)
    }

    @Test
    fun aDatedKeepsakeOrABadCollectionsFileIsRefused() {
        ImageIO.write(SheetSplitterTest.sheet(1536, 1024, columns = 3, rows = 2), "png", File(batchDir, "sheet-1.png"))
        File(batchDir, PictureBatch.CSV).writeText("$header,rewardOf\nsheet-1.png,1,garden-1,Garden 1,quick-1:1,2026-11-01,a garden,2026-10-30,yes,gardens\n")
        val dated = assertFailsWith<IllegalArgumentException> { PictureBatch.run(settings) }
        assertTrue(dated.message!!.contains("keepsake"), dated.message)
        assertFailsWith<IllegalArgumentException> { PictureBatch.readCollections("id,name,members\ngardens,Gardens,garden-1\n") }
        val bad = assertFailsWith<IllegalArgumentException> { PictureBatch.readCollections("id,title,members\nGardens!,Gardens,garden-1\nok,,garden-1\nok2,Ok,\n") }
        assertEquals(3, bad.message!!.lines().size, bad.message)
        assertFalse(dir.exists(), "nothing published")
    }

    /**
     * T49 X4: `title_<lang>` columns become the catalog's translated titles (Romanian normalized to ș / ț),
     * collections.csv takes them too by header name, and a title our bundled font can't draw is only a warning.
     */
    @Test
    fun titleColumnsBecomeTranslatedTitles() {
        ImageIO.write(SheetSplitterTest.sheet(1536, 1024, columns = 3, rows = 2), "png", File(batchDir, "sheet-1.png"))
        val dated = (1..6).joinToString("\n") { t ->
            val ja = if (t == 1) "庭園" else ""
            "sheet-1.png,$t,garden-$t,Garden $t,quick-1:1,2026-11-0$t,a quiet garden $t,2026-10-30,yes,Garten $t,Grădină ţ $t,$ja,"
        }
        File(batchDir, PictureBatch.CSV).writeText("$header,title_de,title_ro,title_ja,title_pt-BR\n$dated\n")
        File(batchDir, PictureBatch.COLLECTIONS_CSV).writeText("id,title,members,title_fr\ngardens,Gardens,garden-1;garden-2,Jardins\n")
        val warnings = ArrayList<String>()
        val published = PictureBatch.run(PictureBatch.Settings(
            batchDir = batchDir, dir = dir, sourcesDir = sources, provenanceFile = provenance, signingKey = key,
            reservedIds = setOf("starter"), today = today, nowSeconds = 1_793_000_000, previewDir = previews, trustedKeys = trusted,
            fontDir = File("../androidApp/src/main/res/font"), warn = { warnings += it },
        ))
        assertEquals(mapOf("de" to "Garten 1", "ro" to "Grădină ț 1", "ja" to "庭園"), published.pictures.first().titles)
        assertEquals(mapOf("de" to "Garten 2", "ro" to "Grădină ț 2"), published.pictures[1].titles, "blank columns stay on English")
        val catalog = assertIs<CatalogCheck.Ok>(RemoteCatalogs.check(File(dir, "catalog.json").readBytes(), File(dir, "catalog.json.sig").readBytes(), trusted, Int.MAX_VALUE, null, setOf("starter"))).catalog
        assertEquals(published.pictures, catalog.pictures)
        assertEquals(mapOf("fr" to "Jardins"), catalog.collections.single().titles)
        assertEquals(emptyList(), warnings, "every title draws in its language's faces")

        val glyphs = GlyphCheck(File("../androidApp/src/main/res/font")).warnings(mapOf("garden-9" to mapOf("de" to "Garten 庭")))
        assertTrue(glyphs.isNotEmpty() && glyphs.all { "garden-9 title_de" in it }, "$glyphs")
    }

    /** T49: the drafted titles for the live catalog read, cover every published picture and collection, and draw. */
    @Test
    fun theLiveCatalogsTitlesFileCoversItAndDraws() {
        val titles = PictureBatch.readTitles(File("../tools/remote/titles/2026-10-24-live.csv").readText())
        val catalog = RemoteCatalogs.parse(File("../pictures/v1/catalog.json").readBytes(), Int.MAX_VALUE)
        val live = assertIs<CatalogCheck.Ok>(catalog).catalog
        assertEquals((live.pictures.map { it.id } + live.collections.map { it.id }).toSet(), titles.keys)
        assertTrue(titles.values.all { it.keys == Titles.LANGUAGES.toSet() }, "every language")
        assertEquals(emptyList(), GlyphCheck(File("../androidApp/src/main/res/font")).warnings(titles))
    }

    @Test
    fun aBadTitleColumnIsRefused() {
        val e = assertFailsWith<IllegalArgumentException> {
            PictureBatch.read("$header,title_de,title_nl\nsheet-1.png,1,garden-1,Garden,quick-1:1,,a garden,2026-10-30,yes,Garten,Tuin\n")
        }
        assertTrue(e.message!!.contains("title_nl"), e.message)
        val long = "x".repeat(RemoteCatalogs.MAX_TITLE + 1)
        val e2 = assertFailsWith<IllegalArgumentException> { PictureBatch.read("$header,title_de\nsheet-1.png,1,garden-1,Garden,quick-1:1,,a garden,2026-10-30,yes,$long\n") }
        assertTrue(e2.message!!.contains("title_de"), e2.message)
        assertEquals(mapOf("garden-1" to mapOf("ja" to "庭園", "pt-BR" to "Jardim")), PictureBatch.readTitles("id,title_ja,title_pt-BR\ngarden-1,庭園,Jardim\n"))
        assertFailsWith<IllegalArgumentException> { PictureBatch.readTitles("id,title_xx\ngarden-1,Garden\n") }
    }

    /** T43: the optional `mystery` column makes a dated drop a mystery, stamped for the mystery app; undated is refused. */
    @Test
    fun theMysteryColumnFlagsADatedDrop() {
        ImageIO.write(SheetSplitterTest.sheet(1536, 1024, columns = 3, rows = 2), "png", File(batchDir, "sheet-1.png"))
        val dated = (1..6).joinToString("\n") { t -> "sheet-1.png,$t,garden-$t,Garden $t,quick-1:1,2026-11-0$t,a quiet garden $t,2026-10-30,yes,${if (t == 3) "yes" else ""}" }
        File(batchDir, PictureBatch.CSV).writeText("$header,mystery\n$dated\n")
        val published = PictureBatch.run(settings)
        assertEquals(listOf("garden-3"), published.pictures.filter { it.mystery }.map { it.id })
        assertEquals(RemoteCatalogs.MYSTERY_MIN_APP, published.pictures.single { it.mystery }.minAppVersion)
        assertTrue(published.pictures.filterNot { it.mystery }.all { it.minAppVersion == RemoteCatalogs.DROPS_MIN_APP }, "R1: the others keep their stamp")

        val undated = assertFailsWith<IllegalArgumentException> {
            PictureBatch.read("$header,mystery\na.png,1,x,X,quick-1:1,,s,2026-10-30,yes,maybe\n")
        }
        assertTrue("mystery must be yes" in undated.message.orEmpty(), undated.message)
    }

    @Test
    fun anUndatedMysteryIsRefused() {
        ImageIO.write(SheetSplitterTest.sheet(1536, 1024, columns = 3, rows = 2), "png", File(batchDir, "sheet-1.png"))
        File(batchDir, PictureBatch.CSV).writeText("$header,mystery\nsheet-1.png,1,garden-1,Garden 1,quick-1:1,,a garden,2026-10-30,yes,yes\n")
        val error = assertFailsWith<IllegalArgumentException> { PictureBatch.run(settings) }
        assertTrue("a mystery picture is a dated drop" in error.message.orEmpty(), error.message)
        assertFalse(dir.exists(), "nothing published")
    }

    /** T38: the optional `event` column flags a weekly event; a T37 file without it still reads. */
    @Test
    fun theEventColumnFlagsAWeeklyEvent() {
        val week = PictureBatch.readCollections("id,title,members,event\ngolden-week,Golden Week,w1;w2,yes\ngardens,Gardens,g1,\n")
        assertEquals(listOf(true, false), week.map { it.event })
        assertEquals(listOf(false), PictureBatch.readCollections("id,title,members\ngardens,Gardens,g1\n").map { it.event })
        val bad = assertFailsWith<IllegalArgumentException> { PictureBatch.readCollections("id,title,members,event\nweek,Week,w1,maybe\n") }
        assertTrue("event must be yes" in bad.message.orEmpty(), bad.message)
    }

    @Test
    fun theContentCheckStopsTheBatchUntilEveryRowSaysYes() {
        write(rows { if (it == 2 || it == 5) "no" else "yes" })
        val error = assertFailsWith<PictureBatch.NotChecked> { PictureBatch.run(settings) }
        assertEquals(listOf("garden-2", "garden-5"), error.unchecked)
        assertTrue(File(previews, "garden-2.review.png").exists(), "a preview to check against")
        assertFalse(dir.exists(), "nothing published")
        assertFalse(sources.exists(), "no sources or provenance")
    }

    @Test
    fun aSheetThatDoesNotSplitStopsTheBatch() {
        write(rows())
        ImageIO.write(SheetSplitterTest.sheet(1536, 1024, columns = 3, rows = 2, blank = setOf(0)), "png", File(batchDir, "sheet-1.png"))
        val error = assertFailsWith<IllegalArgumentException> { PictureBatch.run(settings) }
        assertTrue("sheet-1.png: found 5 tiles" in error.message.orEmpty(), error.message)
        assertFalse(dir.exists())
    }

    @Test
    fun rowsAreCheckedBeforeAnyImageWork() {
        write(
            "sheet-1.png,1,garden-1,One,quick-1:1,2026-10-31,a,2026-10-30,yes\n" +
                "sheet-1.png,1,garden-2,Two,quick-3:4,,b,2026-10-30,yes\n" +
                "sheet-2.png,2,garden-1,Three,quick-1:1,,c,2026-10-30,yes",
        )
        val message = assertFailsWith<IllegalArgumentException> { PictureBatch.run(settings) }.message.orEmpty()
        listOf("sheet-2.png isn't in", "sheet-1.png mixes aspects", "sheet-1.png tile 1 is on two rows", "garden-1 is on two rows", "2026-10-31 isn't after today").forEach {
            assertTrue(it in message, "missing \"$it\" in:\n$message")
        }
    }

    @Test
    fun theCsvHasDefaultsAndNamedErrors() {
        val rows = PictureBatch.read(
            "sheet,tile,id,title,size,subject,generatedOn,contentChecked,colors,locked,focus\n" +
                "a.png,4,fox,\"The \"\"Fox\"\"\",big-4:3,a fox,2026-10-30,YES,more,false,0.2\n" +
                "a.png,5,owl,Owl,big-4:3,an owl,2026-10-30,,,,\n",
        )
        val (fox, owl) = rows
        assertEquals("The \"Fox\"", fox.title)
        assertEquals(AspectRatio.LANDSCAPE, fox.ratio)
        assertEquals(ColorChoice.MORE, fox.colors)
        assertFalse(fox.locked)
        assertEquals(0.2, fox.focus)
        assertTrue(fox.contentChecked)
        assertEquals(ColorChoice.STANDARD, owl.colors)
        assertTrue(owl.locked, "drops default to locked (T36)")
        assertEquals(0.5, owl.focus)
        assertNull(owl.availableFrom)
        assertFalse(owl.contentChecked, "an empty check is a no")
        assertEquals(PictureBatch.DEFAULT_PROMPT, owl.prompt)

        val missing = assertFailsWith<IllegalArgumentException> { PictureBatch.read("sheet,tile,id\n") }
        assertTrue("missing columns" in missing.message.orEmpty())
        val bad = assertFailsWith<IllegalArgumentException> {
            PictureBatch.read("$header\na.png,7,x,X,huge-1:1,2026-13-01,s|t,2026-10-30,maybe\n")
        }
        assertTrue("line 2: " in bad.message.orEmpty(), bad.message)
    }
}
