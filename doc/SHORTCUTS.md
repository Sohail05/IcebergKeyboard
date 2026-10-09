# Text Shortcuts — Behaviour and File Formats

## Behaviour

A **shortcut** is a `trigger` → `expansion` pair.

- **Trigger:** 1–64 characters, no whitespace. Matched case-insensitively against the run of non-space characters right before the cursor, so `;addr` and `brb` both work.
- **Expansion:** 1–10,000 characters. May contain newlines and placeholders.
- **Uniqueness:** a trigger is unique regardless of case (`BRB` and `brb` are the same shortcut).

When the text before the cursor matches a trigger:

1. The suggestion strip shows `✦ <expansion>` in the first slot. Tapping it expands.
2. If **Expand on space** is on, pressing space expands and adds a trailing space (unless the expansion already ends in whitespace or uses `{cursor}`).
3. **Backspace right after** an expansion restores the trigger. The same trigger won't auto-expand again at that spot, so the next space types a plain space.

### Case

| Typed | Expansion stored as `be right back` |
|---|---|
| `brb` | `be right back` |
| `Brb` | `Be right back` |
| `BRB` | `BE RIGHT BACK` |

### Placeholders

| Placeholder | Result |
|---|---|
| `{date}` | `2026-10-09` (ISO 8601) |
| `{time}` | `14:05` (24-hour) |
| `{datetime}` | `2026-10-09 14:05` |
| `{clipboard}` | Current clipboard text (empty if none) |
| `{cursor}` | Where the caret is placed after expanding (first one wins) |
| `{{` | A literal `{` |

Unknown placeholders such as `{name}` are left as-is.

## Export format (canonical)

UTF-8 JSON, pretty-printed, sorted by trigger. Default file name: `iceberg-shortcuts-YYYY-MM-DD.json`.

```json
{
    "format": "iceberg-keyboard/shortcuts",
    "version": 1,
    "exportedAt": "2026-10-09T14:53:08.912-04:00",
    "shortcuts": [
        { "trigger": ";addr", "expansion": "1 Main St\nSpringfield" },
        { "trigger": "brb", "expansion": "be right back" }
    ]
}
```

- `version` is bumped only for incompatible changes. Importers reject files whose `version` is greater than they support, with a clear error.
- Unknown top-level or per-entry fields are ignored, so newer exports stay readable as long as `version` is unchanged.
- The app's own storage (`files/shortcuts.json`) uses this same format, so an exported file is a full backup.

## Import formats

The format is detected automatically. Every import shows a preview before anything changes.

| Format | Detected when | Layout |
|---|---|---|
| **Iceberg JSON** | Top-level object with a `shortcuts` array | As above |
| **JSON array** | Top-level `[...]` | `[{"trigger": "...", "expansion": "..."}]`. Accepted key aliases: trigger = `shortcut`, `key`, `abbreviation`; expansion = `phrase`, `text`, `value`, `word` |
| **JSON map** | Top-level object without `shortcuts` | `{"brb": "be right back", "omw": "on my way"}` |
| **Gboard dictionary** | First line contains `Gboard Dictionary` | Tab-separated `word<TAB>shortcut<TAB>locale`. Rows without a shortcut (plain dictionary words) are ignored. **Note:** column order comes from community write-ups, not an official spec. Verify against a real export (unzip `PersonalDictionary.zip`) |
| **TSV** | Any line contains a tab | `trigger<TAB>expansion`. `\n`, `\t` and `\\` escapes are decoded |
| **CSV** | Otherwise | `trigger,expansion`. RFC 4180 quoting (`"a, b"`, `""` for a quote) within a single line. `\n` escapes are decoded |

For TSV and CSV, an optional header row (`trigger,expansion` or `shortcut,...`) and `#` comment lines are skipped. A UTF-8 BOM is ignored. Files over 2 MB are refused.

### Validation and conflicts

- Invalid rows (empty trigger or expansion, whitespace in the trigger, too long) are **skipped and listed** in the preview. They never abort the import.
- Duplicate triggers *inside* the file: the last one wins.
- Conflicts with existing shortcuts are resolved by the mode you pick:

| Mode | Existing `brb` = "be right back", imported `brb` = "back soon" |
|---|---|
| Merge: imported version wins | `brb` = "back soon" |
| Merge: keep my existing version | `brb` = "be right back" |
| Replace all my shortcuts | Only imported shortcuts remain |

After importing, a message reports *added / updated / unchanged* counts.

### Moving shortcuts from Samsung Keyboard

Samsung Keyboard has no export option for text shortcuts. To move them, either:
1. Recreate them in a spreadsheet as `trigger,expansion`, save as CSV, and import; or
2. Export Gboard's personal dictionary (Gboard → Dictionary → Personal dictionary → ⋮ → Export), unzip it, and import the `.txt` file.
