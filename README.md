# Iceberg Keyboard

A fast, private Android keyboard built for the **Samsung Galaxy S9+** (Android 8–10), with **text shortcuts you can import and export**.

- Full QWERTY + symbols + numpad, long-press accents/symbols, emoji, clipboard paste
- Suggestions, conservative autocorrect with **backspace-to-undo**, learned words, contractions
- Text shortcuts with `{date}`, `{clipboard}`, `{cursor}` placeholders: import/export as JSON, CSV, TSV or a Gboard dictionary
- Multi-touch rollover, space-bar cursor drag, accelerating backspace, double-space period, smart punctuation
- Light / Dark / **AMOLED black** themes, adjustable key height, haptics
- **No internet permission.** Nothing you type leaves the phone

## Quick start

```bash
make setup     # JDK 17 + Android SDK (macOS / Homebrew)
make check     # unit tests + lint + debug APK
make install   # install on a USB-connected phone (USB debugging on) and select the keyboard
make release   # signed APK in dist/ for sideloading (run `make keystore` once first)
```

Installing on the phone without a cable, signing, and troubleshooting: see [doc/BUILD_AND_DEPLOY.md](doc/BUILD_AND_DEPLOY.md).

## Layout

```
app/   Android app (Kotlin): IME service, Canvas keyboard, settings, shortcut manager
doc/   Spec, shortcut file formats, improvement roadmap, architecture, build & test guides
scripts/  Toolchain setup and keystore creation
```

| Doc | |
|---|---|
| [SPEC.md](doc/SPEC.md) | Features and target-device constraints |
| [SHORTCUTS.md](doc/SHORTCUTS.md) | Shortcut behaviour and import/export formats |
| [IMPROVEMENTS.md](doc/IMPROVEMENTS.md) | Typing performance and UX roadmap |
| [ARCHITECTURE.md](doc/ARCHITECTURE.md) | How the code fits together |
| [TESTING.md](doc/TESTING.md) | Automated tests and the on-device checklist |

## Feedback

Found a bug or want a feature? [Open an issue](../../issues/new/choose). There are templates for bug reports and feature requests.

## License

Code: [MIT](LICENSE). The bundled English word list has its own terms; see [NOTICE.md](NOTICE.md).
