package com.iceberg.keyboard.core

/**
 * A text-expansion shortcut: typing [trigger] offers (or auto-inserts) [expansion].
 * Triggers are matched case-insensitively and may contain punctuation (e.g. ";addr"), but no whitespace.
 */
data class Shortcut(val trigger: String, val expansion: String) {
    val key: String get() = trigger.lowercase()

    companion object {
        const val MAX_TRIGGER_LENGTH = 64
        const val MAX_EXPANSION_LENGTH = 10_000

        /** Returns null when valid, otherwise a human-readable reason. */
        fun validate(trigger: String, expansion: String): String? = when {
            trigger.isEmpty() -> "trigger is empty"
            trigger.any { it.isWhitespace() } -> "trigger contains whitespace"
            trigger.length > MAX_TRIGGER_LENGTH -> "trigger longer than $MAX_TRIGGER_LENGTH characters"
            expansion.isEmpty() -> "expansion is empty"
            expansion.length > MAX_EXPANSION_LENGTH -> "expansion longer than $MAX_EXPANSION_LENGTH characters"
            else -> null
        }
    }
}

/**
 * Applies the capitalisation the user typed to an expansion:
 * "brb" -> as stored, "Brb" -> first letter upper-cased, "BRB" -> whole expansion upper-cased.
 */
fun applyTypedCase(typed: String, replacement: String): String {
    val letters = typed.filter { it.isLetter() }
    if (letters.isEmpty() || replacement.isEmpty()) return replacement
    return when {
        letters.length > 1 && letters.all { it.isUpperCase() } -> replacement.uppercase()
        letters.first().isUpperCase() -> replacement.replaceFirstChar { it.titlecase() }
        else -> replacement
    }
}
