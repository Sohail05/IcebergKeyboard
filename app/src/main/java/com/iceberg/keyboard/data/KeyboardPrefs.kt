package com.iceberg.keyboard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Typed access to the settings defined in res/xml/preferences.xml. Keys must match that file. */
class KeyboardPrefs(context: Context) {
    val raw: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    val theme: String get() = raw.getString(THEME, "system") ?: "system"
    val keyHeightDp: Int get() = raw.getInt(KEY_HEIGHT, 54)
    val numberRow: Boolean get() = raw.getBoolean(NUMBER_ROW, false)
    val keyHints: Boolean get() = raw.getBoolean(KEY_HINTS, true)
    val longPressMs: Int get() = raw.getInt(LONG_PRESS_MS, 300)

    val haptics: Boolean get() = raw.getBoolean(HAPTICS, true)
    val hapticMs: Int get() = raw.getInt(HAPTIC_MS, 12)
    val sound: Boolean get() = raw.getBoolean(SOUND, false)

    val autoCap: Boolean get() = raw.getBoolean(AUTO_CAP, true)
    val doubleSpacePeriod: Boolean get() = raw.getBoolean(DOUBLE_SPACE_PERIOD, true)
    val suggestions: Boolean get() = raw.getBoolean(SUGGESTIONS, true)
    val autocorrect: Boolean get() = raw.getBoolean(AUTOCORRECT, true)
    val learnWords: Boolean get() = raw.getBoolean(LEARN_WORDS, true)
    val smartPunctuation: Boolean get() = raw.getBoolean(SMART_PUNCTUATION, true)
    val spaceCursor: Boolean get() = raw.getBoolean(SPACE_CURSOR, true)

    val shortcutAutoExpand: Boolean get() = raw.getBoolean(SHORTCUT_AUTO_EXPAND, true)

    var recentEmoji: List<String>
        get() = raw.getString(RECENT_EMOJI, "")!!.split(' ').filter { it.isNotEmpty() }
        set(v) = raw.edit().putString(RECENT_EMOJI, v.joinToString(" ")).apply()

    companion object {
        const val THEME = "theme"
        const val KEY_HEIGHT = "key_height_dp"
        const val NUMBER_ROW = "number_row"
        const val KEY_HINTS = "key_hints"
        const val LONG_PRESS_MS = "long_press_ms"
        const val HAPTICS = "haptics"
        const val HAPTIC_MS = "haptic_ms"
        const val SOUND = "sound"
        const val AUTO_CAP = "auto_cap"
        const val DOUBLE_SPACE_PERIOD = "double_space_period"
        const val SUGGESTIONS = "suggestions"
        const val AUTOCORRECT = "autocorrect"
        const val LEARN_WORDS = "learn_words"
        const val SMART_PUNCTUATION = "smart_punctuation"
        const val SPACE_CURSOR = "space_cursor"
        const val SHORTCUT_AUTO_EXPAND = "shortcut_auto_expand"
        const val RECENT_EMOJI = "recent_emoji"
    }
}
