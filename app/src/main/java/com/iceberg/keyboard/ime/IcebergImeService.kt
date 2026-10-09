package com.iceberg.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.iceberg.keyboard.core.ExpansionTemplate
import com.iceberg.keyboard.core.Shortcut
import com.iceberg.keyboard.core.Suggester
import com.iceberg.keyboard.core.TextRules
import com.iceberg.keyboard.core.applyTypedCase
import com.iceberg.keyboard.data.Dictionary
import com.iceberg.keyboard.data.KeyboardPrefs
import com.iceberg.keyboard.data.ShortcutStore
import com.iceberg.keyboard.ui.SettingsActivity
import java.time.LocalDateTime

class IcebergImeService : InputMethodService(), KeyboardView.Listener, SuggestionStripView.Listener {

    private enum class Mode { LETTERS, SYMBOLS, SYMBOLS2, NUMPAD }
    private enum class Shift { OFF, ON, LOCKED }

    /** What the current editor field allows; derived from [EditorInfo]. */
    private data class Field(
        val numeric: Boolean = false,
        val phone: Boolean = false,
        val password: Boolean = false,
        val suggestions: Boolean = true,
        val learn: Boolean = true,
        val autoCap: Boolean = true,
        val variant: FieldVariant = FieldVariant.TEXT,
        val rawKeys: Boolean = false,
    )

    /** Lets backspace revert the last autocorrection or shortcut expansion. */
    private data class Undo(val original: String, val inserted: String)

    private lateinit var prefs: KeyboardPrefs
    private lateinit var shortcuts: ShortcutStore
    private var vibrator: Vibrator? = null
    private var audio: AudioManager? = null

    private var keyboardView: KeyboardView? = null
    private var strip: SuggestionStripView? = null
    private var emojiPanel: EmojiPanel? = null

    private var mode = Mode.LETTERS
    private var shift = Shift.OFF
    private var field = Field()
    private val layoutCache = HashMap<Mode, KeyboardLayout>()

