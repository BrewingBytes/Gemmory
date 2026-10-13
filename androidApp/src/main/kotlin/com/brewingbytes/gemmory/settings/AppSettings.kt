package com.brewingbytes.gemmory.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings toggles (design system, Settings): Sound and Haptics, both on by default. Kept in shared preferences,
 * which hold no user content and may be backed up.
 */
class AppSettings(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _sound = MutableStateFlow(prefs.getBoolean(SOUND, true))
    private val _haptics = MutableStateFlow(prefs.getBoolean(HAPTICS, true))
    val sound: StateFlow<Boolean> = _sound.asStateFlow()
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    fun setSound(on: Boolean) {
        _sound.value = on
        prefs.edit { putBoolean(SOUND, on) }
    }

    fun setHaptics(on: Boolean) {
        _haptics.value = on
        prefs.edit { putBoolean(HAPTICS, on) }
    }

    private companion object {
        const val FILE = "settings"
        const val SOUND = "sound"
        const val HAPTICS = "haptics"
    }
}
