package com.brewingbytes.gemmory.storage

import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.collections.CollectionPrefs
import com.brewingbytes.gemmory.reminder.ReminderPrefs
import com.brewingbytes.gemmory.review.ReviewPrefs
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Photo data never leaves the phone (AGENTS.md): the backup rules must exclude exactly the StorageLayout dirs, plus
 * Firebase's identifiers (#5) and the remote pictures' download cache (#43).
 */
class BackupRulesTest {
    private val appData = setOf("file:${StorageLayout.PHOTO_CANVASES}/", "file:${StorageLayout.LEDGER}/", "file:${StorageLayout.REMOTE}/")

    /**
     * Where Firebase BOM 34.19.0 keeps per-install state, read off a device: the Crashlytics installation UUID and
     * the Analytics app instance id (shared prefs), pending crash reports, session ids and the event queues. The
     * Firebase installation id is in no_backup/, which Android never backs up.
     */
    private val firebase = setOf(
        "sharedpref:com.google.firebase.crashlytics.xml",
        "sharedpref:com.google.android.gms.measurement.prefs.xml",
        "file:.crashlytics.v3/",
        "file:datastore/firebaseSessions/",
        "database:google_app_measurement.db",
        "database:google_app_measurement_local.db",
        "database:com.google.android.datatransport.events",
    )

    private val expected = appData + firebase

    private fun xml(name: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$name")).documentElement

    private fun rules(section: Element, tag: String): Set<String> {
        val nodes = section.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }.toSet()
    }

    @Test
    fun legacyBackupExcludesPhotoCanvasesTheLedgerAndFirebaseIds() {
        val root = xml("backup_rules.xml")
        assertEquals(expected, rules(root, "exclude"))
        assertTrue(rules(root, "include").isEmpty(), "an include would stop everything else being backed up")
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludePhotoCanvasesTheLedgerAndFirebaseIds() {
        val root = xml("data_extraction_rules.xml")
        for (name in listOf("cloud-backup", "device-transfer")) {
            val section = root.getElementsByTagName(name).item(0) as Element
            assertEquals(expected, rules(section, "exclude"), name)
            assertTrue(rules(section, "include").isEmpty(), name)
        }
    }

    /** #32: picture unlocks survive a reinstall, so their prefs file is never excluded (design doc, Locked pictures). */
    @Test
    fun pictureUnlocksAreBackedUp() {
        val unlocks = "sharedpref:${PictureUnlocks.FILE}.xml"
        assertTrue(unlocks !in rules(xml("backup_rules.xml"), "exclude"))
        val root = xml("data_extraction_rules.xml")
        for (name in listOf("cloud-backup", "device-transfer")) {
            assertTrue(unlocks !in rules(root.getElementsByTagName(name).item(0) as Element, "exclude"), name)
        }
    }

    /** #38: the inventory survives a reinstall like the unlocks: neither it nor a folder above it is excluded. */
    @Test
    fun theInventoryIsBackedUp() {
        val dir = StorageLayout.INVENTORY.substringBefore('/')
        assertTrue(!StorageLayout.INVENTORY.startsWith("${StorageLayout.LEDGER}/"))
        val sections = listOf(rules(xml("backup_rules.xml"), "exclude")) + listOf("cloud-backup", "device-transfer").map {
            rules(xml("data_extraction_rules.xml").getElementsByTagName(it).item(0) as Element, "exclude")
        }
        for (excluded in sections) {
            assertTrue(excluded.none { it == "file:$dir/" || it == "file:${StorageLayout.INVENTORY}" }, "$excluded")
        }
    }

    /**
     * #43: the download cache (catalog, state, grids) is excluded and can be fetched again; a started remote picture's
     * progress and grid copy are backed up like a bundled picture's.
     */
    @Test
    fun remoteCacheIsExcludedButRemoteProgressIsBackedUp() {
        for (path in listOf(StorageLayout.REMOTE_CATALOG, StorageLayout.REMOTE_SIGNATURE, StorageLayout.REMOTE_STATE, StorageLayout.REMOTE_GRIDS)) {
            assertTrue(path.startsWith("${StorageLayout.REMOTE}/"), path)
        }
        val sections = listOf(rules(xml("backup_rules.xml"), "exclude")) + listOf("cloud-backup", "device-transfer").map {
            rules(xml("data_extraction_rules.xml").getElementsByTagName(it).item(0) as Element, "exclude")
        }
        for (excluded in sections) {
            assertTrue("file:${StorageLayout.REMOTE}/" in excluded)
            assertTrue(excluded.none { StorageLayout.REMOTE_CANVASES.startsWith(it.removePrefix("file:").trimEnd('/')) }, "$excluded")
        }
        assertEquals("canvases/remote", CanvasKind.REMOTE.dir)
    }

    /**
     * T35 (eng D2, R1): the `play` file lives in its canvas directory, so a photo canvas's stays out of every backup
     * with it, while a picture's goes along with its progress. The reminder's settings may be backed up.
     */
    @Test
    fun aPlayFileFollowsItsCanvasAndReminderSettingsAreBackedUp() {
        val repo = CanvasRepository(File("files"))
        val photoPlay = File(repo.dirOf(CanvasRef(CanvasKind.PHOTO, "p1")), CanvasRepository.PLAY).path.removePrefix("files/")
        val sections = listOf(rules(xml("backup_rules.xml"), "exclude")) + listOf("cloud-backup", "device-transfer").map {
            rules(xml("data_extraction_rules.xml").getElementsByTagName(it).item(0) as Element, "exclude")
        }
        for (excluded in sections) {
            assertTrue(excluded.any { it.startsWith("file:") && photoPlay.startsWith(it.removePrefix("file:")) }, "$photoPlay in $excluded")
            assertTrue(excluded.none { it.startsWith("file:canvases/bundled") || it == "file:canvases/" }, "$excluded")
            assertTrue("sharedpref:${ReminderPrefs.FILE}.xml" !in excluded)
            // T39: the review counter goes along, so a restored phone doesn't start its three attempts over.
            assertTrue("sharedpref:${ReviewPrefs.FILE}.xml" !in excluded)
        }
    }

    /**
     * T37: a completed collection and its keepsake survive a reinstall: the completions and T38's event earns (so
     * nothing is earned twice), the unlocks naming the keepsake and the remote canvas holding it are all backed up and
     * transferred.
     */
    @Test
    fun collectionsAndTheirKeepsakesAreBackedUp() {
        val sections = listOf(rules(xml("backup_rules.xml"), "exclude")) + listOf("cloud-backup", "device-transfer").map {
            rules(xml("data_extraction_rules.xml").getElementsByTagName(it).item(0) as Element, "exclude")
        }
        for (excluded in sections) {
            assertTrue("sharedpref:${CollectionPrefs.FILE}.xml" !in excluded, "$excluded")
            assertTrue("sharedpref:${PictureUnlocks.FILE}.xml" !in excluded, "$excluded")
            assertTrue(excluded.none { StorageLayout.REMOTE_CANVASES.startsWith(it.removePrefix("file:").trimEnd('/')) }, "$excluded")
        }
    }

    @Test
    fun everyLedgerFileIsInsideTheExcludedDir() {
        // The allowance counter, the ad caps and the entitlement cache (#8): a restored cache would carry VIP to a
        // phone Play hasn't confirmed it on, so it never goes to cloud backup or device transfer.
        for (path in listOf(StorageLayout.LEDGER_DAILY, StorageLayout.LEDGER_ADS, StorageLayout.LEDGER_ENTITLEMENTS)) {
            assertTrue(path.startsWith("${StorageLayout.LEDGER}/"), path)
        }
        assertEquals("ledger/entitlements", StorageLayout.LEDGER_ENTITLEMENTS)
    }

    @Test
    fun backupIsOnInTheManifest() {
        val app = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("application").item(0) as Element
        assertEquals("true", app.getAttributeNS("http://schemas.android.com/apk/res/android", "allowBackup"))
    }
}
