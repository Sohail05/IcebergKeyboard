package com.iceberg.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TextRulesTest {
    @Test fun `trailing word and token`() {
        assertEquals("wor", TextRules.trailingWord("hello wor"))
        assertEquals("don't", TextRules.trailingWord("I don't"))
        assertEquals("quote", TextRules.trailingWord("say 'quote"))
        assertEquals("", TextRules.trailingWord("end. "))
        assertEquals(";addr", TextRules.trailingToken("my ;addr"))
        assertEquals("addr", TextRules.trailingWord("my ;addr"))
    }

    @Test fun `double space period rules`() {
        assertTrue(TextRules.canInsertPeriodOnDoubleSpace("hello "))
        assertTrue(TextRules.canInsertPeriodOnDoubleSpace("(yes) "))
        assertFalse(TextRules.canInsertPeriodOnDoubleSpace("hello. "))
        assertFalse(TextRules.canInsertPeriodOnDoubleSpace("hello  "))
        assertFalse(TextRules.canInsertPeriodOnDoubleSpace(" "))
        assertFalse(TextRules.canInsertPeriodOnDoubleSpace("hello"))
    }

    @Test fun `delete previous word`() {
        assertEquals(6, TextRules.lengthToDeletePreviousWord("hello world "))
        assertEquals(5, TextRules.lengthToDeletePreviousWord("hello"))
        assertEquals(2, TextRules.lengthToDeletePreviousWord("hello, "))
        assertEquals(3, TextRules.lengthToDeletePreviousWord("   "))
    }

    @Test fun `expansion templates`() {
        val now = LocalDateTime.of(2026, 10, 9, 14, 5)
        assertEquals(ExpansionTemplate.Rendered("Today is 2026-10-09 at 14:05", 0),
            ExpansionTemplate.render("Today is {date} at {time}", now) { null })
        assertEquals(ExpansionTemplate.Rendered("<b></b>", 4), ExpansionTemplate.render("<b>{cursor}</b>", now) { null })
        assertEquals("clip: X", ExpansionTemplate.render("clip: {clipboard}", now) { "X" }.text)
        assertEquals("{unknown} {", ExpansionTemplate.render("{unknown} {{", now) { null }.text)
        assertEquals("plain", ExpansionTemplate.render("plain", now) { error("not called") }.text)
    }
}
