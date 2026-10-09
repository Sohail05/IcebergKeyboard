package com.iceberg.keyboard.data

import android.content.Context
import android.util.Log
import com.iceberg.keyboard.core.Shortcut
import com.iceberg.keyboard.core.ShortcutCodec
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide shortcut repository persisted as JSON (same format as export) in app-private storage.
 * The IME and settings screens run in the same process, so they share this instance and see
 * changes immediately via [addListener].
 */
class ShortcutStore private constructor(private val file: File) {

    @Volatile private var byKey: Map<String, Shortcut> = emptyMap()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    val all: List<Shortcut> get() = byKey.values.sortedBy { it.key }
    val size: Int get() = byKey.size

    fun find(trigger: String): Shortcut? = if (trigger.isEmpty()) null else byKey[trigger.lowercase()]

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    @Synchronized
    fun put(shortcut: Shortcut, replacingTrigger: String? = null) {
        val m = LinkedHashMap(byKey)
        replacingTrigger?.let { m.remove(it.lowercase()) }
        m[shortcut.key] = shortcut
        commit(m)
    }

    @Synchronized
    fun remove(trigger: String) {
        val m = LinkedHashMap(byKey)
        if (m.remove(trigger.lowercase()) != null) commit(m)
    }

    @Synchronized
    fun replaceAll(shortcuts: Collection<Shortcut>) {
        commit(shortcuts.associateByTo(LinkedHashMap()) { it.key })
    }

    private fun commit(m: Map<String, Shortcut>) {
        byKey = m
        save()
        listeners.forEach { it() }
    }

    private fun load() {
        if (!file.exists()) {
            byKey = DEFAULTS.associateBy { it.key }
            save()
            return
        }
        try {
            byKey = ShortcutCodec.parse(file.readText()).shortcuts.associateBy { it.key }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read shortcuts; keeping a backup", e)
            file.copyTo(File(file.parentFile, "${file.name}.corrupt"), overwrite = true)
            byKey = emptyMap()
        }
    }

    /** Atomic write: temp file + rename, so a crash mid-write never loses the user's shortcuts. */
    private fun save() {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(ShortcutCodec.export(byKey.values))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        private const val TAG = "ShortcutStore"

        private val DEFAULTS = listOf(
            Shortcut("omw", "On my way!"),
            Shortcut("brb", "be right back"),
            Shortcut("ty", "thank you"),
            Shortcut(";date", "{date}"),
            Shortcut(";time", "{time}"),
        )

        @Volatile private var instance: ShortcutStore? = null

        fun get(context: Context): ShortcutStore = instance ?: synchronized(this) {
            instance ?: ShortcutStore(File(context.applicationContext.filesDir, "shortcuts.json"))
                .also { it.load(); instance = it }
        }
    }
}
