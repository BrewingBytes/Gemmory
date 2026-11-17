package com.brewingbytes.gemmory.ui

import androidx.compose.runtime.Composable

/**
 * Reads whether a screen reader is exploring by touch (TalkBack; VoiceOver on iOS), at the moment it's called: chips
 * that time out (the music chip T42, the shared offer T50) stay up under it.
 */
@Composable
expect fun rememberTouchExploration(): () -> Boolean
