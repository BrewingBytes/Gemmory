package com.brewingbytes.gemmory.storage

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Photo data never leaves the phone (AGENTS.md): the backup rules must exclude exactly the StorageLayout dirs. */
class BackupRulesTest {
    private val expected = setOf("file:${StorageLayout.PHOTO_CANVASES}/", "file:${StorageLayout.LEDGER}/")

    private fun xml(name: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$name")).documentElement

    private fun rules(section: Element, tag: String): Set<String> {
        val nodes = section.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }.toSet()
    }

    @Test
    fun legacyBackupExcludesPhotoCanvasesAndTheLedger() {
        val root = xml("backup_rules.xml")
        assertEquals(expected, rules(root, "exclude"))
        assertTrue(rules(root, "include").isEmpty(), "an include would stop everything else being backed up")
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludePhotoCanvasesAndTheLedger() {
        val root = xml("data_extraction_rules.xml")
        for (name in listOf("cloud-backup", "device-transfer")) {
            val section = root.getElementsByTagName(name).item(0) as Element
            assertEquals(expected, rules(section, "exclude"), name)
            assertTrue(rules(section, "include").isEmpty(), name)
        }
    }

    @Test
    fun backupIsOnInTheManifest() {
        val app = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("application").item(0) as Element
        assertEquals("true", app.getAttributeNS("http://schemas.android.com/apk/res/android", "allowBackup"))
    }
}
