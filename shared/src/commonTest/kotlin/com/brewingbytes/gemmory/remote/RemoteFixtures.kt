package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.save.Decoded
import kotlinx.datetime.LocalDate

/** A signed catalog and its grids, built the way the publishing tool builds them. */
object RemoteFixtures {
    val SEED = ByteArray(32) { (it * 7 + 1).toByte() }
    val KEY: ByteArray = Ed25519.publicKey(SEED)
    val OTHER_SEED = ByteArray(32) { (it * 3 + 2).toByte() }

    fun grid(size: CanvasSize = CanvasSize.QUICK, ratio: AspectRatio = AspectRatio.SQUARE, colors: Int = 4, salt: Int = 0): ByteArray {
        val spec = GridSpec.of(size, ratio)
        val palette = List(colors) { PaletteColor(it + 1, 0xFF000000.toInt() or (it * 40 + salt), Lab(it * 10.0, salt.toDouble(), 0.0)) }
        return GridCodec.encode(Pattern(spec.width, spec.height, palette, IntArray(spec.cellCount) { it % colors }))
    }

    fun picture(
        id: String,
        grid: ByteArray,
        size: CanvasSize = CanvasSize.QUICK,
        ratio: AspectRatio = AspectRatio.SQUARE,
        locked: Boolean = true,
        minAppVersion: Int = 12,
        addedAt: String = "2026-10-18",
        availableFrom: String? = null,
        rewardOf: String? = null,
        mystery: Boolean = false,
    ): RemotePicture {
        val file = (GridCodec.decode(grid) as Decoded.Ok<GridFile>).value
        return RemotePicture(
            id, id.replaceFirstChar { it.uppercase() }, ratio, size, file.pattern.width, file.pattern.height,
            file.pattern.palette.size, RemoteCatalogs.gridPath(id), Sha256.digest(grid).toHex(), file.crc32, locked,
            minAppVersion, LocalDate.parse(addedAt), availableFrom?.let(LocalDate::parse), rewardOf, mystery,
        )
    }

    fun catalogJson(
        pictures: List<RemotePicture>,
        issuedAt: Long = 1_792_000_000,
        version: Long = 1,
        collections: List<RemoteCollection> = emptyList(),
    ): ByteArray =
        Json.write(
            Json.Obj(
                linkedMapOf(
                    "version" to Json.Num(version.toString()),
                    "issuedAt" to Json.Num(issuedAt.toString()),
                    "pictures" to Json.Arr(pictures.map { RemoteCatalogs.entry(it) }),
                ).apply { if (collections.isNotEmpty()) put("collections", Json.Arr(collections.map { RemoteCatalogs.collectionEntry(it) })) },
            ),
        ).encodeToByteArray()

    fun sign(catalog: ByteArray, seed: ByteArray = SEED): ByteArray =
        RemoteCatalogs.encodeSignature(Ed25519.sign(seed, catalog)).encodeToByteArray()
}
