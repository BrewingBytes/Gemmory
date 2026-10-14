package com.brewingbytes.gemmory.app

import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern

/** Shared setup for the accessibility tests: shell access, TalkBack on/off and where its focus is. */
object A11yTestSupport {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    /** Keeps TalkBack running while the test reads the window (the default UiAutomation would suppress it). */
    val automation: UiAutomation by lazy { instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES) }

    private const val TALKBACK = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"

    fun shell(command: String): String =
        automation.executeShellCommand(command).use { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }

    val talkBackInstalled: Boolean
        get() = shell("pm list packages com.google.android.marvin.talkback").contains("com.google.android.marvin.talkback")

    /** Turns TalkBack on and waits until the app sees touch exploration (what TalkBack focus needs). */
    fun talkBackOn() {
        shell("pm grant com.google.android.marvin.talkback android.permission.POST_NOTIFICATIONS")
        shell("settings put secure enabled_accessibility_services $TALKBACK")
        shell("settings put secure accessibility_enabled 1")
        val manager = context.getSystemService(AccessibilityManager::class.java)
        waitFor(20_000, "touch exploration") { manager.isTouchExplorationEnabled }
    }

    fun talkBackOff() {
        shell("settings put secure enabled_accessibility_services \"\"")
        shell("settings put secure accessibility_enabled 0")
    }

    /**
     * The text or description of the node TalkBack's focus is on, read fresh from the active window (the automation's
     * node cache can keep an old focus state); null when nothing there is focused.
     */
    fun accessibilityFocusText(): String? {
        if (android.os.Build.VERSION.SDK_INT >= 34) automation.clearCache()
        return focusedIn(automation.rootInActiveWindow)?.let(::spoken)
    }

    private fun dump(n: AccessibilityNodeInfo?): String = if (n == null) "-" else
        "[${n.className?.toString()?.substringAfterLast('.')}:${(n.text ?: n.contentDescription ?: "").take(20)}:${if (n.isAccessibilityFocused) "F" else ""}" +
            (0 until n.childCount).joinToString("") { dump(n.getChild(it)) } + "]"

    /** The node's own words, else the first ones inside it (Compose puts a merged node's description on a child). */
    private fun spoken(node: AccessibilityNodeInfo): String? =
        (node.contentDescription ?: node.text)?.toString()
            ?: (0 until node.childCount).firstNotNullOfOrNull { i -> node.getChild(i)?.let(::spoken) }

    private fun focusedIn(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null || !node.refresh()) return null
        if (node.isAccessibilityFocused) return node
        return (0 until node.childCount).firstNotNullOfOrNull { focusedIn(node.getChild(it)) }
    }

    fun waitFor(timeoutMs: Long, what: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) shell("screencap -p /data/local/tmp/a11y-timeout.png")
            check(SystemClock.uptimeMillis() < end) { "timed out waiting for $what (focus: ${accessibilityFocusText()}) ${dump(automation.rootInActiveWindow)}" }
            SystemClock.sleep(50)
        }
    }

    /** A [width]×[height] canvas with [colors] colors in reading order (slot = cell % colors). */
    fun pattern(width: Int, height: Int, colors: Int) = Pattern(
        width = width,
        height = height,
        palette = List(colors) { PaletteColor(it + 1, (0xFF shl 24) or (0x305070 + it * 0x282828), Lab(it * 15.0, 0.0, 0.0)) },
        cells = IntArray(width * height) { it % colors },
    )
}
