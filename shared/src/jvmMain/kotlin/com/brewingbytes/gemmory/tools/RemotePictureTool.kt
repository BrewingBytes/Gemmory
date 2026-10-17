package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.CatalogCheck
import com.brewingbytes.gemmory.remote.GridCheck
import com.brewingbytes.gemmory.remote.Json
import com.brewingbytes.gemmory.remote.RemoteCatalog
import com.brewingbytes.gemmory.remote.RemoteCatalogs
import com.brewingbytes.gemmory.remote.RemoteKeys
import com.brewingbytes.gemmory.remote.RemotePicture
import com.brewingbytes.gemmory.remote.Sha256
import com.brewingbytes.gemmory.remote.toHex
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import kotlinx.datetime.LocalDate
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Build-time only (#43): publishes a remote picture into the catalog folder (`pictures/v1/` in this repo). It freezes
 * the source like [BundledGridTool], adds the catalog entry (CRC, SHA-256, size, colors), signs the catalog with the
 * Ed25519 key kept outside every repo, then checks the result with the app's own verifier ([RemoteCatalogs.check],
 * [RemoteKeys.PRODUCTION]) before writing anything, and adds the provenance row. [withdraw] removes a picture: it
 * leaves every phone that hasn't started it.
 *
 * Grids are immutable: an id is never reused for another grid. A fix is a new id, and the old one withdrawn.
 */
object RemotePictureTool {
    /** The questions every picture answers "yes" to before it's published (README → Publishing remote pictures). */
    val CONTENT_CHECK = listOf(
        "Fits the current rating (PEGI 3 / Everyone): no violence, fear, nudity, drugs, gambling or crude humour.",
        "Made for adults (AGENTS.md): no cartoon mascots, nursery or toy styles, nothing aimed at children.",
        "No identifiable real person, logo, brand or protected design (a car, a scooter, a building's trade dress).",
        "Source and license recorded in the provenance row, with commercial use allowed.",
    )

    class Request(
        val dir: File,
        val id: String,
        val title: String,
        val source: File,
        val spec: String,
        val focus: Double,
        val locked: Boolean,
        val minAppVersion: Int,
        val addedAt: LocalDate,
        val nowSeconds: Long,
        val signingKey: File,
        /** "<source> | <author, date> | <license>", as in tools/bundled/PROVENANCE.md. */
        val provenance: String,
        val provenanceFile: File,
        val reviewPng: File?,
        val reservedIds: Set<String>,
        val trustedKeys: List<ByteArray> = RemoteKeys.PRODUCTION,
    )

    fun publish(r: Request): RemotePicture {
        require(RemoteCatalogs.ID.matches(r.id)) { "id must match ${RemoteCatalogs.ID.pattern}" }
        require(r.id !in r.reservedIds) { "${r.id} is a bundled picture's id" }
        require(r.title.isNotBlank() && r.title.length <= RemoteCatalogs.MAX_TITLE) { "title must be 1-${RemoteCatalogs.MAX_TITLE} characters" }
        require(r.provenance.count { it == '|' } == 2) { "provenance must be \"<source> | <author, date> | <license>\"" }
        val spec = requireNotNull(BundledGridTool.SPECS[r.spec]?.takeIf { it.id != "starter-3:4" }) { "spec must be one of the picker's: ${BundledGridTool.SPECS.keys - "starter-3:4"}" }
        val (old, oldIssued) = current(r.dir, r.trustedKeys, r.reservedIds)
        require(old.none { it.id == r.id }) { "${r.id} is already published; grids are immutable, so use a new id" }
        val gridFile = File(r.dir, RemoteCatalogs.gridPath(r.id))
        require(!gridFile.exists()) { "$gridFile exists; ids are never reused" }

        val image = ImageIO.read(r.source) ?: error("cannot read image ${r.source}")
        val pattern = BundledGridTool.convertImage(image, spec, r.focus)
        val grid = GridCodec.encode(pattern)
        val crc = (GridCodec.decode(grid) as Decoded.Ok).value.crc32
        val (sizeName, ratioLabel) = spec.id.split('-')
        val picture = RemotePicture(
            id = r.id,
            title = r.title,
            ratio = AspectRatio.entries.first { it.label == ratioLabel },
            size = CanvasSize.valueOf(sizeName.uppercase()),
            width = pattern.width,
            height = pattern.height,
            colors = pattern.palette.size,
            grid = RemoteCatalogs.gridPath(r.id),
            sha256 = Sha256.digest(grid).toHex(),
            crc32 = crc,
            locked = r.locked,
            minAppVersion = r.minAppVersion,
            addedAt = r.addedAt,
        )
        val issuedAt = maxOf(r.nowSeconds, (oldIssued ?: 0) + 1)
        val catalog = catalogBytes(RemoteCatalog(issuedAt, old + picture))
        val signature = sign(catalog, r.signingKey)
        verify(catalog, signature, oldIssued, r, picture, grid)

        gridFile.parentFile.mkdirs()
        gridFile.writeBytes(grid)
        File(r.dir, "catalog.json").writeBytes(catalog)
        File(r.dir, "catalog.json.sig").writeText(RemoteCatalogs.encodeSignature(signature))
        r.reviewPng?.let { it.parentFile?.mkdirs(); ImageIO.write(BundledGridTool.preview(pattern), "png", it) }
        appendProvenance(r.provenanceFile, picture, r.provenance, r.spec, r.focus)
        return picture
    }

