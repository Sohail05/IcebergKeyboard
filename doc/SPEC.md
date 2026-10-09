# Iceberg Keyboard — Product Spec

**Status:** v0.1.0 (sideload only, no store listing)
**Target device:** Samsung Galaxy S9+ (SM-G965x)

## 1. Goals

1. A dependable everyday keyboard with the features people expect from Samsung Keyboard or Gboard.
2. First-class **text shortcuts** (text expansion) that can be **imported and exported** so they survive phone changes and can be shared.
3. Fast typing on a 2018 flagship: no dropped keystrokes and no visible lag.
4. Private by default: no network permission, no analytics, and learned words stay on the device.

### Non-goals (for now)

- Play Store / Galaxy Store publishing
- Cloud sync, accounts, or telemetry
- Languages other than English (US) (see [IMPROVEMENTS.md](IMPROVEMENTS.md) for the multi-language plan)

## 2. Target device constraints

| Property | Galaxy S9+ | Design consequence |
|---|---|---|
| Android version | Shipped 8.0, final update Android 10 (One UI 2.x) | `minSdk 26`, `targetSdk 34`; tested on an API 29 emulator |
| Display | 6.2" 1440×2960 (default render FHD+ 1080×2220), Super AMOLED | Keys sized in dp; **AMOLED black theme** saves power |
| Aspect ratio | 18.5:9, curved edges | Small edge padding so edge keys stay hittable on the curved glass |
| SoC / RAM | Exynos 9810 / Snapdragon 845, 6 GB | Canvas-drawn keyboard, ~10k-word in-memory dictionary (<2 MB heap) |
| Emoji font | Emoji 12 (Android 10) | Emoji the font can't draw are filtered out at runtime (no "tofu" boxes) |
| Haptics | Linear-ish vibration motor | Tunable vibration length (3–40 ms) |

## 3. Feature list

Legend: ✅ implemented in v0.1 · 🔜 planned (see IMPROVEMENTS.md)

### 3.1 Core keyboard

| Feature | Status | Notes |
|---|---|---|
| QWERTY letters layout | ✅ | Half-key offset on the home row, like hardware keyboards |
| Symbols pages (`?123`, `=\<`) | ✅ | Two pages: the common symbols, then the rarer ones |
| Optional number row | ✅ | Setting; **forced on in password fields** |
| Numeric / phone keypad | ✅ | Picked automatically for number, phone and date fields |
| Shift, caps lock | ✅ | Tap = one capital; double-tap or long-press = caps lock |
| Auto-capitalization | ✅ | Sentence case, even when the app forgets the flag |
| Backspace with auto-repeat | ✅ | Deletes on press; repeats after 380 ms; after ~1 s deletes **whole words** |
| Grapheme-aware delete | ✅ | 👍🏽 or 👨‍👩‍👧 are removed with one press |
| Enter / IME action key | ✅ | Shows Go / Search / Send / Next / Done; long-press always inserts a newline |
| Long-press alternates | ✅ | Digits on the top row, symbols on the middle rows, accents. Slide to choose |
| Key hints | ✅ | Small label showing the long-press character (setting) |
| Email / URL field variants | ✅ | `@` or `/` key plus a `.com` key; no autocorrect or auto-caps |
| Emoji panel | ✅ | Categories, recents, filtered to what the device font supports |
| Clipboard paste button | ✅ | Toolbar |
| Switch keyboard | ✅ | Toolbar 🌐 or long-press space |
| Hide keyboard | ✅ | Toolbar ⌄ |
| Themes | ✅ | Follow system / Light / Dark / AMOLED black |
| Adjustable key height | ✅ | 40–72 dp |
| Haptic + sound feedback | ✅ | Each can be toggled; vibration length adjustable |
| Landscape | ✅ | Fullscreen "extract" mode is disabled, so the app stays visible |

### 3.2 Typing intelligence

| Feature | Status | Notes |
|---|---|---|
| Suggestion strip (3 slots) | ✅ | Completions and corrections, keeping the capitalization you typed |
| Autocorrect on space or punctuation | ✅ | Conservative: only unknown, letters-only words with a strong single-edit match |
| **Backspace undoes autocorrect** | ✅ | Restores what you typed; that word isn't corrected again in this spot |
| Keep-as-typed | ✅ | Tap the `"quoted"` word in the strip to keep it and teach it to the dictionary |
| Contractions | ✅ | dont → don't, im → I'm, i → I |
| Learn new words | ✅ | A word typed twice becomes "known". Never learned in password or incognito fields |
| Forget a learned word | ✅ | Long-press it in the suggestion strip |
| Double-space → period | ✅ | Within 450 ms, only after a word character |
| Smart punctuation | ✅ | After a suggestion pick, `word␣` + `.` becomes `word.␣` |
| Space-bar cursor control | ✅ | Drag along the space bar; about 12 dp per character |
| Next-word prediction | 🔜 | Needs a bigram model |
| Glide / swipe typing | 🔜 | |

### 3.3 Text shortcuts (text expansion)

| Feature | Status | Notes |
|---|---|---|
| Add / edit / delete / search | ✅ | Settings → Manage shortcuts |
| Case-insensitive triggers | ✅ | Triggers may contain punctuation (`;addr`) but no spaces |
| Case follows what you typed | ✅ | `brb` → be right back, `Brb` → Be right back, `BRB` → BE RIGHT BACK |
| Suggestion chip | ✅ | Shown as ✦ expansion in the strip whenever the text before the cursor matches a trigger |
| Expand on space | ✅ | Setting; backspace right after undoes it |
| Placeholders | ✅ | `{date}` `{time}` `{datetime}` `{clipboard}` `{cursor}` |
| Multi-line expansions | ✅ | |
| **Export** | ✅ | JSON file via the system file picker, or share as text |
| **Import** | ✅ | Iceberg JSON, plain JSON array or object, Gboard dictionary, TSV, CSV |
| Import preview + merge options | ✅ | Shows counts, duplicates and skipped rows, then: imported wins / keep mine / replace all |
| Default starter shortcuts | ✅ | `omw`, `brb`, `ty`, `;date`, `;time` (first run only) |

The file formats are specified in [SHORTCUTS.md](SHORTCUTS.md).

## 4. Privacy and data

- **No INTERNET permission.** The only permission is `VIBRATE`.
- Learned words: `files/user_words.tsv`. Excluded from cloud backup and kept on the device.
- Shortcuts: `files/shortcuts.json`, written atomically (temp file + rename). If the file is corrupt it is kept as `shortcuts.json.corrupt` rather than discarded.
- Password fields, and fields that set `IME_FLAG_NO_PERSONALIZED_LEARNING` (incognito tabs), get no learning and no suggestions.

## 5. Acceptance criteria (v0.1)

- [x] Installs on Android 8–10 from an APK file
- [x] Can be enabled and selected from the in-app setup screen
- [x] Types correctly in single-line, multi-line, email, URL, password, number and phone fields
- [x] Shortcuts: create → type trigger → expands; export → wipe → import → identical set
- [x] Unit tests pass (`make test`); lint passes (`make lint`)
- [ ] Manual checklist in [TESTING.md](TESTING.md) run on a physical S9+
