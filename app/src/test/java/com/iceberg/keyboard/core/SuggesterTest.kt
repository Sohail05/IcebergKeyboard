package com.iceberg.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggesterTest {
    private val words = listOf("the", "of", "and", "to", "a", "in", "for", "is", "on", "that", "this", "with",
        "you", "it", "not", "or", "be", "are", "from", "at", "as", "your", "have", "new", "more", "about",
        "keyboard", "key", "keep", "hello", "help", "world", "would", "there", "their", "they")
    private val s = Suggester(words)

    @Test fun `completes prefixes by frequency`() {
        val r = s.suggest("th").map { it.word }
        assertEquals("the", r.first())
        assertTrue(r.all { it.startsWith("th") || it.length <= 4 })
    }

    @Test fun `keeps typed capitalisation`() {
        assertEquals("Keyboard", s.suggest("Keyb").first().word)
        assertEquals("HELLO", s.suggest("HELL").first().word)
    }

    @Test fun `autocorrects transpositions and neighbour-key typos`() {
        assertEquals("the", s.autocorrect("teh"))
        assertEquals("The", s.autocorrect("Teh"))
        assertEquals("hello", s.autocorrect("hrllo")) // r is next to e
    }

    @Test fun `never corrects known words, numbers or mixed tokens`() {
        assertNull(s.autocorrect("the"))
        assertNull(s.autocorrect("b2b"))
        assertNull(s.autocorrect("xqzvw"))
    }

    @Test fun `handles contractions and lone i`() {
        assertEquals("don't", s.autocorrect("dont"))
        assertEquals("I'm", s.autocorrect("im"))
        assertEquals("I", s.autocorrect("i"))
    }

    @Test fun `apostrophe-less corpus forms are replaced by contractions`() {
        val withBare = Suggester(listOf("the", "dont", "im"))
        assertFalse(withBare.isKnown("dont"))
        assertTrue(withBare.isKnown("don't"))
        assertEquals("don't", withBare.autocorrect("dont"))
        assertEquals("I'm", withBare.autocorrect("im"))
        assertEquals("don't", withBare.suggest("don").first().word)
    }

    @Test fun `learned words stop being corrected and are suggested`() {
        assertFalse(s.isKnown("kotlin"))
        s.learn("kotlin"); s.learn("kotlin")
        assertTrue(s.isKnown("kotlin"))
        assertNull(s.autocorrect("kotlin"))
        assertEquals("kotlin", s.suggest("kot").first().word)
        assertTrue(s.forget("kotlin"))
        assertFalse(s.isKnown("kotlin"))
    }

    @Test fun `user words export and import`() {
        s.learn("zed", 3)
        val other = Suggester(words)
        other.importUserWords(s.exportUserWords())
        assertTrue(other.isKnown("zed"))
    }

    @Test fun `rejects junk when learning`() {
        assertFalse(s.learn("a"))
        assertFalse(s.learn("abc123"))
    }
}
