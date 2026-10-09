package com.iceberg.keyboard.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.iceberg.keyboard.BuildConfig
import com.iceberg.keyboard.R
import com.iceberg.keyboard.data.Dictionary
import com.iceberg.keyboard.data.ShortcutStore
import com.iceberg.keyboard.ime.IcebergImeService

/** Launcher + IME settings screen: setup status, a test field, and all preferences. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var selectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        status = findViewById(R.id.setup_status)
        enableButton = findViewById(R.id.enable_button)
        selectButton = findViewById(R.id.select_button)
        enableButton.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        selectButton.setOnClickListener { imm().showInputMethodPicker() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settings_container, SettingsFragment()).commit()
        }
    }

    override fun onResume() {
        super.onResume()
        updateSetupState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSetupState() // the IME picker is a dialog; refresh after it closes
    }

    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    private fun updateSetupState() {
        val id = imeId()
        val enabled = imm().enabledInputMethodList.any { it.id == id }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == id
        status.setText(
            when {
                selected -> R.string.setup_done
                enabled -> R.string.setup_select
                else -> R.string.setup_enable
            },
        )
        enableButton.visibility = if (enabled) View.GONE else View.VISIBLE
        selectButton.visibility = if (enabled && !selected) View.VISIBLE else View.GONE
    }

    private fun imeId(): String = "$packageName/${IcebergImeService::class.java.name}"

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>("manage_shortcuts")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), ShortcutsActivity::class.java))
                true
            }
            findPreference<Preference>("clear_learned")?.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.clear_learned_title)
                    .setMessage(getString(R.string.clear_learned_message, Dictionary.userWordCount()))
                    .setPositiveButton(R.string.clear) { _, _ -> Dictionary.clearUserWords(requireContext()) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            findPreference<Preference>("about")?.summary = "Version ${BuildConfig.VERSION_NAME}"
        }

        override fun onResume() {
            super.onResume()
            Dictionary.ensureLoaded(requireContext())
            findPreference<Preference>("manage_shortcuts")?.summary =
                getString(R.string.shortcuts_summary, ShortcutStore.get(requireContext()).size)
        }
    }
}
