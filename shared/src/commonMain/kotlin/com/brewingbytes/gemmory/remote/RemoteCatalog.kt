package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import kotlinx.datetime.LocalDate
import kotlin.io.encoding.Base64

/**
 * One picture of the remote catalog (#43): a finished GMRY grid made by `freezeBundledGrid`, published to the content
 * repo and never changed (saves bind to [crc32]; a fix is a new id). [grid] is a path relative to the catalog.
 * [minAppVersion] is a versionCode. [availableFrom] makes it a dated drop (T36): hidden on the phone before that local
 * date, free to start on it. [rewardOf] makes it a collection's keepsake (T37): hidden until that collection is earned.
 */
data class RemotePicture(
    val id: String,
    val title: String,
    val ratio: AspectRatio,
    val size: CanvasSize,
    val width: Int,
    val height: Int,
    val colors: Int,
    val grid: String,
    val sha256: String,
    val crc32: Int,
    val locked: Boolean,
    val minAppVersion: Int,
    val addedAt: LocalDate,
    val availableFrom: LocalDate? = null,
    val rewardOf: String? = null,
)

/**
 * A themed collection (T37, eng D7): [members] are picture ids, remote or bundled, in display order; picture entries
 * carry no collection field. [event] marks T38's weekly event. Collection and event ids share one namespace, apart from
 * picture ids.
 */
data class RemoteCollection(val id: String, val title: String, val members: List<String>, val event: Boolean = false)

/**
 * A verified catalog. [issuedAt] (epoch seconds) only moves forward, so an old signed catalog can't be replayed.
 * [collections] are in catalog order, oldest first (T37: "newest" is the last listed).
 */
data class RemoteCatalog(val issuedAt: Long, val pictures: List<RemotePicture>, val collections: List<RemoteCollection> = emptyList())

/** Why a catalog was refused. Every refusal keeps the cached catalog as it is. */
sealed interface CatalogRefusal {
    val code: String

    data object BadSignature : CatalogRefusal {
        override val code = "bad_signature"
    }

    data class Malformed(val what: String) : CatalogRefusal {
        override val code = "malformed"
    }

    data class UnsupportedVersion(val version: Long) : CatalogRefusal {
        override val code = "unsupported_version"
    }

    /** Older than the catalog already accepted. */
    data class Rollback(val issuedAt: Long, val cached: Long) : CatalogRefusal {
        override val code = "rollback"
    }

    /**
     * An id the app already uses (a bundled picture) or one listed twice: unlocks and saves are keyed by id. A
     * collection id that is also a picture id clashes too (T37).
     */
    data class IdClash(val id: String) : CatalogRefusal {
        override val code = "id_clash"
    }
}

sealed interface CatalogCheck {
    data class Ok(val catalog: RemoteCatalog) : CatalogCheck

    data class Refused(val reason: CatalogRefusal) : CatalogCheck
}

/** Why a downloaded grid was refused. */
enum class GridRefusal(val code: String) {
    BAD_HASH("bad_hash"),
    BAD_FILE("bad_file"),
    BAD_CRC("bad_crc"),
    WRONG_SHAPE("wrong_shape"),
}

sealed interface GridCheck {
    class Ok(val grid: GridFile) : GridCheck

    data class Refused(val reason: GridRefusal) : GridCheck
}

/**
 * The remote catalog's rules (#43), pure so they're unit-tested: parse, signature, rollback, id clashes, the
 * minAppVersion filter and the grid checks. The network and files stay in :androidApp.
 *
 * Format v1 (`catalog.json`, UTF-8): `{"version": 1, "issuedAt": <epoch s>, "pictures": [{"id", "title", "ratio"
 * ("1:1" | "3:4" | "4:3"), "size" ("quick" | "medium" | "big"), "width", "height", "colors", "grid"
 * ("grids/<id>.gmrg"), "sha256" (hex), "crc32" (8 hex digits), "locked", "minAppVersion", "addedAt" ("YYYY-MM-DD"), optional "availableFrom" ("YYYY-MM-DD", T36), optional "rewardOf" (a collection id, T37)}],
 * optional "collections": [{"id", "title", "members" ([picture ids]), optional "event" (true, T38)}]}`. A member the
 * catalog doesn't list is allowed (it may need a newer app, or be withdrawn). Unknown fields are ignored. `catalog.json.sig` is the Ed25519 signature of the exact catalog bytes, in base64.
 */
