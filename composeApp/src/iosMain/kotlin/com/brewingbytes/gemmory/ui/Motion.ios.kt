package com.brewingbytes.gemmory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberReducedMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }

/**
 * VoiceOver's focus moves (design system, iOS → VoiceOver) come with the iOS screens in Stage 2; until then a sheet
 * leaves VoiceOver where it puts the focus itself.
 */
@Composable
actual fun rememberAccessibilityFocus(): AccessibilityFocus = remember { NoFocusMoves }

private object NoFocusMoves : AccessibilityFocus {
    override fun moveTo(tag: String): Boolean = false
    override fun holdOn(tag: String, cancelled: () -> Boolean) = Unit
}
