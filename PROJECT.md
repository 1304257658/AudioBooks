# AudioBooks Project Notes

This document is the durable project log for architecture decisions, implemented features, known traps, and current gaps. Keep it updated when behavior changes.

## Product Direction

- Offline-first Android audiobook reader.
- User books, reading progress, settings, downloads, and model files stay on device.
- Public model download is allowed. User content upload is not allowed.
- Do not bundle large model files into Git or the APK.
- Do not fake TTS quality with system fallback voices. Playback must use the intended local model runtime once it is integrated.

## Current Architecture

- UI: single `MainActivity.kt` using Jetpack Compose.
- State: `AudioBookViewModel` owns books, model state, reader settings, search state, and playback commands.
- Storage:
  - `LocalProjectPaths` stores app data under app-specific external storage:
    `Android/data/com.example.audiobooks/files/audiobooks-data/`
  - Books are under `books/`.
  - Downloaded model packages are under `models/<model-name>/`.
  - App settings are stored in JSON for now.
- Parsers:
  - EPUB: parses OPF spine, extracts readable text, writes local `reader/reader.html`, and extracts EPUB assets for WebView reading.
  - TXT: UTF-8 plain text parsing.
  - PDF: text PDF parsing through PDFBox Android, currently page-based chapters.
- TTS:
  - Target models are CosyVoice2 ONNX, Fun-CosyVoice3 ONNX, and Fish Speech 1.5.
  - Model packages can be downloaded from ModelScope by file whitelist.
  - Runtime integration for those target models is not complete yet.
  - System Android TTS fallback is intentionally not used.
  - Sherpa/native playback runtime must be created lazily on first playback, not during app startup. Eager native initialization can crash the app before the first screen renders.

## Implemented Features

- Import EPUB, TXT, and text PDF files.
- Store imported books and parsed metadata locally.
- Recently listened books sort first.
- Save and restore sentence-level progress.
- Reader page with text display, start/stop, previous/next, chapters, and reader tools.
- Chapter navigation screen.
- Reader tools panel:
  - search and jump
  - bookmarks
  - sleep timer
  - repeat and A-B markers
  - speed
  - font scale
  - line spacing
  - background mode
  - language selector
  - reset reader settings
- Model package management:
  - select model
  - manual download by user click
  - per-model download progress
  - per-model delete
  - resource size labels
- APK output includes version in filename.

## Known Traps

- Downloaded model files are not the same as a working runtime. CosyVoice/Fish need ONNX Runtime Mobile plus tokenizer, acoustic/vocoder wiring, and post-processing.
- Do not reintroduce Android system TTS as a quality fallback. It hides runtime gaps and makes test results misleading.
- Do not initialize TTS native engines in `AudioBookViewModel` construction. Keep startup independent from model runtime availability.
- ModelScope repositories may contain multiple variants. Download only files listed in `TtsModelSpec.downloadFiles`; do not download the whole repository.
- Previously imported books keep old parsed metadata. Parser fixes usually require deleting and re-importing the book.
- Android file managers often restrict `Android/data`. Use USB or a capable file manager to inspect app-specific external files.
- EPUB WebView reading preserves more layout but is not yet synchronized to sentence index.

## Current Gaps

- Real CosyVoice2/Fun-CosyVoice3/Fish runtime integration is pending.
- Reader settings are partially localized; not every small UI string is in the language map yet.
- EPUB chapter extraction is heuristic from headings/title and may need NCX/nav support.
- PDF chapter navigation is page-based, not document-outline based.
- Search jump is sentence-based in text reader; WebView EPUB mode does not yet scroll to matching HTML location.

## Verification Checklist

- Run `.\scripts\build-debug.ps1`.
- Confirm generated APK name contains version, for example:
  `AudioBooks-debug-v0.2.3-4.apk`.
- Run `git diff --check`.
- Scan for personal path leakage before final response.
- Re-import affected books after parser changes before testing parser fixes.