object RemoteCatalogs {
    const val VERSION = 1L

    /** Far above any real catalog (~300 bytes a picture); a bigger response is refused before parsing. */
    const val MAX_CATALOG_BYTES = 1 shl 20

    /** A Big grid is ~16 KB. */
    const val MAX_GRID_BYTES = 256 * 1024

    /** Same as a canvas id (`CanvasRef.ID`): one plain directory name. */
    val ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")

    const val MAX_TITLE = 60

    /**
     * The first app (versionCode) that takes more colors than a size's Standard cap, up to its More cap (#29). An
     * older app reads such an entry as malformed and refuses the whole catalog, so a picture above Standard must
     * name this or a later version; the same entry with an older one is malformed here too.
     */
    const val MORE_COLORS_MIN_APP = 12

    /**
     * The first app (versionCode) that knows dated drops (T36). An older app would show a dated picture at once, so a
     * dated entry must name this or a later version; one naming an older version is malformed.
     */
    const val DROPS_MIN_APP = 13

    /**
     * The first app (versionCode) that knows keepsakes (T37). An older app would show an unearned keepsake as an
     * ordinary picture, so an entry with `rewardOf` must name this or a later version; one naming an older version is
     * malformed.
     */
    const val REWARDS_MIN_APP = 15

    /** The first app (versionCode) that reads the remote catalog at all (#43). */
    const val REMOTE_MIN_APP = 11

    /**
     * The lowest app that reads [p] right (D14): an older app would refuse the whole catalog over its colors, or show
     * a dated drop or an unearned keepsake at once. The publishing tool stamps this as the picture's `minAppVersion`.
     * Each later hiding field (`mystery` with T43) adds its own version here; fields an older app can safely ignore,
     * like collections, never raise it.
     */
    fun lowestApp(p: RemotePicture): Int = maxOf(
        REMOTE_MIN_APP,
        if (p.colors > p.size.paletteCap) MORE_COLORS_MIN_APP else REMOTE_MIN_APP,
        if (p.availableFrom != null) DROPS_MIN_APP else REMOTE_MIN_APP,
        if (p.rewardOf != null) REWARDS_MIN_APP else REMOTE_MIN_APP,
    )

    fun gridPath(id: String): String = "grids/$id.gmrg"

    /**
     * The whole check of a downloaded catalog: [signature] (the `.sig` file's bytes) by one of [keys], then the
     * format, then no rollback below [cachedIssuedAt], then no id in [reservedIds] (the bundled pictures) or twice.
     * Pictures needing a newer app than [appVersion] are left out, unread, so a later format can add fields for them.
     */
    fun check(
        catalog: ByteArray,
        signature: ByteArray,
        keys: List<ByteArray>,
        appVersion: Int,
        cachedIssuedAt: Long?,
        reservedIds: Set<String>,
    ): CatalogCheck {
        if (catalog.size > MAX_CATALOG_BYTES) return CatalogCheck.Refused(CatalogRefusal.Malformed("too large"))
        val sig = decodeSignature(signature)
        if (sig == null || keys.none { Ed25519.verify(it, catalog, sig) }) return CatalogCheck.Refused(CatalogRefusal.BadSignature)
        val parsed = parse(catalog, appVersion)
        if (parsed !is CatalogCheck.Ok) return parsed
        val c = parsed.catalog
        if (cachedIssuedAt != null && c.issuedAt < cachedIssuedAt) {
            return CatalogCheck.Refused(CatalogRefusal.Rollback(c.issuedAt, cachedIssuedAt))
        }
        c.pictures.firstOrNull { it.id in reservedIds }?.let { return CatalogCheck.Refused(CatalogRefusal.IdClash(it.id)) }
        c.collections.firstOrNull { it.id in reservedIds }?.let { return CatalogCheck.Refused(CatalogRefusal.IdClash(it.id)) }
        return parsed
    }

