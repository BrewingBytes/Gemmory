package com.brewingbytes.gemmory.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.play.HighlightStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings (design system, Settings): Sound, Haptics and Milestones (#35), all on by default, and the Selected color
 * style (#19), Stronger tint by default. Kept in shared preferences, which hold no user content and may be backed up.
 */
class AppSettings(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _sound = MutableStateFlow(prefs.getBoolean(SOUND, true))
    private val _haptics = MutableStateFlow(prefs.getBoolean(HAPTICS, true))
    val sound: StateFlow<Boolean> = _sound.asStateFlow()
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()
    private val _milestones = MutableStateFlow(prefs.getBoolean(MILESTONES, true))
    val milestones: StateFlow<Boolean> = _milestones.asStateFlow()
    private val _highlight = MutableStateFlow(HighlightStyle.fromKey(prefs.getString(HIGHLIGHT, null)))
    val highlight: StateFlow<HighlightStyle> = _highlight.asStateFlow()

    fun setSound(on: Boolean) {
        _sound.value = on
        prefs.edit { putBoolean(SOUND, on) }
    }

    fun setHaptics(on: Boolean) {
        _haptics.value = on
        prefs.edit { putBoolean(HAPTICS, on) }
    }

    fun setMilestones(on: Boolean) {
        _milestones.value = on
        prefs.edit { putBoolean(MILESTONES, on) }
    }

    fun setHighlight(style: HighlightStyle) {
        _highlight.value = style
        prefs.edit { putString(HIGHLIGHT, style.key) }
    }

    private companion object {
        const val FILE = "settings"
        const val SOUND = "sound"
        const val HAPTICS = "haptics"
        const val MILESTONES = "milestones"
        const val HIGHLIGHT = "highlight"
    }
}
