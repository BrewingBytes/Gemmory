package com.brewingbytes.gemmory.timelapse

import org.junit.Test
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.assertEquals

/**
 * Google's "Get it on Google Play" badges (T53, T49): one per app language, each the file from
 * play.google.com/intl/en_us/badges (`<lang>_badge_web_generic.png`; Brazil's `pt-br` is `pt` here), unchanged, and
 * all the size [ShareLayout] lays out.
 */
class PlayBadgesTest {
    private val sha1 = mapOf(
        "en" to "cb8126b32c2274e0394246b40bd0b7f9f847e44c",
        "de" to "a48d2102c6b4545601337f53eda7551890af8613",
        "es" to "9230af71af1118eafd8696e6518d5b225cd66326",
        "pt" to "e9436ff57b84e84dff7e8d6dc230b90f51ee151a",
        "fr" to "db6305d94f1ddfdb38a5f784a1900e30f4000c30",
        "it" to "38b22403fc1703c848d06aac084ae34ce526e3ae",
        "ro" to "9a382602405596f1a791480105069bb84c6ac1e1",
        "pl" to "5f07c856e0de22e0439cfa6e8d2f61115ba46cf6",
        "tr" to "4cdfcf8518de2b47ae2ba7e93969d13e94b82f69",
        "hu" to "9960fe26dccaa83d939284719a20c99edb990b55",
        "bg" to "06e2447377eee0831a8c4436ed5d181b97d70e4e",
        "ja" to "798efdd02f49310e760aa424d0a8239a48372ffc",
    )

    @Test
    fun everyLanguageHasGooglesBadgeUnchanged() {
        assertEquals(FrameStyle.BADGE_LANGUAGES, sha1.keys)
        for ((language, expected) in sha1) {
            val file = File("src/main/assets/${FrameStyle.badgeAsset(language)}")
            val digest = MessageDigest.getInstance("SHA-1").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(expected, digest, file.name)
            val image = ImageIO.read(file)
            assertEquals(ShareLayout.BADGE_FILE_WIDTH to ShareLayout.BADGE_FILE_HEIGHT, image.width to image.height, file.name)
        }
    }

    @Test
    fun anOtherLanguageGetsTheEnglishBadge() {
        assertEquals(FrameStyle.BADGE_ASSET, FrameStyle.badgeAsset("nl"))
        assertEquals("share/play_badge_pt.png", FrameStyle.badgeAsset("pt"))
    }
}
