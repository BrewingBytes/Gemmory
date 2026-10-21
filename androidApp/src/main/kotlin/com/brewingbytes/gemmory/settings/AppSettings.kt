package com.brewingbytes.gemmory.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.brewingbytes.gemmory.game.MusicRules
import com.brewingbytes.gemmory.play.HighlightStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings (design system, Settings): Sound, Haptics and Milestones (#35), all on by default, Music and its volume
 * (T42, on at 30%), and the Selected color style (#19), Stronger tint by default, and whether each gallery tab's Finished
 * section is expanded (#89). Also the first-music flags (T42, D24): whether a canvas has opened yet and whether the
 * "Music is playing" chip has shown. Kept in shared preferences, which hold no user content and may be backed up.
 */
class AppSettings(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _sound = MutableStateFlow(prefs.getBoolean(SOUND, true))
    private val _haptics = MutableStateFlow(prefs.getBoolean(HAPTICS, true))
    val sound: StateFlow<Boolean> = _sound.asStateFlow()
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()
    private val _milestones = MutableStateFlow(prefs.getBoolean(MILESTONES, true))
    val milestones: StateFlow<Boolean> = _milestones.asStateFlow()
    private val _music = MutableStateFlow(prefs.getBoolean(MUSIC, true))
    val music: StateFlow<Boolean> = _music.asStateFlow()
    private val _musicVolume = MutableStateFlow(MusicRules.volumeStep(prefs.getInt(MUSIC_VOLUME, MusicRules.DEFAULT_VOLUME)))
    val musicVolume: StateFlow<Int> = _musicVolume.asStateFlow()
    private val _musicUnlocked = MutableStateFlow(prefs.getBoolean(MUSIC_UNLOCKED, false))

    /** A canvas has opened once (D24): music may play from then on, in the gallery too. */
    val musicUnlocked: StateFlow<Boolean> = _musicUnlocked.asStateFlow()
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

    fun setMusic(on: Boolean) {
        _music.value = on
        prefs.edit { putBoolean(MUSIC, on) }
    }

    fun setMusicVolume(percent: Int) {
        val step = MusicRules.volumeStep(percent)
        _musicVolume.value = step
        prefs.edit { putInt(MUSIC_VOLUME, step) }
    }

    fun unlockMusic() {
        if (_musicUnlocked.value) return
        _musicUnlocked.value = true
        prefs.edit { putBoolean(MUSIC_UNLOCKED, true) }
    }

    /** The "Music is playing" chip has shown (D24): it never shows again. */
    val musicChipShown: Boolean get() = prefs.getBoolean(MUSIC_CHIP, false)

    fun musicChipShown() {
        prefs.edit { putBoolean(MUSIC_CHIP, true) }
    }

    /** Connected tests only (T42): Music on at 30%, no canvas opened yet and the chip not shown, as on a fresh install. */
    @VisibleForTesting
    internal fun resetMusicForTest() {
        setMusic(true)
        setMusicVolume(MusicRules.DEFAULT_VOLUME)
        _musicUnlocked.value = false
        prefs.edit { remove(MUSIC_UNLOCKED).remove(MUSIC_CHIP) }
    }

    fun setHighlight(style: HighlightStyle) {
        _highlight.value = style
        prefs.edit { putString(HIGHLIGHT, style.key) }
    }

    /** Whether a gallery tab's Finished section (#89) is expanded: 0 Pictures, 1 My Photos. Collapsed by default. */
    fun finishedOpen(tab: Int): Boolean = prefs.getBoolean(finishedKey(tab), false)

    fun setFinishedOpen(tab: Int, open: Boolean) {
        prefs.edit { putBoolean(finishedKey(tab), open) }
    }

    private companion object {
        fun finishedKey(tab: Int) = if (tab == 0) FINISHED_PICTURES else FINISHED_PHOTOS

        const val FILE = "settings"
        const val SOUND = "sound"
        const val HAPTICS = "haptics"
        const val MILESTONES = "milestones"
        const val HIGHLIGHT = "highlight"
        const val MUSIC = "music"
        const val MUSIC_VOLUME = "music_volume"
        const val MUSIC_UNLOCKED = "music_unlocked"
        const val MUSIC_CHIP = "music_chip_shown"
        const val FINISHED_PICTURES = "finished_open_pictures"
        const val FINISHED_PHOTOS = "finished_open_photos"
    }
}
