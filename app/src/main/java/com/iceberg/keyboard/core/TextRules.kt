package com.iceberg.keyboard.core

/** Pure text helpers used by the IME; kept free of Android types so they are unit-testable. */
object TextRules {
    /** Characters after which pressing space twice should not insert a period. */
    private const val NO_PERIOD_AFTER = ".,!?;:-–—…/\\@#\n"

    /** Punctuation that "pulls" a preceding auto-inserted space: "word ." -> "word. ". */
    const val SPACE_SWAPPING_PUNCTUATION = ".,!?;:"

    fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'

    /** The run of word characters immediately before the cursor ("hello wor" -> "wor"). */
    fun trailingWord(before: CharSequence): String {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        // Don't treat leading apostrophes as part of the word ('quote).
        while (i < before.length && (before[i] == '\'' || before[i] == '’')) i++
        return before.subSequence(i, before.length).toString()
    }

    /** The run of non-whitespace characters before the cursor; used for shortcut triggers like ";addr". */
    fun trailingToken(before: CharSequence): String {
        var i = before.length
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        return before.subSequence(i, before.length).toString()
    }

    /** True when "…word␣" should become "…word.␣" on a quick second space. */
    fun canInsertPeriodOnDoubleSpace(before: CharSequence): Boolean {
        if (before.length < 2 || before[before.length - 1] != ' ') return false
        val prev = before[before.length - 2]
        if (prev.isWhitespace() || NO_PERIOD_AFTER.indexOf(prev) >= 0) return false
        return prev.isLetterOrDigit() || prev == ')' || prev == '"' || prev == '\'' || prev == '’' || prev == '”' ||
            Character.getType(prev) == Character.OTHER_SYMBOL.toInt() || Character.isSurrogate(prev)
    }

    fun isAllLetters(word: String) = word.isNotEmpty() && word.all { it.isLetter() || it == '\'' || it == '’' }

    /**
     * How many chars "delete previous word" removes: trailing whitespace plus the word before it.
     * A run of punctuation counts as its own word ("hello, " -> deletes ", " first).
     */
    fun lengthToDeletePreviousWord(before: CharSequence): Int {
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        if (i == 0) return before.length
        val wordy = isWordChar(before[i - 1])
        while (i > 0 && !before[i - 1].isWhitespace() && isWordChar(before[i - 1]) == wordy) i--
        return before.length - i
    }
}
