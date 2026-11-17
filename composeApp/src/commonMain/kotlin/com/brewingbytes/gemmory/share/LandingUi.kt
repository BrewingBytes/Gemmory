package com.brewingbytes.gemmory.share

import com.brewingbytes.gemmory.storage.CanvasRef

/** What the gallery does for a share link's picture (T50). */
sealed interface LandingUi {
    /** Open its canvas now (entry share_link). */
    data class Open(val ref: CanvasRef) : LandingUi

    /** It became ready after the gallery drew: "Shared with you: <title> · Open" (DR2). */
    data class Offer(val ref: CanvasRef, val title: String) : LandingUi
}
