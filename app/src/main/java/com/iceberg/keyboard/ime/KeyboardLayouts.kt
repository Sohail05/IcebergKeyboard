package com.iceberg.keyboard.ime

import android.graphics.RectF

enum class KeyCode { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, TO_SYMBOLS, TO_SYMBOLS_2, TO_LETTERS, SETTINGS_COMMA }

/**
 * One key. [width] and [leftGap] are in "key units" (a standard letter key = 1).
 * Geometry ([rect], [touchLeft], [touchRight]) is filled in by [KeyboardView] on layout, so layouts are
 * built fresh per configuration and never shared between views.
 */
class Key(
    val code: KeyCode,
    val label: String,
    val output: String = label,
    val width: Float = 1f,
    val alternates: List<String> = emptyList(),
    val leftGap: Float = 0f,
    hint: String? = null,
) {
    val hint: String? = hint ?: alternates.firstOrNull()
    val isLetter: Boolean = code == KeyCode.CHAR && output.length == 1 && output[0].isLetter()

    val rect = RectF()
    var touchLeft = 0f
    var touchRight = 0f
}

class KeyboardLayout(val id: String, val rows: List<List<Key>>)

/** Field-dependent tweaks applied to the bottom row of the letters layout. */
enum class FieldVariant { TEXT, EMAIL, URI }

object KeyboardLayouts {

    /** Long-press alternates. Digits/symbols come first so a long-press + release types them. */
    private val ALTERNATES: Map<Char, List<String>> = mapOf(
        'q' to listOf("1"), 'w' to listOf("2"),
        'e' to listOf("3", "é", "è", "ê", "ë", "ē", "ę"),
        'r' to listOf("4"), 't' to listOf("5", "þ"),
        'y' to listOf("6", "ý", "ÿ"),
        'u' to listOf("7", "ú", "ù", "û", "ü", "ū"),
        'i' to listOf("8", "í", "ì", "î", "ï", "ī"),
        'o' to listOf("9", "ó", "ò", "ô", "ö", "õ", "ø", "œ", "ō"),
        'p' to listOf("0"),
        'a' to listOf("@", "á", "à", "â", "ä", "ã", "å", "æ", "ā"),
        's' to listOf("#", "ß", "ś", "š"),
        'd' to listOf("$", "ð"), 'f' to listOf("%"), 'g' to listOf("&", "ğ"), 'h' to listOf("-"),
        'j' to listOf("+"), 'k' to listOf("("), 'l' to listOf(")", "ł"),
        'z' to listOf("*", "ž", "ź", "ż"), 'x' to listOf("\""), 'c' to listOf("'", "ç", "ć", "č"),
        'v' to listOf(":"), 'b' to listOf(";"), 'n' to listOf("!", "ñ", "ń"), 'm' to listOf("?"),
    )

    private val PERIOD_ALTS = listOf("?", "!", ",", "'", "\"", ":", ";", "-", "…", "/")
    private val SYMBOL_ALTS: Map<String, List<String>> = mapOf(
        "1" to listOf("¹", "½", "⅓", "¼", "⅛"), "2" to listOf("²", "⅔"), "3" to listOf("³", "¾", "⅜"),
        "4" to listOf("⁴"), "5" to listOf("⅝"), "7" to listOf("⅞"), "0" to listOf("°", "ⁿ", "∅"),
        "$" to listOf("€", "£", "¥", "¢", "₹", "₩"), "-" to listOf("–", "—", "_", "·"),
        "+" to listOf("±"), "(" to listOf("[", "{", "<"), ")" to listOf("]", "}", ">"),
        "\"" to listOf("“", "”", "„", "«", "»"), "'" to listOf("‘", "’", "‚", "‹", "›"),
        "!" to listOf("¡"), "?" to listOf("¿"), "*" to listOf("†", "‡", "★"), "%" to listOf("‰"),
        "=" to listOf("≠", "≈", "∞"), "/" to listOf("\\"), "&" to listOf("§"),
    )

    private fun letter(c: Char, gap: Float = 0f) =
        Key(KeyCode.CHAR, c.toString(), c.toString(), alternates = ALTERNATES[c].orEmpty(), leftGap = gap)

