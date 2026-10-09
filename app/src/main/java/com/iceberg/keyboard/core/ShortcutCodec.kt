package com.iceberg.keyboard.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ShortcutEntry(val trigger: String, val expansion: String)

/** On-disk / export format. See doc/SHORTCUTS.md. */
@Serializable
data class ShortcutFile(
    val format: String = ShortcutCodec.FORMAT_ID,
    val version: Int = ShortcutCodec.FORMAT_VERSION,
    val exportedAt: String? = null,
    val shortcuts: List<ShortcutEntry> = emptyList(),
)

enum class ImportFormat { ICEBERG_JSON, JSON_ARRAY, JSON_MAP, GBOARD, DELIMITED }

data class ImportResult(
    val format: ImportFormat,
    val shortcuts: List<Shortcut>,
    /** One entry per rejected record, e.g. "line 4: trigger contains whitespace". */
    val skipped: List<String>,
)

enum class MergeMode {
    /** Add new shortcuts; imported ones win over existing ones with the same trigger. */
    OVERWRITE_DUPLICATES,

    /** Add new shortcuts; existing ones with the same trigger are kept. */
    KEEP_EXISTING,

    /** Delete everything and use only the imported set. */
    REPLACE_ALL,
}

data class MergeOutcome(val shortcuts: List<Shortcut>, val added: Int, val updated: Int, val unchanged: Int)

class ShortcutFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ShortcutCodec {
    const val FORMAT_ID = "iceberg-keyboard/shortcuts"
    const val FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val triggerKeys = listOf("trigger", "shortcut", "key", "abbreviation")
    private val expansionKeys = listOf("expansion", "phrase", "text", "value", "word")

    fun export(shortcuts: Collection<Shortcut>, exportedAt: String? = null): String {
        val entries = shortcuts.sortedBy { it.key }.map { ShortcutEntry(it.trigger, it.expansion) }
        return json.encodeToString(ShortcutFile.serializer(), ShortcutFile(exportedAt = exportedAt, shortcuts = entries))
    }

