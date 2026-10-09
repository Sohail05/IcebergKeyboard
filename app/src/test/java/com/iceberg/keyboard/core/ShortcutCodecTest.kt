package com.iceberg.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutCodecTest {

    private val sample = listOf(Shortcut("brb", "be right back"), Shortcut(";addr", "1 Main St\nSpringfield"))

    @Test fun `export then import round-trips including newlines and unicode`() {
        val withUnicode = sample + Shortcut("shrug", "¯\\_(ツ)_/¯ 👍🏽")
        val json = ShortcutCodec.export(withUnicode, "2026-10-09T10:00:00Z")
        val result = ShortcutCodec.parse(json)
        assertEquals(ImportFormat.ICEBERG_JSON, result.format)
        assertEquals(withUnicode.sortedBy { it.key }, result.shortcuts.sortedBy { it.key })
        assertTrue(result.skipped.isEmpty())
    }

    @Test fun `export contains format marker and version`() {
        val json = ShortcutCodec.export(sample)
        assertTrue(json.contains("\"format\": \"iceberg-keyboard/shortcuts\""))
        assertTrue(json.contains("\"version\": 1"))
    }

    @Test fun `parses plain json array with alternative key names`() {
        val r = ShortcutCodec.parse("""[{"shortcut":"omw","phrase":"on my way"},{"key":"ty","value":"thank you"}]""")
        assertEquals(ImportFormat.JSON_ARRAY, r.format)
        assertEquals(listOf(Shortcut("omw", "on my way"), Shortcut("ty", "thank you")), r.shortcuts)
    }

    @Test fun `parses json object map and reports non-string values`() {
        val r = ShortcutCodec.parse("""{"omw":"on my way","n":5}""")
        assertEquals(ImportFormat.JSON_MAP, r.format)
        assertEquals(listOf(Shortcut("omw", "on my way")), r.shortcuts)
        assertEquals(1, r.skipped.size)
    }

    @Test fun `rejects files from a newer format version`() {
        val e = runCatching { ShortcutCodec.parse("""{"format":"x","version":99,"shortcuts":[]}""") }.exceptionOrNull()
        assertTrue(e is ShortcutFormatException)
    }

    @Test fun `parses gboard dictionary export, ignoring plain words`() {
        val text = "# Gboard Dictionary version:1\nOn my way\tomw\ten-US\nKubernetes\t\ten-US\n"
        val r = ShortcutCodec.parse(text)
        assertEquals(ImportFormat.GBOARD, r.format)
        assertEquals(listOf(Shortcut("omw", "On my way")), r.shortcuts)
    }

    @Test fun `parses tsv with escapes and skips header and comments`() {
        val text = "trigger\texpansion\n# comment\nsig\tBest,\\nSam\n"
        val r = ShortcutCodec.parse(text)
        assertEquals(ImportFormat.DELIMITED, r.format)
        assertEquals(listOf(Shortcut("sig", "Best,\nSam")), r.shortcuts)
    }

    @Test fun `parses csv with quoted commas and escaped quotes`() {
        val r = ShortcutCodec.parse("addr,\"1 Main St, Apt \"\"B\"\"\"\nty,thanks\n")
        assertEquals(listOf(Shortcut("addr", "1 Main St, Apt \"B\""), Shortcut("ty", "thanks")), r.shortcuts)
    }

    @Test fun `invalid rows are skipped with reasons and duplicates collapse`() {
        val r = ShortcutCodec.parse("bad trigger,x\nok,\nbrb,first\nBRB,second\n")
        assertEquals(listOf(Shortcut("BRB", "second")), r.shortcuts)
        assertEquals(2, r.skipped.size)
        assertTrue(r.skipped[0].contains("whitespace"))
    }

    @Test fun `utf8 bom is ignored`() {
        val r = ShortcutCodec.parse("\uFEFFbrb,be right back")
        assertEquals(1, r.shortcuts.size)
    }

    @Test fun `empty or garbage input throws`() {
        assertTrue(runCatching { ShortcutCodec.parse("   ") }.exceptionOrNull() is ShortcutFormatException)
        assertTrue(runCatching { ShortcutCodec.parse("just one column") }.exceptionOrNull() is ShortcutFormatException)
        assertTrue(runCatching { ShortcutCodec.parse("{not json") }.exceptionOrNull() is ShortcutFormatException)
    }

    @Test fun `merge modes`() {
        val existing = listOf(Shortcut("brb", "be right back"), Shortcut("ty", "thank you"))
        val incoming = listOf(Shortcut("BRB", "back soon"), Shortcut("ty", "thank you"), Shortcut("omw", "on my way"))

        val over = ShortcutCodec.merge(existing, incoming, MergeMode.OVERWRITE_DUPLICATES)
        assertEquals(1, over.added); assertEquals(1, over.updated); assertEquals(1, over.unchanged)
        assertEquals("back soon", over.shortcuts.first { it.key == "brb" }.expansion)

        val keep = ShortcutCodec.merge(existing, incoming, MergeMode.KEEP_EXISTING)
        assertEquals(1, keep.added); assertEquals(0, keep.updated)
        assertEquals("be right back", keep.shortcuts.first { it.key == "brb" }.expansion)

        val replace = ShortcutCodec.merge(existing, incoming, MergeMode.REPLACE_ALL)
        assertEquals(3, replace.shortcuts.size)
    }

    @Test fun `typed case is applied to expansions`() {
        assertEquals("be right back", applyTypedCase("brb", "be right back"))
        assertEquals("Be right back", applyTypedCase("Brb", "be right back"))
        assertEquals("BE RIGHT BACK", applyTypedCase("BRB", "be right back"))
        assertEquals("2026", applyTypedCase(";D", "2026"))
    }
}
