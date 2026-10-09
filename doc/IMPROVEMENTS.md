# Typing Performance and UX Improvements

Suggestions for making Iceberg faster and nicer to type on than the stock keyboard, grouped by theme. Items marked ✅ already ship in v0.1. The rest are ranked by **impact ÷ effort** (P1 = do next).

## A. Latency and input reliability

| # | Improvement | Why it matters | Status |
|---|---|---|---|
| A1 | **Canvas-drawn keys, no View per key** | One `onDraw` with no per-frame allocations. Avoids measure/layout passes for ~35 views on every shift toggle | ✅ |
| A2 | **Multi-touch rollover** | Fast two-thumb typists press the next key before releasing the last. Committing the earlier key on the new finger-down prevents dropped or swapped letters, the #1 complaint about older keyboards | ✅ |
| A3 | **Suggestions off the hot path** | Keys commit immediately. Suggestion lookup is debounced (16 ms) and coalesced, so a burst of keys does one lookup, not ten | ✅ |
| A4 | **Backspace acts on touch-down** | Feels 50–100 ms faster than acting on release | ✅ |
| A5 | **Async dictionary load** | Keyboard is usable before the word list loads; suggestions appear when it is ready (<100 ms) | ✅ |
| A6 | **No IPC-heavy shift checks per letter** | `getCursorCapsMode` only after separators, deletes and cursor jumps, not after every letter | ✅ |
| A7 | Touch-down haptic via `VibrationEffect` with an adjustable length | The S9+ motor feels mushy at the defaults; 8–15 ms clicks feel crisp | ✅ |
| A8 | **Baseline profile** (`androidx.profileinstaller` + generated profile) | Precompiles the IME's hot paths, so the first keystrokes after an install or update don't stutter while ART is still compiling the code | P1 |
| A9 | Hardware-layer cache of the static key layer, redrawing only pressed keys | Lower GPU work at 60 Hz; matters most on the 1440p panel | P2 |
| A10 | Measure keystroke latency in CI with Macrobenchmark (`FrameTimingMetric`) | Stops performance regressions from creeping in | P2 |
| A11 | Move suggestion computation to a background `HandlerThread` with cancellation | Keeps the UI thread clear once next-word prediction makes lookups heavier | P2 |

## B. Accuracy (fewer typos, fewer bad corrections)

| # | Improvement | Why it matters | Status |
|---|---|---|---|
| B1 | **Keyboard-proximity weighting** in corrections | `hrllo` → hello beats `hrllo` → hullo because `r` is next to `e` | ✅ |
| B2 | **Conservative autocorrect + backspace-to-undo** | Bad corrections are what make people turn autocorrect off. Undo makes them cheap | ✅ |
| B3 | **Learned words never get corrected** | Names, slang and jargon stop being "fixed" after you type them twice | ✅ |
| B4 | Leave emails, URLs and #hashtags alone | Only standalone words are corrected | ✅ |
| B5 | **Touch-model key hit boxes**: shift each key's effective centre toward where *you* actually tap (learned per key) | Thumbs land consistently low and toward the palm. Adapting hit boxes to your own taps cuts errors where you hit the neighbouring key | P1 |
| B6 | **Bigram next-word prediction** + using the previous word in correction ranking | "I'll see you **tomorrow**". Also picks `there` vs `their` from context | P1 |
| B7 | Larger, licensed dictionary (e.g. SCOWL / Wiktionary frequency lists, ~50k words) | The bundled 10k list misses many names and technical terms. Its licence also only allows personal use | P1 before any public release |
| B8 | Edit distance 2 for longer words (≥7 letters) | Catches `accomodation` → accommodation | P2 |
| B9 | Swap the first-character case on Shift+suggestion | Proper nouns without retyping | P3 |

## C. Ergonomics on a tall 6.2" screen