    /** Removes [id] from the catalog and deletes its grid; phones that started it keep their own copy. */
    fun withdraw(dir: File, id: String, nowSeconds: Long, signingKey: File, reservedIds: Set<String>, trustedKeys: List<ByteArray> = RemoteKeys.PRODUCTION) {
        val (old, oldIssued) = current(dir, trustedKeys, reservedIds)
        require(old.any { it.id == id }) { "$id isn't in the catalog" }
        val catalog = catalogBytes(RemoteCatalog(maxOf(nowSeconds, (oldIssued ?: 0) + 1), old.filter { it.id != id }))
        val signature = sign(catalog, signingKey)
        val check = RemoteCatalogs.check(catalog, RemoteCatalogs.encodeSignature(signature).encodeToByteArray(), trustedKeys, Int.MAX_VALUE, oldIssued, reservedIds)
        check(check is CatalogCheck.Ok) { "the app would refuse this catalog: $check" }
        File(dir, "catalog.json").writeBytes(catalog)
        File(dir, "catalog.json.sig").writeText(RemoteCatalogs.encodeSignature(signature))
        File(dir, RemoteCatalogs.gridPath(id)).delete()
    }

    /** The published catalog, which must itself verify (so the tool never builds on a tampered one). */
    private fun current(dir: File, keys: List<ByteArray>, reserved: Set<String>): Pair<List<RemotePicture>, Long?> {
        val file = File(dir, "catalog.json")
        if (!file.exists()) return emptyList<RemotePicture>() to null
        val check = RemoteCatalogs.check(file.readBytes(), File(dir, "catalog.json.sig").readBytes(), keys, Int.MAX_VALUE, null, reserved)
        check(check is CatalogCheck.Ok) { "the published catalog doesn't verify: $check" }
        return check.catalog.pictures to check.catalog.issuedAt
    }

    fun catalogBytes(c: RemoteCatalog): ByteArray = (
        Json.write(
            Json.Obj(
                linkedMapOf(
                    "version" to Json.Num(RemoteCatalogs.VERSION.toString()),
                    "issuedAt" to Json.Num(c.issuedAt.toString()),
                    "pictures" to Json.Arr(c.pictures.map { RemoteCatalogs.entry(it) }),
                ),
            ),
        ) + "\n"
        ).encodeToByteArray()

    /** The app's checks, run on exactly the bytes about to be written. */
    private fun verify(catalog: ByteArray, signature: ByteArray, oldIssued: Long?, r: Request, picture: RemotePicture, grid: ByteArray) {
        val sig = RemoteCatalogs.encodeSignature(signature).encodeToByteArray()
        val check = RemoteCatalogs.check(catalog, sig, r.trustedKeys, r.minAppVersion, oldIssued, r.reservedIds)
        check(check is CatalogCheck.Ok) {
            if (check == CatalogCheck.Refused(com.brewingbytes.gemmory.remote.CatalogRefusal.BadSignature)) {
                "the signing key isn't one the app trusts (RemoteKeys.PRODUCTION)"
            } else {
                "the app would refuse this catalog: $check"
            }
        }
        val listed = check.catalog.pictures.single { it.id == picture.id }
        check(RemoteCatalogs.checkGrid(listed, grid) is GridCheck.Ok) { "the app would refuse this grid" }
    }