    /** The `.sig` file: base64 of 64 bytes, surrounding whitespace allowed. */
    fun decodeSignature(file: ByteArray): ByteArray? =
        runCatching { Base64.decode(file.decodeToString().trim()) }.getOrNull()?.takeIf { it.size == Ed25519.SIGNATURE_BYTES }

    fun encodeSignature(signature: ByteArray): String = Base64.encode(signature) + "\n"

    /** Parses catalog bytes, unsigned (the signature is [check]'s job). */
    fun parse(bytes: ByteArray, appVersion: Int): CatalogCheck {
        val root = Json.parse(bytes.decodeToString(throwOnInvalidSequence = false)) as? Json.Obj ?: return malformed("not a JSON object")
        val version = root.long("version") ?: return malformed("version")
        if (version != VERSION) return CatalogCheck.Refused(CatalogRefusal.UnsupportedVersion(version))
        val issuedAt = root.long("issuedAt")?.takeIf { it > 0 } ?: return malformed("issuedAt")
        val list = root.fields["pictures"] as? Json.Arr ?: return malformed("pictures")
        val pictures = ArrayList<RemotePicture>()
        val ids = HashSet<String>()
        for ((i, item) in list.items.withIndex()) {
            val o = item as? Json.Obj ?: return malformed("pictures[$i]")
            val minApp = o.long("minAppVersion")?.takeIf { it in 1..Int.MAX_VALUE } ?: return malformed("pictures[$i].minAppVersion")
            if (minApp > appVersion) continue
            val picture = picture(o) ?: return malformed("pictures[$i]")
            if (!ids.add(picture.id)) return CatalogCheck.Refused(CatalogRefusal.IdClash(picture.id))
            pictures += picture
        }
        val collections = ArrayList<RemoteCollection>()
        val groups = root.fields["collections"]
        if (groups != null) {
            val arr = groups as? Json.Arr ?: return malformed("collections")
            for ((i, item) in arr.items.withIndex()) {
                val c = (item as? Json.Obj)?.let(::collection) ?: return malformed("collections[$i]")
                // One namespace for collections and events, apart from pictures (eng D7): unlocks and events key on it.
                if (c.id in ids || collections.any { it.id == c.id }) return CatalogCheck.Refused(CatalogRefusal.IdClash(c.id))
                collections += c
            }
        }
        return CatalogCheck.Ok(RemoteCatalog(issuedAt, pictures, collections))
    }

    /** A downloaded grid against its catalog entry: SHA-256, then the GMRY file, then its CRC, then its shape. */
    fun checkGrid(picture: RemotePicture, bytes: ByteArray): GridCheck {
        if (bytes.size > MAX_GRID_BYTES || Sha256.digest(bytes).toHex() != picture.sha256) return GridCheck.Refused(GridRefusal.BAD_HASH)
        val grid = (GridCodec.decode(bytes) as? Decoded.Ok)?.value ?: return GridCheck.Refused(GridRefusal.BAD_FILE)
        if (grid.crc32 != picture.crc32) return GridCheck.Refused(GridRefusal.BAD_CRC)
        val p = grid.pattern
        if (p.width != picture.width || p.height != picture.height || p.palette.size != picture.colors) {
            return GridCheck.Refused(GridRefusal.WRONG_SHAPE)
        }
        return GridCheck.Ok(grid)
    }