    private fun sym(s: String) = Key(KeyCode.CHAR, s, s, alternates = SYMBOL_ALTS[s].orEmpty())
    private fun shift() = Key(KeyCode.SHIFT, "⇧", width = 1.5f)
    private fun backspace() = Key(KeyCode.BACKSPACE, "⌫", width = 1.5f)
    private fun enter() = Key(KeyCode.ENTER, "↵", width = 1.5f)
    private fun space(width: Float) = Key(KeyCode.SPACE, "English", " ", width = width)
    private fun period() = Key(KeyCode.CHAR, ".", ".", alternates = PERIOD_ALTS)
    private fun numberRow() = "1234567890".map { sym(it.toString()) }

    fun letters(numberRow: Boolean, variant: FieldVariant): KeyboardLayout {
        val rows = ArrayList<List<Key>>()
        if (numberRow) rows += numberRow()
        rows += "qwertyuiop".map { letter(it) }
        rows += "asdfghjkl".mapIndexed { i, c -> letter(c, if (i == 0) 0.5f else 0f) }
        rows += listOf(shift()) + "zxcvbnm".map { letter(it) } + backspace()
        val left = when (variant) {
            FieldVariant.EMAIL -> Key(KeyCode.CHAR, "@", "@", alternates = listOf(".com", "-", "_", "+"))
            FieldVariant.URI -> Key(KeyCode.CHAR, "/", "/", alternates = listOf(".com", ".org", ".net", "-", "_", ":"))
            FieldVariant.TEXT -> Key(KeyCode.SETTINGS_COMMA, ",", ",", hint = "⚙")
        }
        rows += listOf(
            Key(KeyCode.TO_SYMBOLS, "?123", width = 1.5f),
            left,
            space(if (variant == FieldVariant.TEXT) 5f else 4f),
        ) + (if (variant != FieldVariant.TEXT) listOf(Key(KeyCode.CHAR, ".com", ".com", alternates = listOf(".org", ".net", ".io", ".co.uk"))) else emptyList()) +
            listOf(period(), enter())
        return KeyboardLayout("letters-$numberRow-$variant", rows)
    }

    fun symbols(): KeyboardLayout = KeyboardLayout(
        "symbols",
        listOf(
            numberRow(),
            listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { sym(it) },
            listOf(Key(KeyCode.TO_SYMBOLS_2, "=\\<", width = 1.5f)) +
                listOf("*", "\"", "'", ":", ";", "!", "?").map { sym(it) } + backspace(),
            listOf(Key(KeyCode.TO_LETTERS, "ABC", width = 1.5f), sym(","), space(5f), period(), enter()),
        ),
    )

    fun symbols2(): KeyboardLayout = KeyboardLayout(
        "symbols2",
        listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { sym(it) },
            listOf("£", "€", "¥", "^", "°", "=", "{", "}", "\\", "%").map { sym(it) },
            listOf(Key(KeyCode.TO_SYMBOLS, "?123", width = 1.5f)) +
                listOf("©", "®", "™", "✓", "[", "]", "<").map { sym(it) } + backspace(),
            listOf(Key(KeyCode.TO_LETTERS, "ABC", width = 1.5f), sym(">"), space(5f), sym("…"), enter()),
        ),
    )

    /** Numeric / phone / date fields. */
    fun numpad(phone: Boolean): KeyboardLayout {
        fun n(s: String, alts: List<String> = emptyList()) = Key(KeyCode.CHAR, s, s, width = 1f, alternates = alts)
        return KeyboardLayout(
            "numpad-$phone",
            listOf(
                listOf(n("1"), n("2"), n("3"), n("-", listOf("(", ")", "/"))),
                listOf(n("4"), n("5"), n("6"), Key(KeyCode.SPACE, "␣", " ")),
                listOf(n("7"), n("8"), n("9"), Key(KeyCode.BACKSPACE, "⌫")),
                listOf(
                    if (phone) n("*", listOf("#", "+", "N", ";")) else n(",", listOf(":", "/")),
                    n("0", if (phone) listOf("+") else emptyList()),
                    if (phone) n("#", listOf("+", "*")) else n(".", listOf(",")),
                    Key(KeyCode.ENTER, "↵"),
                ),
            ),
        )
    }
}