    /** Ed25519 with the JDK, from a PKCS#8 key (PEM or DER), as `openssl genpkey -algorithm ed25519` writes it. */
    fun sign(data: ByteArray, keyFile: File): ByteArray {
        val raw = keyFile.readBytes()
        val text = raw.decodeToString()
        val der = if ("-----BEGIN" in text) {
            Base64.getMimeDecoder().decode(text.lines().filter { !it.startsWith("-----") }.joinToString(""))
        } else {
            raw
        }
        val key: PrivateKey = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(der))
        return Signature.getInstance("Ed25519").run {
            initSign(key)
            update(data)
            sign()
        }
    }

    /** Read first, then write (lessons, #31: a one-liner once truncated PROVENANCE.md). */
    private fun appendProvenance(file: File, p: RemotePicture, provenance: String, spec: String, focus: Double) {
        val before = if (file.exists()) file.readText() else HEADER
        val (source, author, license) = provenance.split('|').map { it.trim() }
        val row = "| ${p.title} | `${p.id}` | $source | $author | $license | spec `$spec` (${p.width}×${p.height}, ${p.colors} colors), focus $focus, seed 0x474D5259 | ${p.addedAt} | ${if (p.locked) "locked" else "free"}, min app ${p.minAppVersion}, CRC `${p.crc32.toUInt().toString(16).padStart(8, '0')}` |\n"
        file.parentFile?.mkdirs()
        file.writeText(before.trimEnd('\n') + "\n" + row)
    }

    private val HEADER = """
        # Remote picture provenance

        Every remote picture (#43) published to `pictures/v1/`: source, license, freeze settings and when it went out.
        Rows are added by `./gradlew :shared:publishRemotePicture` (README → Publishing remote pictures). A withdrawn
        picture keeps its row, marked withdrawn. Grids are never re-frozen: a fix is a new id.

        | Picture | Id | Source | Author, date | License | Freeze | Added | Catalog |
        |---|---|---|---|---|---|---|---|
    """.trimIndent() + "\n"

    /** Gradle passes `key=value` pairs (`publishRemotePicture` / `withdrawRemotePicture` in shared/build.gradle.kts). */
    @JvmStatic
    fun main(args: Array<String>) {
        val a = args.associate { it.substringBefore('=') to it.substringAfter('=', "") }
        fun arg(name: String) = a[name]?.takeIf { it.isNotBlank() } ?: error("missing -P$name")
        val dir = File(arg("dir"))
        val key = File(arg("signingKey"))
        val reserved = File(arg("bundled")).listFiles { f -> f.name.endsWith(".gmrg") }!!.map { it.name.removeSuffix(".gmrg") }.toSet()
        val now = System.currentTimeMillis() / 1000
        if (a["mode"] == "withdraw") {
            withdraw(dir, arg("id"), now, key, reserved)
            println("withdrew ${arg("id")}; commit $dir and open a PR (README → Publishing remote pictures)")
            return
        }
        if (a["contentChecked"] != "yes") {
            println("Before publishing, check the picture:")
            CONTENT_CHECK.forEach { println("  - $it") }
            error("then run again with -PcontentChecked=yes")
        }
        val id = arg("id")
        val picture = publish(
            Request(
                dir = dir,
                id = id,
                title = arg("title"),
                source = File(arg("source")),
                spec = arg("spec"),
                focus = a["focus"]?.toDoubleOrNull() ?: 0.5,
                locked = a["locked"] != "false",
                minAppVersion = arg("minAppVersion").toInt(),
                addedAt = LocalDate.parse(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()),
                nowSeconds = now,
                signingKey = key,
                provenance = arg("provenance"),
                provenanceFile = File(arg("provenanceFile")),
                reviewPng = File(arg("reviewDir"), "$id.review.png"),
                reservedIds = reserved,
            ),
        )
        println("published ${picture.id} (${picture.width}×${picture.height}, ${picture.colors} colors, CRC ${picture.crc32.toUInt().toString(16)}) → $dir")
        println("verified with the app's checks; commit $dir and the provenance row, then open a PR")
    }
}
