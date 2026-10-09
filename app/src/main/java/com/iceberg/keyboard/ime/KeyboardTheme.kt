package com.iceberg.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Colours for the keyboard surface. "amoled" uses true black, which saves power on the S9+'s OLED panel. */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val specialKey: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val onAccent: Int,
    val popup: Int,
    val popupSelected: Int,
    val strip: Int,
    val stripText: Int,
    val divider: Int,
) {
    companion object {
        private val LIGHT = KeyboardTheme(
            background = Color.parseColor("#E4E7EB"), key = Color.WHITE, keyPressed = Color.parseColor("#C9CED6"),
            specialKey = Color.parseColor("#C3CAD3"), text = Color.parseColor("#1B1F24"), hint = Color.parseColor("#7A828C"),
            accent = Color.parseColor("#1F6FEB"), onAccent = Color.WHITE, popup = Color.WHITE,
            popupSelected = Color.parseColor("#1F6FEB"), strip = Color.parseColor("#E4E7EB"),
            stripText = Color.parseColor("#1B1F24"), divider = Color.parseColor("#B8BEC7"),
        )
        private val DARK = KeyboardTheme(
            background = Color.parseColor("#1C1E22"), key = Color.parseColor("#3A3D43"), keyPressed = Color.parseColor("#55595F"),
            specialKey = Color.parseColor("#2A2C31"), text = Color.parseColor("#ECEFF3"), hint = Color.parseColor("#9AA1AA"),
            accent = Color.parseColor("#4C9AFF"), onAccent = Color.parseColor("#0B1020"), popup = Color.parseColor("#4A4E55"),
            popupSelected = Color.parseColor("#4C9AFF"), strip = Color.parseColor("#1C1E22"),
            stripText = Color.parseColor("#ECEFF3"), divider = Color.parseColor("#3A3D43"),
        )
        private val AMOLED = DARK.copy(
            background = Color.BLACK, key = Color.parseColor("#1E1F22"), keyPressed = Color.parseColor("#3A3C40"),
            specialKey = Color.parseColor("#121315"), strip = Color.BLACK, divider = Color.parseColor("#26282B"),
            popup = Color.parseColor("#2A2C30"),
        )

        fun resolve(context: Context, pref: String): KeyboardTheme = when (pref) {
            "light" -> LIGHT
            "dark" -> DARK
            "amoled" -> AMOLED
            else -> {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
            }
        }
    }
}
