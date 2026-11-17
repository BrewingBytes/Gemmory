package com.brewingbytes.gemmory.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTouchExploration(): () -> Boolean {
    val context = LocalContext.current
    return remember(context) { { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled ?: false } }
}