| # | Improvement | Why it matters | Status |
|---|---|---|---|
| C1 | **Adjustable key height** (40–72 dp) | Taller keys mean fewer misses; shorter keys show more of the app | ✅ |
| C2 | **Space-bar cursor drag** | Precise caret placement without the fiddly text handles | ✅ |
| C3 | **Accelerating backspace** (chars → words) | Clears a sentence in about a second | ✅ |
| C4 | **One-handed mode**: shrink to 80% and dock left or right | The S9+ is 158 mm tall. One-handed reach to `q` and `p` is a stretch | P1 |
| C5 | Split / thumb layout for landscape | Two-thumb typing in landscape with the keyboard spanning 160 mm is slow | P2 |
| C6 | Edge padding tuned for the curved display | Avoids accidental palm touches on `q`, `a`, `p`, `l` at the curved edges | P2 |
| C7 | Swipe left on backspace to delete the last word; swipe up on keys for the hint character | Faster than long-press, which forces a 300 ms wait | P2 |
| C8 | Floating keyboard mode | For the occasional landscape video plus chat | P3 |

## D. Flow and "fewer taps"

| # | Improvement | Why it matters | Status |
|---|---|---|---|
| D1 | **Smart punctuation spacing** (`word␣` + `.` → `word.␣`) | Saves a backspace after every suggestion pick that ends a sentence | ✅ |
| D2 | **Double-space period** with a 450 ms window | | ✅ |
| D3 | **Field-aware layouts** (email `@`, URL `/` and `.com`, numpad for numbers, number row in passwords) | Saves a mode switch in the most common forms | ✅ |
| D4 | **Shortcut chip in the suggestion strip** | See and confirm the expansion before committing | ✅ |
| D5 | **Shortcut placeholders** (`{date}`, `{clipboard}`, `{cursor}`) | Turns shortcuts into templates (email sign-offs, form replies) | ✅ |
| D6 | **Auto-return to letters** after typing a symbol followed by space | Saves a tap after `5 ` or `@ ` | P1 |
| D7 | **Clipboard history panel** (last 20 clips, pin favourites, auto-expire after 1 h) | Samsung Keyboard has it. Users will miss it | P1 |
| D8 | Shift + chorded letter (hold shift, tap letter, release) | Power-user caps without toggling | P2 |
| D9 | Emoji search by keyword (`:fire`) | Scrolling through 300+ emoji is slow | P2 |
| D10 | Glide (swipe) typing | Large effort, but a major speed boost for one-handed use | P3 |
| D11 | Voice input key launching the system recogniser | | P3 |

## E. Shortcuts-specific

| # | Improvement | Status |
|---|---|---|
| E1 | Import/export (JSON, CSV, TSV, Gboard) with preview, skipped-row report and merge modes | ✅ |
| E2 | Share shortcuts as text (paste into a note or message) | ✅ |
| E3 | Atomic save + corrupt-file quarantine | ✅ |
| E4 | **Scheduled auto-backup** to a user-chosen folder (SAF persisted URI) every N days | P1 |
| E5 | Shortcut **groups / tags** and per-group export | P2 |
| E6 | Usage counts + "unused for 90 days" cleanup | P2 |
| E7 | Mid-word triggers (e.g. `:)` inside text) and trigger-on-punctuation | P2 |
| E8 | Rich placeholders: `{date:dd/MM}` custom formats, `{input:Name}` prompt | P3 |
| E9 | Multi-device sync through an exported file in a synced folder (no cloud backend) | P3 |

## F. Accessibility and polish

| # | Improvement | Status |
|---|---|---|
| F1 | TalkBack: virtual view hierarchy (`ExploreByTouchHelper`) so each key is announced | P1, required for a public release |
| F2 | High-contrast theme and bold labels option | P2 |
| F3 | Key-press preview bubble (optional) | P2 |
| F4 | Localised UI strings and more layouts (QWERTZ, AZERTY) with per-language dictionaries | P2 |
| F5 | Material You dynamic colour (Android 12+, for the next phone) | P3 |

## Suggested next milestone (v0.2)

A8 baseline profile · B5 touch model · B6 bigrams · C4 one-handed · D6 auto-return · D7 clipboard history · E4 auto-backup · F1 TalkBack.