    /**
     * Parses any supported import format (auto-detected):
     * Iceberg JSON, a JSON array of objects, a JSON object map, Gboard dictionary text, or TSV/CSV.
     * Later duplicates (case-insensitive trigger) replace earlier ones.
     */
    fun parse(raw: String): ImportResult {
        val text = raw.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) throw ShortcutFormatException("File is empty")
        return if (text.startsWith("{") || text.startsWith("[")) parseJson(text) else parseLines(text)
    }

    fun merge(existing: Collection<Shortcut>, incoming: Collection<Shortcut>, mode: MergeMode): MergeOutcome {
        if (mode == MergeMode.REPLACE_ALL) {
            val result = dedupe(incoming)
            return MergeOutcome(result, added = result.size, updated = 0, unchanged = 0)
        }
        val map = LinkedHashMap<String, Shortcut>()
        existing.forEach { map[it.key] = it }
        var added = 0
        var updated = 0
        var unchanged = 0
        for (s in dedupe(incoming)) {
            val current = map[s.key]
            when {
                current == null -> { map[s.key] = s; added++ }
                current == s -> unchanged++
                mode == MergeMode.OVERWRITE_DUPLICATES -> { map[s.key] = s; updated++ }
                else -> unchanged++
            }
        }
        return MergeOutcome(map.values.toList(), added, updated, unchanged)
    }

    private fun dedupe(items: Collection<Shortcut>): List<Shortcut> {
        val map = LinkedHashMap<String, Shortcut>()
        items.forEach { map[it.key] = it }
        return map.values.toList()
    }

    // ---- JSON ----

    private fun parseJson(text: String): ImportResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ShortcutFormatException("Invalid JSON: ${e.message}", e)
        }
        val collector = Collector()
        val format: ImportFormat
        when {
            root is JsonObject && root["shortcuts"] is JsonArray -> {
                format = ImportFormat.ICEBERG_JSON
                val version = (root["version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                if (version != null && version > FORMAT_VERSION) {
                    throw ShortcutFormatException("File was written by a newer version (format v$version)")
                }
                readArray(root["shortcuts"] as JsonArray, collector)
            }
            root is JsonArray -> {
                format = ImportFormat.JSON_ARRAY
                readArray(root, collector)
            }
            root is JsonObject -> {
                format = ImportFormat.JSON_MAP
                root.entries.forEachIndexed { i, (k, v) ->
                    val value = (v as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (value == null) collector.skip("entry ${i + 1} (\"$k\"): value is not a string")
                    else collector.add("entry ${i + 1}", k, value)
                }
            }
            else -> throw ShortcutFormatException("Unsupported JSON structure")
        }
        return collector.result(format)
    }

    private fun readArray(array: JsonArray, collector: Collector) {
        array.forEachIndexed { i, el ->
            val label = "item ${i + 1}"
            val obj = el as? JsonObject
            if (obj == null) {
                collector.skip("$label: not an object")
                return@forEachIndexed
            }
            val trigger = obj.firstString(triggerKeys)
            val expansion = obj.firstString(expansionKeys)
            if (trigger == null || expansion == null) collector.skip("$label: missing trigger or expansion")
            else collector.add(label, trigger, expansion)
        }
    }

    private fun JsonObject.firstString(keys: List<String>): String? {
        for (k in keys) {
            val el: JsonElement = this[k] ?: continue
            val p = el as? JsonPrimitive ?: continue
            if (p.isString) return p.content
        }
        return null
    }

    // ---- Line based (Gboard / TSV / CSV) ----

    private fun parseLines(text: String): ImportResult {
        val lines = text.lines()
        val collector = Collector()
        val isGboard = lines.first().contains("Gboard Dictionary", ignoreCase = true)
        lines.forEachIndexed { index, rawLine ->
            val lineNo = index + 1
            val line = rawLine.trimEnd('\r')
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
            if (isGboard && index == 0) return@forEachIndexed
            if (isGboard) {
                // Gboard export: word<TAB>shortcut<TAB>locale... (plain dictionary words have no shortcut).
                val cols = line.split('\t')
                val shortcut = cols.getOrNull(1)?.trim().orEmpty()
                if (shortcut.isEmpty()) return@forEachIndexed
                collector.add("line $lineNo", shortcut, cols[0])
                return@forEachIndexed
            }
            val cols = if (line.contains('\t')) line.split('\t') else splitCsv(line)
            if (cols.size < 2) {
                collector.skip("line $lineNo: expected two columns (trigger, expansion)")
                return@forEachIndexed
            }
            val trigger = cols[0].trim()
            if (lineNo == 1 && trigger.equals("trigger", true) || lineNo == 1 && trigger.equals("shortcut", true)) {
                return@forEachIndexed // header row
            }
            collector.add("line $lineNo", trigger, unescape(cols[1]))
        }
        if (collector.isEmpty()) {
            throw ShortcutFormatException("No shortcuts found. Supported: JSON, Gboard dictionary, TSV or CSV.")
        }
        return collector.result(if (isGboard) ImportFormat.GBOARD else ImportFormat.DELIMITED)
    }

    /** Minimal RFC 4180 field splitting for a single line (quoted fields may contain commas and ""). */
    internal fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    /** Delimited formats can't hold raw newlines/tabs, so "\n" and "\t" escapes are supported. */
    internal fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2; continue }
                    't' -> { sb.append('\t'); i += 2; continue }
                    '\\' -> { sb.append('\\'); i += 2; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private class Collector {
        private val map = LinkedHashMap<String, Shortcut>()
        private val skipped = ArrayList<String>()

        fun add(label: String, trigger: String, expansion: String) {
            val t = trigger.trim()
            val problem = Shortcut.validate(t, expansion)
            if (problem != null) skipped.add("$label: $problem") else Shortcut(t, expansion).let { map[it.key] = it }
        }

        fun skip(reason: String) { skipped.add(reason) }
        fun isEmpty() = map.isEmpty()
        fun result(format: ImportFormat) = ImportResult(format, map.values.toList(), skipped)
    }
}
