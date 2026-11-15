package com.brewingbytes.gemmory.settings

import com.brewingbytes.gemmory.game.MusicRules
import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.storage.KeyValueStore
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings (design system, Settings): Sound, Haptics and Milestones (#35), all on by default, Music and its volume
 * (T42, on at 30%), and the Selected color style (#19), Stronger tint by default, and whether each gallery tab's Finished
 * section is expanded (#89). Also the first-music flags (T42, D24): whether a canvas has opened yet and whether the
 * "Music is playing" chip has shown, and whether Play Games' turn has come (T45). Kept in shared preferences, which
 * hold no user content and may be backed up.
 *
 * [prefs] is the [StorageLayout.Prefs.SETTINGS] file ([FILE]); common since iOS Stage 1c (T64), on the same file and
 * keys as before (eng D7), and the [com.brewingbytes.gemmory.app.AppGraph] opens it.
 */
class AppSettings(private val prefs: KeyValueStore) {

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

    /**
     * Connected tests only (T42): Music on at 30%, no canvas opened yet and the chip not shown, as on a fresh install.
     * Public because the tests live in androidApp.
     */
    fun resetMusicForTest() {
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

    /**
     * Play Games' turn has come (T45): set once the player finished a picture or earned an achievement, and kept, so
     * a restored phone doesn't wait for another finish.
     */
    val playGamesReady: Boolean get() = prefs.getBoolean(PLAY_GAMES_READY, false)

    fun markPlayGamesReady() {
        if (!playGamesReady) prefs.edit { putBoolean(PLAY_GAMES_READY, true) }
    }

    companion object {
        /** The key-value file the settings live in. */
        const val FILE = StorageLayout.Prefs.SETTINGS

        private fun finishedKey(tab: Int) = if (tab == 0) FINISHED_PICTURES else FINISHED_PHOTOS

        private const val SOUND = "sound"
        private const val HAPTICS = "haptics"
        private const val MILESTONES = "milestones"
        private const val HIGHLIGHT = "highlight"
        private const val MUSIC = "music"
        private const val MUSIC_VOLUME = "music_volume"
        private const val MUSIC_UNLOCKED = "music_unlocked"
        private const val MUSIC_CHIP = "music_chip_shown"
        private const val FINISHED_PICTURES = "finished_open_pictures"
        private const val FINISHED_PHOTOS = "finished_open_photos"
        private const val PLAY_GAMES_READY = "play_games_ready"
    }
}