    private fun picture(o: Json.Obj): RemotePicture? {
        val id = o.string("id")?.takeIf { ID.matches(it) } ?: return null
        val title = o.string("title")?.takeIf { t -> t.isNotBlank() && t.length <= MAX_TITLE && t.none { it < ' ' } } ?: return null
        val ratio = o.string("ratio")?.let { r -> AspectRatio.entries.firstOrNull { it.label == r } } ?: return null
        val size = o.string("size")?.let { s -> CanvasSize.entries.firstOrNull { it.name.lowercase() == s } } ?: return null
        val spec = GridSpec.of(size, ratio)
        val width = o.long("width")?.takeIf { it == spec.width.toLong() } ?: return null
        val height = o.long("height")?.takeIf { it == spec.height.toLong() } ?: return null
        val minApp = o.long("minAppVersion") ?: return null
        val colors = o.long("colors")?.takeIf { it in 1..size.moreCap } ?: return null
        if (colors > size.paletteCap && minApp < MORE_COLORS_MIN_APP) return null
        val grid = o.string("grid")?.takeIf { it == gridPath(id) } ?: return null
        val sha = o.string("sha256")?.takeIf { s -> s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' } } ?: return null
        val crc = o.string("crc32")?.takeIf { it.length == 8 }?.let { hexToBytes(it) } ?: return null
        val locked = (o.fields["locked"] as? Json.Bool)?.value ?: return null
        val added = o.string("addedAt")?.let(::date) ?: return null
        val from = if ("availableFrom" in o.fields) o.string("availableFrom")?.let(::date) ?: return null else null
        if (from != null && minApp < DROPS_MIN_APP) return null
        val reward = if ("rewardOf" in o.fields) o.string("rewardOf")?.takeIf { ID.matches(it) } ?: return null else null
        if (reward != null && minApp < REWARDS_MIN_APP) return null
        val crc32 = crc.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        return RemotePicture(id, title, ratio, size, width.toInt(), height.toInt(), colors.toInt(), grid, sha, crc32, locked, minApp.toInt(), added, from, reward)
    }

    private fun collection(o: Json.Obj): RemoteCollection? {
        val id = o.string("id")?.takeIf { ID.matches(it) } ?: return null
        val title = o.string("title")?.takeIf { t -> t.isNotBlank() && t.length <= MAX_TITLE && t.none { it < ' ' } } ?: return null
        val list = (o.fields["members"] as? Json.Arr)?.items?.takeIf { it.isNotEmpty() } ?: return null
        val members = list.map { m -> (m as? Json.Str)?.value?.takeIf { ID.matches(it) } ?: return null }
        if (members.toSet().size != members.size) return null
        val event = if ("event" in o.fields) (o.fields["event"] as? Json.Bool)?.value ?: return null else false
        return RemoteCollection(id, title, members, event)
    }

    /** The publishing tool's collection entry (T37), fields in the documented order. */
    fun collectionEntry(c: RemoteCollection): Json.Obj = Json.Obj(
        linkedMapOf<String, Json>(
            "id" to Json.Str(c.id),
            "title" to Json.Str(c.title),
            "members" to Json.Arr(c.members.map { Json.Str(it) }),
        ).apply { if (c.event) put("event", Json.Bool(true)) },
    )

    /** The publishing tool's catalog entry, fields in the documented order. */
    fun entry(p: RemotePicture): Json.Obj = Json.Obj(
        linkedMapOf(
            "id" to Json.Str(p.id),
            "title" to Json.Str(p.title),
            "ratio" to Json.Str(p.ratio.label),
            "size" to Json.Str(p.size.name.lowercase()),
            "width" to Json.Num(p.width.toString()),
            "height" to Json.Num(p.height.toString()),
            "colors" to Json.Num(p.colors.toString()),
            "grid" to Json.Str(p.grid),
            "sha256" to Json.Str(p.sha256),
            "crc32" to Json.Str(p.crc32.toUInt().toString(16).padStart(8, '0')),
            "locked" to Json.Bool(p.locked),
            "minAppVersion" to Json.Num(p.minAppVersion.toString()),
            "addedAt" to Json.Str(p.addedAt.toString()),
        ).apply {
            p.availableFrom?.let { put("availableFrom", Json.Str(it.toString())) }
            p.rewardOf?.let { put("rewardOf", Json.Str(it)) }
        },
    )

    private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    private fun malformed(what: String) = CatalogCheck.Refused(CatalogRefusal.Malformed(what))

    private fun Json.Obj.string(key: String): String? = (fields[key] as? Json.Str)?.value

    /** An integer field, exactly (no fraction or exponent). */
    private fun Json.Obj.long(key: String): Long? = (fields[key] as? Json.Num)?.text?.toLongOrNull()
}
