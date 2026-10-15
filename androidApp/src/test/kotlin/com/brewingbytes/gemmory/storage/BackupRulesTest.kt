package com.brewingbytes.gemmory.storage

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Photo data never leaves the phone (AGENTS.md): the backup rules must exclude exactly the StorageLayout dirs, plus
 * Firebase's identifiers (#5).
 */
class BackupRulesTest {
    private val appData = setOf("file:${StorageLayout.PHOTO_CANVASES}/", "file:${StorageLayout.LEDGER}/")

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
