package com.brewingbytes.gemmory.ui

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsVoiceOverRunning

@Composable
actual fun rememberTouchExploration(): () -> Boolean = { UIAccessibilityIsVoiceOverRunning() }
