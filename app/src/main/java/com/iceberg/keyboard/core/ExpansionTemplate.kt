package com.iceberg.keyboard.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Renders placeholders inside a shortcut expansion:
 *  {date} 2026-10-09, {time} 14:05, {datetime} 2026-10-09 14:05,
 *  {clipboard} current clipboard text, {cursor} where the caret ends up.
 * Unknown placeholders are left untouched. "{{" produces a literal "{".
 */
object ExpansionTemplate {
    data class Rendered(val text: String, val cursorFromEnd: Int)

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun hasPlaceholders(template: String) = template.contains('{')

    fun render(template: String, now: LocalDateTime, clipboard: () -> String?): Rendered {
        if (!hasPlaceholders(template)) return Rendered(template, 0)
        val sb = StringBuilder()
        var cursorAt = -1
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '{' && i + 1 < template.length && template[i + 1] == '{') {
                sb.append('{'); i += 2; continue
            }
            if (c == '{') {
                val end = template.indexOf('}', i)
                if (end > i) {
                    when (template.substring(i + 1, end)) {
                        "date" -> sb.append(now.format(dateFmt))
                        "time" -> sb.append(now.format(timeFmt))
                        "datetime" -> sb.append(now.format(dateFmt)).append(' ').append(now.format(timeFmt))
                        "clipboard" -> sb.append(clipboard().orEmpty())
                        "cursor" -> if (cursorAt < 0) cursorAt = sb.length
                        else -> sb.append(template, i, end + 1)
                    }
                    i = end + 1
                    continue
                }
            }
            sb.append(c)
            i++
        }
        val cursorFromEnd = if (cursorAt < 0) 0 else sb.length - cursorAt
        return Rendered(sb.toString(), cursorFromEnd)
    }
}
