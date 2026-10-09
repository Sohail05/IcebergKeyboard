package com.iceberg.keyboard.data

import android.content.Context
import android.util.Log
import com.iceberg.keyboard.core.Suggester
import java.io.File
import java.util.concurrent.Executors

/**
 * Loads the bundled word list and the learned user words off the main thread.
 * [suggester] is null until loading finishes (typically < 100 ms on a Galaxy S9+).
 */
object Dictionary {
    private const val TAG = "Dictionary"
    private const val ASSET = "words_en.txt"
    private const val USER_FILE = "user_words.tsv"

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "iceberg-dict").apply { priority = Thread.MIN_PRIORITY } }

    @Volatile var suggester: Suggester? = null
        private set
    @Volatile private var dirty = false
    @Volatile private var loading = false

    fun ensureLoaded(context: Context) {
        if (suggester != null || loading) return
        loading = true
        val app = context.applicationContext
        io.execute { loadNow(app) }
    }

    private fun loadNow(app: Context) {
        try {
            val words = app.assets.open(ASSET).bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.toList() }
            val s = Suggester(words)
            val user = File(app.filesDir, USER_FILE)
            if (user.exists()) s.importUserWords(user.readText())
            suggester = s
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary", e)
        } finally {
            loading = false
        }
    }

    fun learn(word: String, weight: Int = 1) {
        if (suggester?.learn(word, weight) == true) dirty = true
    }

    fun forget(word: String) {
        if (suggester?.forget(word) == true) dirty = true
    }

    /** Deletes all learned words and reloads the bundled list. */
    fun clearUserWords(context: Context) {
        val app = context.applicationContext
        dirty = false
        io.execute {
            File(app.filesDir, USER_FILE).delete()
            loadNow(app)
        }
    }

    /** Persists learned words if anything changed; called when the keyboard hides. */
    fun saveIfDirty(context: Context) {
        val s = suggester ?: return
        if (!dirty) return
        dirty = false
        val data = s.exportUserWords()
        val file = File(context.applicationContext.filesDir, USER_FILE)
        io.execute {
            try {
                val tmp = File(file.parentFile, "$USER_FILE.tmp")
                tmp.writeText(data)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save user words", e)
            }
        }
    }

    fun userWordCount(): Int = suggester?.userWordCount() ?: 0
}
