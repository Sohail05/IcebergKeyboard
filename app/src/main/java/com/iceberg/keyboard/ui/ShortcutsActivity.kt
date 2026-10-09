package com.iceberg.keyboard.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iceberg.keyboard.R
import com.iceberg.keyboard.core.ImportResult
import com.iceberg.keyboard.core.MergeMode
import com.iceberg.keyboard.core.Shortcut
import com.iceberg.keyboard.core.ShortcutCodec
import com.iceberg.keyboard.core.ShortcutFormatException
import com.iceberg.keyboard.data.ShortcutStore
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.Executors

/** List, search, add, edit, delete, import and export text shortcuts. */
class ShortcutsActivity : AppCompatActivity() {

    private lateinit var store: ShortcutStore
    private lateinit var adapter: ShortcutAdapter
    private lateinit var empty: TextView
    private var filter = ""
    private val io = Executors.newSingleThreadExecutor()
    private val storeListener: () -> Unit = { runOnUiThread { refresh() } }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { export(it) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { import(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcuts)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        store = ShortcutStore.get(this)
        empty = findViewById(R.id.empty)
        adapter = ShortcutAdapter(onClick = { editDialog(it) }, onLongClick = { confirmDelete(it) })
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@ShortcutsActivity)
            adapter = this@ShortcutsActivity.adapter
        }
        findViewById<FloatingActionButton>(R.id.add).setOnClickListener { editDialog(null) }
        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { filter = s?.toString().orEmpty().trim(); refresh() }
        })
        store.addListener(storeListener)
        refresh()
    }

    override fun onDestroy() {
        store.removeListener(storeListener)
        io.shutdown()
        super.onDestroy()
    }

    private fun refresh() {
        val all = store.all
        val shown = if (filter.isEmpty()) all else all.filter {
            it.trigger.contains(filter, true) || it.expansion.contains(filter, true)
        }
        adapter.submit(shown)
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        supportActionBar?.subtitle = resources.getQuantityString(R.plurals.shortcut_count, all.size, all.size)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.shortcuts, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_import -> {
            importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
            true
        }
        R.id.action_export -> {
            exportLauncher.launch("iceberg-shortcuts-${LocalDate.now()}.json")
            true
        }
        R.id.action_share -> { share(); true }
        R.id.action_delete_all -> {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_all_title)
                .setMessage(getString(R.string.delete_all_message, store.size))
                .setPositiveButton(R.string.delete) { _, _ -> store.replaceAll(emptyList()) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---- Add / edit / delete ----

    private fun editDialog(existing: Shortcut?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_shortcut, null)
        val trigger = view.findViewById<EditText>(R.id.trigger)
        val expansion = view.findViewById<EditText>(R.id.expansion)
        existing?.let { trigger.setText(it.trigger); expansion.setText(it.expansion) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_shortcut else R.string.edit_shortcut)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .apply { if (existing != null) setNeutralButton(R.string.delete) { _, _ -> store.remove(existing.trigger) } }
            .create()
        dialog.setOnShowListener {
            // Validate before closing so input isn't lost on error.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = trigger.text.toString().trim()
                val e = expansion.text.toString()
                val problem = Shortcut.validate(t, e)
                val clash = store.find(t)
                when {
                    problem != null -> trigger.error = problem
                    clash != null && clash.key != existing?.key -> trigger.error = getString(R.string.trigger_exists)
                    else -> {
                        store.put(Shortcut(t, e), replacingTrigger = existing?.trigger)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(s: Shortcut) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_one_title, s.trigger))
            .setPositiveButton(R.string.delete) { _, _ -> store.remove(s.trigger) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- Import / export ----

    private fun export(uri: Uri) {
        val json = ShortcutCodec.export(store.all, OffsetDateTime.now().toString())
        io.execute {
            val result = runCatching {
                contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(json) }
            }
            runOnUiThread {
                val msg = result.fold(
                    { getString(R.string.export_done, store.size) },
                    { getString(R.string.export_failed, it.message) },
                )
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun share() {
        val json = ShortcutCodec.export(store.all, OffsetDateTime.now().toString())
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Iceberg Keyboard shortcuts")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_shortcuts)))
    }

    private fun import(uri: Uri) {
        io.execute {
            val parsed: Result<ImportResult> = runCatching {
                val text = contentResolver.openInputStream(uri)!!.use { input ->
                    val bytes = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
                    if (bytes.size > MAX_IMPORT_BYTES) throw ShortcutFormatException("File is larger than 2 MB")
                    String(bytes, Charsets.UTF_8)
                }
                ShortcutCodec.parse(text)
            }
            runOnUiThread {
                parsed.fold(
                    { confirmImport(it) },
                    {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.import_failed)
                            .setMessage(it.message ?: it.toString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    },
                )
            }
        }
    }

    private fun confirmImport(result: ImportResult) {
        val existing = store.all
        val duplicates = result.shortcuts.count { s -> existing.any { it.key == s.key } }
        val message = buildString {
            append(getString(R.string.import_summary, result.shortcuts.size, result.format.name.lowercase().replace('_', ' ')))
            if (duplicates > 0) append("\n").append(getString(R.string.import_duplicates, duplicates))
            if (result.skipped.isNotEmpty()) {
                append("\n\n").append(getString(R.string.import_skipped, result.skipped.size))
                result.skipped.take(5).forEach { append("\n• ").append(it) }
                if (result.skipped.size > 5) append("\n…")
            }
        }
        val modes = arrayOf(MergeMode.OVERWRITE_DUPLICATES, MergeMode.KEEP_EXISTING, MergeMode.REPLACE_ALL)
        val labels = arrayOf(getString(R.string.merge_overwrite), getString(R.string.merge_keep), getString(R.string.merge_replace))
        var choice = 0
        AlertDialog.Builder(this)
            .setTitle(R.string.import_title)
            .setMessage(message)
            .setPositiveButton(R.string.import_next) { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.import_how)
                    .setSingleChoiceItems(labels, 0) { _, which -> choice = which }
                    .setPositiveButton(R.string.import_action) { _, _ -> applyImport(result, modes[choice]) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyImport(result: ImportResult, mode: MergeMode) {
        val outcome = ShortcutCodec.merge(store.all, result.shortcuts, mode)
        store.replaceAll(outcome.shortcuts)
        Toast.makeText(this, getString(R.string.import_done, outcome.added, outcome.updated, outcome.unchanged), Toast.LENGTH_LONG).show()
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private class ShortcutAdapter(
        private val onClick: (Shortcut) -> Unit,
        private val onLongClick: (Shortcut) -> Unit,
    ) : RecyclerView.Adapter<ShortcutAdapter.Holder>() {
        private var items: List<Shortcut> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<Shortcut>) { items = list; notifyDataSetChanged() }

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val trigger: TextView = v.findViewById(R.id.trigger)
            val expansion: TextView = v.findViewById(R.id.expansion)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_shortcut, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val s = items[position]
            holder.trigger.text = s.trigger
            holder.expansion.text = s.expansion
            holder.itemView.setOnClickListener { onClick(s) }
            holder.itemView.setOnLongClickListener { onLongClick(s); true }
        }
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
    }
}
