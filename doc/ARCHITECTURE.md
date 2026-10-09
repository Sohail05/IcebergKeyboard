# Architecture

```
app/src/main/java/com/iceberg/keyboard/
├── core/        Pure Kotlin, no Android imports → unit-tested on the JVM
│   ├── Shortcut.kt            model, validation, case-following
│   ├── ShortcutCodec.kt       export/import (JSON, Gboard, TSV, CSV) + merge
│   ├── ExpansionTemplate.kt   {date} {time} {clipboard} {cursor} placeholders
│   ├── Suggester.kt           completions, corrections, autocorrect, learned words
│   └── TextRules.kt           word/token extraction, double-space, word delete
├── data/        Android persistence
│   ├── ShortcutStore.kt       process-wide repository, atomic JSON file, listeners
│   ├── Dictionary.kt          async loading of assets/words_en.txt + user words
│   └── KeyboardPrefs.kt       typed SharedPreferences
├── ime/         The input method
│   ├── IcebergImeService.kt   InputMethodService: editor I/O and all typing logic
│   ├── KeyboardView.kt        Canvas keyboard: touch, rollover, popups, repeat, cursor drag
│   ├── KeyboardLayouts.kt     Key model + letters/symbols/numpad layouts + alternates
│   ├── SuggestionStripView.kt suggestions or toolbar
│   ├── EmojiPanel.kt          emoji grid with categories and recents
│   └── KeyboardTheme.kt       light / dark / AMOLED colours
└── ui/          Activities
    ├── SettingsActivity.kt    setup wizard, try-it field, preferences
    └── ShortcutsActivity.kt   list/search/edit + import/export via the system file picker
```

## Data flow for one keystroke

```
finger down ─► KeyboardView.pointerDown ─► Listener.onKeyDown  (haptic/sound; backspace deletes here)
   [another finger down] ─► rollOverActivePointers ─► onKeyCommit(previous key)
finger up ───► KeyboardView.pointerUp ───► Listener.onKeyCommit(key, alternate?)
                                              │
IcebergImeService.handleText / handleSpace / handlePunctuation / handleEnter
   ├─ InputConnection.commitText (immediate)
   ├─ separator? → shortcut expand ▸ autocorrect ▸ double-space period ▸ learn word
   ├─ updateShiftFromEditor (separators/deletes only)
   └─ scheduleRefresh() ── 16 ms debounce ──► refreshSuggestions() ─► SuggestionStripView
```

### Design decisions

- **No composing text.** Words are committed directly and replaced with `deleteSurroundingText` + `commitText` inside a batch edit. Some apps (Samsung Notes, older WebViews, terminals) handle composing spans badly; reading back `getTextBeforeCursor` is simpler and survives cursor jumps.
- **Undo record.** After an autocorrect or expansion, the service stores `(original, inserted)`. Backspace only reverts if the text before the cursor still ends with `inserted`, so it can't corrupt text after the user has moved.
- **Same-process sharing.** The IME and the settings screens run in one process. `ShortcutStore` is a singleton with listeners, so edits show up in the keyboard instantly without a ContentProvider.
- **Layouts as code.** Keys are Kotlin objects, not the deprecated `Keyboard` XML, which gives full control over weights, alternates and field variants.
- **Strict `core/` boundary.** Anything testable without a device lives in `core/` with no `android.*` imports.

## Storage

| File (app-private `filesDir`) | Content | Backup |
|---|---|---|
| `shortcuts.json` | Same format as export | Included in Android backup |
| `user_words.tsv` | `word<TAB>count` | Excluded from cloud backup (privacy) |
| `shared_prefs/*_preferences.xml` | Settings, recent emoji | Included |
