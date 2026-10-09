# Testing

## Automated

```bash
make test    # JVM unit tests
make lint    # Android lint
make check   # both + debug build (CI)
```

| Suite | Covers |
|---|---|
| `ShortcutCodecTest` | Export/import round-trip (unicode, newlines), JSON array/map aliases, Gboard, TSV escapes, CSV quoting, BOM, invalid rows, future-version rejection, merge modes, case-following |
| `SuggesterTest` | Prefix completion ranking, case preservation, transposition and proximity corrections, contractions, learned/forgotten words, user-word persistence |
| `TextRulesTest` | Word/token extraction, double-space rules, word-delete length, placeholder rendering |

The keyboard logic that needs an `InputConnection` is covered by the manual checklist below and by the emulator smoke test.

## Emulator smoke test (Android 10, S9+ resolution)

```bash
make emulator && make install
adb shell am start -n com.iceberg.keyboard.debug/com.iceberg.keyboard.ui.SettingsActivity
```

Tap the "Type here" field, then check:

| Type | Expected field text |
|---|---|
| `teh␣` | `The ` |
| `brb␣` | `…be right back ` |
| `⌫` immediately | `…brb` |
| `im sure i dont know.␣` | `I'm sure I don't know. ` |
| `here␣␣` | `here. ` |

## Manual checklist (run on the physical S9+ before handing out a build)

**Setup**
- [ ] Fresh install → app shows Step 1 → enable → Step 2 → switch → "active"
- [ ] Update install (same key) keeps shortcuts, learned words and settings

**Fields**
- [ ] Messages / WhatsApp: Enter key shows Send when the app requests it; long-press Enter inserts a newline
- [ ] Chrome address bar: `/` + `.com` keys; Go action; no autocorrect
- [ ] Email field (e.g. Samsung Email login): `@` key, no auto-caps
- [ ] Password field: number row shown, no suggestions, nothing learned
- [ ] Phone dialer/contacts number: numpad with `* #`
- [ ] Chrome incognito: suggestions work, no new words learned

**Typing**
- [ ] Fast two-thumb typing of "the quick brown fox jumps over the lazy dog" with no dropped letters
- [ ] Long-press `e` → slide to `é` → release types `é`; with shift on, types `É`
- [ ] Double-tap shift → caps lock icon ⇪; tap again → off
- [ ] Hold backspace: chars, then whole words after about 1 s
- [ ] Drag on the space bar moves the cursor both ways and stops at the text ends
- [ ] Emoji: insert 👍🏽 then one backspace removes it entirely
- [ ] Rotate to landscape: no fullscreen editor, keyboard usable

**Shortcuts**
- [ ] Add `sig` → `Best,\nSam` → type `sig␣` → two-line expansion
- [ ] `;date␣` → today's date
- [ ] Expansion with `{cursor}` puts the caret at the marker
- [ ] Export → file appears in Downloads → Delete all → Import that file → identical list
- [ ] Import a CSV with one bad row → preview lists the skipped line

**Feedback/appearance**
- [ ] Vibration length slider changes the feel immediately (on next keyboard open)
- [ ] AMOLED theme is true black
- [ ] Key height 40 and 72 both lay out correctly
