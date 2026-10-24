package com.brewingbytes.gemmory.clips

import com.brewingbytes.gemmory.remote.titleIn
import com.brewingbytes.gemmory.copy.Messages
import com.brewingbytes.gemmory.copy.Strings
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef

/**
 * One catalog picture the Clips screen can render (T47). Never a photo canvas. [title] is in the app's language, for
 * the screen; [titleIn] gives it in a clip's language (#152).
 */
class ClipPicture(
    val ref: CanvasRef,
    val title: String,
    val size: String?,
    val titleIn: (Strings) -> String = { title },
    private val grid: () -> ByteArray?,
) {
    /** The picture's grid, decoded; null if it's missing or unreadable. */
    fun pattern(): Pattern? = grid()?.let { (GridCodec.decode(it) as? Decoded.Ok)?.value?.pattern }
}

/**
 * Every catalog picture whose grid is on the phone: the bundled ones, then every remote one the catalog check has
 * downloaded and verified, future drops and unearned keepsakes included (the date and earn gates are bypassed, never
 * the signature: [com.brewingbytes.gemmory.remote.RemotePictures.storedGrid] re-checks it).
 */
fun clipPictures(app: GemmoryApp): List<ClipPicture> {
    val strings = app.strings
    val bundled = BundledPictures.ALL.map { picture ->
        ClipPicture(CanvasRef(CanvasKind.BUNDLED, picture.id), picture.title(strings), BundledPictures.canvasSizeOf(picture.id)?.let { Messages.size(strings, it) }, picture::title) {
            app.assets.open(picture.asset).use { it.readBytes() }
        }
    }
    val remote = app.remote.storedPictures().map { picture ->
        ClipPicture(
            CanvasRef(CanvasKind.REMOTE, picture.id),
            picture.titleIn(strings.locale.toLanguageTag()),
            Messages.size(strings, picture.size),
            { picture.titleIn(it.locale.toLanguageTag()) },
        ) { app.remote.storedGrid(picture.id) }
    }
    return bundled + remote
}
