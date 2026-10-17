package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import kotlinx.datetime.LocalDate

/** A remote catalog folder built like the publishing tool builds one, signed with a test key (#43). */
class TestCatalog(private val seed: ByteArray = SEED) {
    val files = mutableMapOf<String, ByteArray>()
    val pictures = mutableListOf<RemotePicture>()
    var issuedAt = 1_792_000_000L

    fun add(id: String, size: CanvasSize = CanvasSize.QUICK, locked: Boolean = true, addedAt: String = "2026-10-18", salt: Int = id.hashCode()): RemotePicture {
        val grid = grid(size, salt)
        val file = (GridCodec.decode(grid) as Decoded.Ok).value
        val p = RemotePicture(
            id, id.replace('-', ' ').replaceFirstChar { it.uppercase() }, AspectRatio.SQUARE, size, file.pattern.width,
            file.pattern.height, file.pattern.palette.size, RemoteCatalogs.gridPath(id), Sha256.digest(grid).toHex(),
            file.crc32, locked, 1, LocalDate.parse(addedAt),
        )
        pictures += p
        files[p.grid] = grid
        publish()
        return p
    }

    fun remove(id: String) {
        pictures.removeAll { it.id == id }
        files.remove(RemoteCatalogs.gridPath(id))
        publish()
    }

    /** Rewrites and re-signs the catalog, one second later than the last. */
    fun publish() {
        issuedAt++
        val json = Json.write(
            Json.Obj(
                linkedMapOf(
                    "version" to Json.Num("1"),
                    "issuedAt" to Json.Num(issuedAt.toString()),
                    "pictures" to Json.Arr(pictures.map { RemoteCatalogs.entry(it) }),
                ),
            ),
        ).encodeToByteArray()
        files[RemotePictures.CATALOG] = json
        files[RemotePictures.SIGNATURE] = RemoteCatalogs.encodeSignature(Ed25519.sign(seed, json)).encodeToByteArray()
    }

    companion object {
        val SEED = ByteArray(32) { (it * 5 + 3).toByte() }
        val KEY: ByteArray = Ed25519.publicKey(SEED)

        fun grid(size: CanvasSize, salt: Int): ByteArray {
            val spec = GridSpec.of(size, AspectRatio.SQUARE)
            val colors = 6
            val palette = List(colors) { PaletteColor(it + 1, 0xFF000000.toInt() or ((salt + it * 977) and 0xFFFFFF), Lab(it * 12.0, 0.0, 0.0)) }
            return GridCodec.encode(Pattern(spec.width, spec.height, palette, IntArray(spec.cellCount) { (it / 7 + salt) and 0x7FFFFFFF % colors }.map { it % colors }.toIntArray()))
        }
    }
}

/** Serves a [TestCatalog] like GitHub would: ETags, 304s, and switches for offline, errors and cut-off bodies. */
class FakeSource(private val catalog: TestCatalog) : RemoteSource {
    val requests = mutableListOf<Pair<String, String?>>()
    var offline = false
    var cutShort = setOf<String>()
    var httpError = mapOf<String, Int>()

    override fun fetch(path: String, ifNoneMatch: String?, maxBytes: Int): Fetched {
        requests += path to ifNoneMatch
        if (offline) return Fetched.Failed(java.net.UnknownHostException("raw.githubusercontent.com"), offline = true)
        httpError[path]?.let { return Fetched.HttpError(it) }
        val body = catalog.files[path] ?: return Fetched.HttpError(404)
        if (path in cutShort) return Fetched.Failed(java.io.IOException("short body"), offline = false)
        if (body.size > maxBytes) return Fetched.TooLarge
        val etag = "\"" + Sha256.digest(body).toHex().take(12) + "\""
        if (ifNoneMatch == etag) return Fetched.NotModified
        return Fetched.Body(body, etag)
    }
}
