package com.brewingbytes.gemmory.share

import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Strings
import com.brewingbytes.gemmory.policy.LandingAction
import com.brewingbytes.gemmory.policy.LandingInput
import com.brewingbytes.gemmory.policy.ReferrerParse
import com.brewingbytes.gemmory.policy.ShareLanding
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the gallery does for a share link's picture (T50). */
sealed interface LandingUi {
    /** Open its canvas now (entry share_link). */
    data class Open(val ref: CanvasRef) : LandingUi

    /** It became ready after the gallery drew: "Shared with you: <title> · Open" (DR2). */
    data class Offer(val ref: CanvasRef, val title: String) : LandingUi
}

/** What the landing reads from the app: the install's funnel and the pictures this phone can open now. */
interface LandingSource {
    /** The raw install referrer, once read. */
    val referrer: String?

    /** The referrer's read has ended (with or without one). */
    val referrerRead: Boolean

    /** A share link's id as a canvas this phone can open now (`openableRef`); null for a hidden, unknown or crafted id. */
    fun openable(id: String): CanvasRef?

    /** The title the toasts name: an unfinished mystery reads "a mystery picture". */
    fun title(ref: CanvasRef): String

    /** The landing ended: [sharedId] opened (free today), or nothing did (null). */
    fun ended(sharedId: String?)
}

/**
 * The shared picture landing on this phone (T50, rule in [ShareLanding]). [active] only in the process of a fresh
 * install's first launch; any other launch never lands. Every input is handled one at a time on [serial] (the
 * pictures it reads come from the disk), and what the gallery should do is [ui]. The canvas's one-time toast is
 * [takeCanvasToast].
 */
class SharedLanding(
    active: Boolean,
    private val source: LandingSource,
    private val scope: CoroutineScope,
    private val serial: CoroutineDispatcher,
    /** The toast's words (T50 DR1, T48): "Shared with you: <title>". */
    private val strings: Strings,
) {
    private val rule = ShareLanding().also { if (!active) it.left() }
    private var inView = false
    private var checkEnded = false

    @Volatile
    private var toast: Pair<CanvasRef, String>? = null

    private val _ui = MutableStateFlow<LandingUi?>(null)
    val ui: StateFlow<LandingUi?> = _ui.asStateFlow()

    /** The gallery is in view for the first time this session: consent has settled and its tiles have loaded. */
    fun galleryInView() = post { inView = true }

    /** The install referrer's read has ended. */
    fun referrerRead() = post {}

    /** A catalog check ended in this session, whatever its outcome. */
    fun checkEnded() = post { checkEnded = true }

    /** The player left the gallery (a canvas opened by hand included): the landing is over, an offer showing too. */
    fun leftGallery() = scope.launch(serial) {
        if (!rule.over) {
            rule.left()
            source.ended(null)
        }
        if (_ui.value is LandingUi.Offer) _ui.value = null
    }

    /** The gallery opened [LandingUi.Open]'s canvas. */
    fun opened() {
        if (_ui.value is LandingUi.Open) _ui.value = null
    }

    /**
     * The offer's Open was tapped: its picture is free today, its canvas shows the toast, and the gallery opens it as
     * [LandingUi.Open]. The grant is saved before this returns, so the canvas opening next already counts its first gem.
     */
    fun offerTaken(offer: LandingUi.Offer) {
        toast = offer.ref to strings.text(R.string.shared_with_you, offer.title)
        source.ended(offer.ref.id)
        _ui.value = LandingUi.Open(offer.ref)
    }

    /** The offer's time ran out. */
    fun offerGone() {
        if (_ui.value is LandingUi.Offer) _ui.value = null
    }

    /** "Shared with you: <title>" for [ref]'s canvas, once (DR1); null for any other canvas. */
    fun takeCanvasToast(ref: CanvasRef): String? {
        val (shared, text) = toast ?: return null
        if (shared != ref) return null
        toast = null
        return text
    }

    private fun post(change: () -> Unit) = scope.launch(serial) {
        change()
        look()
    }

    private fun look() {
        // Nothing is read before the gallery is in view: the app may still be starting.
        if (rule.over || !inView) return
        val id = source.referrer?.let { ShareLanding.pictureId(ReferrerParse.parse(it)) }
        val ref = id?.let(source::openable)
        when (rule.look(LandingInput(inView, source.referrerRead, id, ref != null, checkEnded))) {
            LandingAction.OPEN -> {
                ref!!
                toast = ref to strings.text(R.string.shared_with_you, source.title(ref))
                source.ended(ref.id)
                _ui.value = LandingUi.Open(ref)
            }
            LandingAction.OFFER -> {
                ref!!
                source.ended(null)
                _ui.value = LandingUi.Offer(ref, source.title(ref))
            }
            LandingAction.DROP -> source.ended(null)
            LandingAction.WAIT -> Unit
        }
    }
}