    private var lastShiftTapAt = 0L
    private var lastSpaceAt = 0L
    private var lastOwnEditAt = 0L
    private var lastSelStart = -1
    private var lastSelEnd = -1
    private var undo: Undo? = null
    private var rejectedWord: String? = null
    /** True right after a suggestion pick inserted "word␣", enabling "word." punctuation swapping. */
    private var autoSpace = false

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshSuggestions() }
    private val shortcutListener: () -> Unit = { handler.post { scheduleRefresh() } }

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPrefs(this)
        shortcuts = ShortcutStore.get(this)
        shortcuts.addListener(shortcutListener)
        vibrator = getSystemService(Vibrator::class.java)
        audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        Dictionary.ensureLoaded(this)
    }

    override fun onDestroy() {
        shortcuts.removeListener(shortcutListener)
        handler.removeCallbacksAndMessages(null)
        Dictionary.saveIfDirty(this)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val kv = KeyboardView(this).also { it.listener = this }
        val st = SuggestionStripView(this).also { it.listener = this }
        val emoji = EmojiPanel(
            this,
            onEmoji = { commitEmoji(it) },
            onBackspace = { handleBackspace() },
            onClose = { showEmoji(false) },
        ).apply { visibility = View.GONE }
        val keys = FrameLayout(this).apply {
            addView(kv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(emoji, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        keyboardView = kv
        strip = st
        emojiPanel = emoji
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(st, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(keys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Landscape fullscreen "extract" mode hides the app; a phone keyboard is better without it. */
    override fun onEvaluateFullscreenMode() = false

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        field = fieldFor(attribute)
        undo = null
        rejectedWord = null
        autoSpace = false
        lastSpaceAt = 0
        lastSelStart = attribute.initialSelStart
        lastSelEnd = attribute.initialSelEnd
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyPrefs()
        if (!restarting || mode == Mode.NUMPAD || field.numeric) {
            mode = if (field.numeric) Mode.NUMPAD else Mode.LETTERS
        }
        if (!restarting) shift = Shift.OFF
        keyboardView?.keyboardLayout = layoutFor(mode)
        keyboardView?.enterLabel = enterLabel(info)
        showEmoji(false)
        updateShiftFromEditor()
        scheduleRefresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Dictionary.saveIfDirty(this)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd)
        lastSelStart = newSelStart
        lastSelEnd = newSelEnd
        // Our own edits already updated shift synchronously; only react to external cursor moves,
        // otherwise a late callback could undo a shift tap the user just made.
        if (SystemClock.uptimeMillis() - lastOwnEditAt > 300) {
            undo = null
            autoSpace = false
            updateShiftFromEditor()
        }
        scheduleRefresh()
    }

    // ---- Configuration ----

    private fun applyPrefs() {
        val theme = KeyboardTheme.resolve(this, prefs.theme)
        layoutCache.clear()
        keyboardView?.apply {
            this.theme = theme
            rowHeightPx = (prefs.keyHeightDp * resources.displayMetrics.density).toInt()
            showHints = prefs.keyHints
            longPressMs = prefs.longPressMs.toLong()
            spaceCursorEnabled = prefs.spaceCursor
        }
        strip?.theme = theme
        emojiPanel?.bind(theme, prefs.recentEmoji)
    }

    private fun layoutFor(m: Mode): KeyboardLayout = layoutCache.getOrPut(m) {
        when (m) {
            Mode.LETTERS -> KeyboardLayouts.letters(prefs.numberRow || field.password, field.variant)
            Mode.SYMBOLS -> KeyboardLayouts.symbols()
            Mode.SYMBOLS2 -> KeyboardLayouts.symbols2()
            Mode.NUMPAD -> KeyboardLayouts.numpad(field.phone)
        }
    }

    private fun fieldFor(ei: EditorInfo): Field {
        val type = ei.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val flags = type and InputType.TYPE_MASK_FLAGS
        val text = cls == InputType.TYPE_CLASS_TEXT
        val password = (text && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val email = text && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val uri = text && variation == InputType.TYPE_TEXT_VARIATION_URI
        val noSuggestions = flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val incognito = ei.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        return Field(
            numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME,
            phone = cls == InputType.TYPE_CLASS_PHONE,
            password = password,
            suggestions = text && !password && !email && !uri && !noSuggestions,
            learn = !password && !incognito,
            autoCap = text && !password && !email && !uri,
            variant = when { email -> FieldVariant.EMAIL; uri -> FieldVariant.URI; else -> FieldVariant.TEXT },
            rawKeys = type == InputType.TYPE_NULL,
        )
    }

    private fun enterLabel(ei: EditorInfo): String {
        if (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "↵"
        return when (ei.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            else -> "↵"
        }
    }

    // ---- KeyboardView.Listener ----

    override fun onKeyDown(key: Key) {
        feedback(key)
        if (key.code == KeyCode.BACKSPACE) handleBackspace()
    }

    override fun onKeyCommit(key: Key, text: String?) {
        when (key.code) {
            KeyCode.CHAR, KeyCode.SETTINGS_COMMA -> handleText(text ?: key.output)
            KeyCode.SPACE -> handleSpace()
            KeyCode.ENTER -> handleEnter()
            KeyCode.SHIFT -> handleShiftTap()
            KeyCode.TO_SYMBOLS -> setMode(Mode.SYMBOLS)
            KeyCode.TO_SYMBOLS_2 -> setMode(Mode.SYMBOLS2)
            KeyCode.TO_LETTERS -> setMode(if (field.numeric) Mode.NUMPAD else Mode.LETTERS)
            KeyCode.BACKSPACE -> Unit
        }
    }

    override fun onKeyLongPress(key: Key): Boolean = when (key.code) {
        KeyCode.SPACE -> { imm().showInputMethodPicker(); true }
        KeyCode.SETTINGS_COMMA -> { openSettings(); true }
        KeyCode.SHIFT -> { shift = Shift.LOCKED; renderShift(); true }
        KeyCode.ENTER -> { currentInputConnection?.commitText("\n", 1); markEdit(); true }
        else -> false
    }

    override fun onBackspaceRepeat(count: Int) {
        if (count <= 12) { handleBackspace(); return }
        // After ~0.9 s of holding, accelerate to whole words.
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0) ?: return
        val n = TextRules.lengthToDeletePreviousWord(before)
        if (n > 0) ic.deleteSurroundingText(n, 0) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        afterDelete()
    }

    override fun onCursorMove(delta: Int) {
        val ic = currentInputConnection ?: return
        undo = null
        autoSpace = false
        if (lastSelEnd < 0) {
            val code = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            repeat(kotlin.math.abs(delta)) { sendDownUpKeyEvents(code) }
            return
        }
        val move = if (delta < 0) -minOf(-delta, ic.getTextBeforeCursor(-delta, 0)?.length ?: 0)
        else minOf(delta, ic.getTextAfterCursor(delta, 0)?.length ?: 0)
        if (move == 0) return
        val pos = lastSelEnd + move
        lastSelStart = pos
        lastSelEnd = pos
        ic.setSelection(pos, pos)
    }

    // ---- Typing ----

    private fun handleText(raw: String) {
        val ic = currentInputConnection ?: return
        var text = raw
        if (shift != Shift.OFF && mode == Mode.LETTERS && text.length == 1 && text[0].isLetter()) text = text.uppercase()
        if (text.length == 1 && TextRules.SPACE_SWAPPING_PUNCTUATION.indexOf(text[0]) >= 0) {
            handlePunctuation(text)
            return
        }
        ic.commitText(text, 1)
        undo = null
        autoSpace = false
        markEdit()
        if (shift == Shift.ON) { shift = Shift.OFF; renderShift() }
        scheduleRefresh()
    }

    private fun handlePunctuation(p: String) {
        val ic = currentInputConnection ?: return
        val before = textBefore(64)
        ic.beginBatchEdit()
        if (autoSpace && prefs.smartPunctuation && before.endsWith(" ")) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("$p ", 1)
            undo = null
        } else if (!maybeAutocorrect(before, p)) {
            learnWord(TextRules.trailingWord(before))
            ic.commitText(p, 1)
            undo = null
        }
        ic.endBatchEdit()
        autoSpace = false
        lastSpaceAt = 0
        afterSeparator()
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val before = textBefore(64)

        if (prefs.shortcutAutoExpand && !field.password) {
            val token = TextRules.trailingToken(before)
            val shortcut = shortcuts.find(token)
            if (shortcut != null && !token.equals(rejectedWord, ignoreCase = true)) {
                expandShortcut(token, shortcut)
                return
            }
        }

        if (maybeAutocorrect(before, " ")) {
            lastSpaceAt = 0
            afterSeparator()
            return
        }

        if (prefs.doubleSpacePeriod && field.autoCap && now - lastSpaceAt < DOUBLE_SPACE_MS &&
            TextRules.canInsertPeriodOnDoubleSpace(before)
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            lastSpaceAt = 0
            undo = null
            afterSeparator()
            return
        }

        learnWord(TextRules.trailingWord(before))
        ic.commitText(" ", 1)
        lastSpaceAt = now
        undo = null
        rejectedWord = null
        autoSpace = false
        afterSeparator()
    }

    private fun handleEnter() {
        learnWord(TextRules.trailingWord(textBefore(64)))
        sendKeyChar('\n') // performs the editor action or sends a raw Enter, as the field expects
        undo = null
        autoSpace = false
        lastSpaceAt = 0
        afterSeparator()
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val u = undo
        if (u != null) {
            undo = null
            if (ic.getTextBeforeCursor(u.inserted.length, 0)?.toString() == u.inserted) {
                replaceTrailing(u.inserted, u.original)
                rejectedWord = u.original
                afterDelete()
                return
            }
        }
        autoSpace = false
        when {
            lastSelStart >= 0 && lastSelStart != lastSelEnd -> ic.commitText("", 1)
            field.rawKeys -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            else -> {
                val before = ic.getTextBeforeCursor(32, 0)
                if (before.isNullOrEmpty()) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                } else {
                    // Delete a whole grapheme so emoji like 👍🏽 or 👨‍👩‍👧 go in one press.
                    val s = before.toString()
                    val bi = BreakIterator.getCharacterInstance().apply { setText(s) }
                    val start = bi.preceding(s.length).let { if (it == BreakIterator.DONE) s.length - 1 else it }
                    ic.deleteSurroundingText(s.length - start, 0)
                }
            }
        }
        afterDelete()
    }

    private fun afterDelete() {
        lastSpaceAt = 0
        markEdit()
        updateShiftFromEditor()
        scheduleRefresh()
    }

    private fun afterSeparator() {
        markEdit()
        updateShiftFromEditor()
        scheduleRefresh()
    }

    private fun markEdit() { lastOwnEditAt = SystemClock.uptimeMillis() }

    /** Corrects the word before the cursor and appends [separator]. Returns false if nothing was done. */
    private fun maybeAutocorrect(before: String, separator: String): Boolean {
        if (!prefs.autocorrect || !field.suggestions) return false
        val word = TextRules.trailingWord(before)
        if (word.isEmpty() || word == rejectedWord) return false
        // Only correct standalone words, not fragments of emails, URLs or hashtags.
        val token = TextRules.trailingToken(before)
        if (token.length != word.length && token.dropLast(word.length).any { it !in "\"'([{¿¡“‘" }) return false
        val fix = Dictionary.suggester?.autocorrect(word) ?: return false
        if (fix == word) return false
        val inserted = fix + separator
        replaceTrailing(word, inserted)
        undo = Undo(word, inserted)
        learnWord(fix)
        return true
    }

    private fun expandShortcut(token: String, shortcut: Shortcut) {
        val rendered = ExpansionTemplate.render(shortcut.expansion, LocalDateTime.now()) { clipboardText() }
        val body = applyTypedCase(token, rendered.text)
        val needsSpace = rendered.cursorFromEnd == 0 && body.lastOrNull()?.isWhitespace() != true
        val inserted = if (needsSpace) "$body " else body
        replaceTrailing(token, inserted)
        if (rendered.cursorFromEnd > 0) {
            moveCursorBack(rendered.cursorFromEnd)
            undo = null
        } else {
            undo = Undo(token, inserted)
        }
        autoSpace = false
        lastSpaceAt = 0
        afterSeparator()
    }

    private fun replaceTrailing(old: String, new: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(old.length, 0)
        ic.commitText(new, 1)
        ic.endBatchEdit()
        markEdit()
    }

    private fun moveCursorBack(chars: Int) {
        val ic = currentInputConnection ?: return
        val et = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        if (et != null && et.selectionEnd >= 0) {
            val pos = et.startOffset + et.selectionEnd - chars
            ic.setSelection(pos, pos)
        } else {
            repeat(chars) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) }
        }
    }

    private fun commitEmoji(e: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(e, 1)
        markEdit()
        if (e.isNotBlank()) prefs.recentEmoji = (listOf(e) + prefs.recentEmoji.filter { it != e }).take(MAX_RECENT_EMOJI)
    }

    private fun learnWord(word: String) {
        if (prefs.learnWords && field.learn && word.isNotEmpty()) Dictionary.learn(word)
    }

    private fun textBefore(n: Int): String = currentInputConnection?.getTextBeforeCursor(n, 0)?.toString().orEmpty()

    // ---- Shift / mode ----

    private fun handleShiftTap() {
        val now = SystemClock.uptimeMillis()
        val doubleTap = now - lastShiftTapAt < DOUBLE_TAP_MS
        shift = when (shift) {
            Shift.OFF -> if (doubleTap) Shift.LOCKED else Shift.ON
            Shift.ON -> if (doubleTap) Shift.LOCKED else Shift.OFF
            Shift.LOCKED -> Shift.OFF
        }
        lastShiftTapAt = now
        renderShift()
    }

    private fun updateShiftFromEditor() {
        if (shift != Shift.LOCKED && mode == Mode.LETTERS) {
            shift = if (prefs.autoCap && field.autoCap && cursorWantsCaps()) Shift.ON else Shift.OFF
        }
        renderShift()
    }

    private fun cursorWantsCaps(): Boolean {
        val ic = currentInputConnection ?: return false
        var type = currentInputEditorInfo?.inputType ?: return false
        val capFlags = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        // Many apps forget the cap flag on plain text fields; default to sentence case.
        if (type and capFlags == 0) type = type or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        return ic.getCursorCapsMode(type) != 0
    }

    private fun renderShift() {
        keyboardView?.shifted = shift != Shift.OFF
        keyboardView?.capsLocked = shift == Shift.LOCKED
    }

    private fun setMode(m: Mode) {
        mode = m
        keyboardView?.keyboardLayout = layoutFor(m)
        if (m == Mode.LETTERS) updateShiftFromEditor() else renderShift()
    }

    private fun showEmoji(show: Boolean) {
        val panel = emojiPanel ?: return
        if (show) {
            panel.bind(KeyboardTheme.resolve(this, prefs.theme), prefs.recentEmoji)
            // Take exactly the space of strip + keys so the app's layout doesn't jump.
            val h = (keyboardView?.height ?: 0) + (strip?.height ?: 0)
            if (h > 0) panel.layoutParams = panel.layoutParams.apply { height = h }
        }
        panel.visibility = if (show) View.VISIBLE else View.GONE
        keyboardView?.visibility = if (show) View.INVISIBLE else View.VISIBLE
        strip?.visibility = if (show) View.GONE else View.VISIBLE
    }

    // ---- Suggestions ----

    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 16)
    }

    private fun refreshSuggestions() {
        val st = strip ?: return
        if (lastSelStart >= 0 && lastSelStart != lastSelEnd) { st.setItems(emptyList()); return }
        val before = textBefore(64)
        val items = ArrayList<SuggestionStripView.Item>()

        if (!field.password) {
            val token = TextRules.trailingToken(before)
            shortcuts.find(token)?.let {
                items += SuggestionStripView.Item(it.trigger, SuggestionStripView.ItemKind.SHORTCUT, primary = prefs.shortcutAutoExpand, display = applyTypedCase(token, it.expansion))
            }
        }

        val s = Dictionary.suggester
        val word = TextRules.trailingWord(before)
        if (prefs.suggestions && field.suggestions && s != null && word.isNotEmpty()) {
            val fix = if (prefs.autocorrect && word != rejectedWord && items.isEmpty()) s.autocorrect(word) else null
            if (fix != null && fix != word) items += SuggestionStripView.Item(word, SuggestionStripView.ItemKind.TYPED, display = "\"$word\"")
            val words = LinkedHashSet<String>()
            fix?.takeIf { it != word }?.let { words += it }
            s.suggest(word, 3).forEach { words += it.word }
            for (w in words) {
                if (items.size >= 3) break
                items += SuggestionStripView.Item(w, SuggestionStripView.ItemKind.WORD, primary = w == fix)
            }
            // Mid-word with nothing to offer: show the word itself (tap = keep + learn) rather than the toolbar.
            if (items.isEmpty()) items += SuggestionStripView.Item(word, SuggestionStripView.ItemKind.TYPED)
        }
        st.setItems(items)
    }

    // ---- SuggestionStripView.Listener ----

    override fun onSuggestionPicked(item: SuggestionStripView.Item) {
        val ic = currentInputConnection ?: return
        val before = textBefore(64)
        when (item.kind) {
            SuggestionStripView.ItemKind.SHORTCUT -> {
                val token = TextRules.trailingToken(before)
                shortcuts.find(token)?.let { expandShortcut(token, it) }
                return
            }
            SuggestionStripView.ItemKind.TYPED -> {
                // Keep exactly what was typed and teach it to the dictionary so it isn't corrected again.
                if (field.learn) Dictionary.learn(item.text, Suggester.USER_KNOWN_THRESHOLD)
                ic.commitText(" ", 1)
                rejectedWord = null
            }
            SuggestionStripView.ItemKind.WORD -> {
                val word = TextRules.trailingWord(before)
                replaceTrailing(word, item.text + " ")
                learnWord(item.text)
            }
        }
        undo = null
        autoSpace = true
        afterSeparator()
    }

    override fun onSuggestionLongPressed(item: SuggestionStripView.Item) {
        if (item.kind == SuggestionStripView.ItemKind.SHORTCUT) return
        Dictionary.forget(item.text)
        Toast.makeText(this, "Removed \"${item.text}\" from learned words", Toast.LENGTH_SHORT).show()
        scheduleRefresh()
    }

    override fun onToolbarAction(action: SuggestionStripView.ToolbarAction) {
        when (action) {
            SuggestionStripView.ToolbarAction.EMOJI -> showEmoji(true)
            SuggestionStripView.ToolbarAction.PASTE -> clipboardText()?.let {
                currentInputConnection?.commitText(it, 1)
                markEdit()
            }
            SuggestionStripView.ToolbarAction.SETTINGS -> openSettings()
            SuggestionStripView.ToolbarAction.SWITCH_KEYBOARD -> imm().showInputMethodPicker()
            SuggestionStripView.ToolbarAction.HIDE -> requestHideSelf(0)
        }
    }

    // ---- Helpers ----

    private fun clipboardText(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun feedback(key: Key) {
        if (prefs.haptics) {
            vibrator?.vibrate(VibrationEffect.createOneShot(prefs.hapticMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (prefs.sound) {
            val fx = when (key.code) {
                KeyCode.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                KeyCode.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyCode.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(fx, -1f)
        }
    }

    companion object {
        private const val DOUBLE_SPACE_MS = 450L
        private const val DOUBLE_TAP_MS = 350L
        private const val MAX_RECENT_EMOJI = 32
    }
}
