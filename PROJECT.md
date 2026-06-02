# AudioBooks Project Notes

This document is the durable project log for architecture decisions, implemented features, known traps, and current gaps. Keep it updated when behavior changes.

## Product Direction

- Offline-first Android audiobook reader. Goal: the best-in-class offline listening experience.
- User books, reading progress, settings, downloads, and model files stay on device.
- Public model download is allowed. User content upload is not allowed.
- Do not bundle large model files into Git or the APK.
- **Model / runtime decoupling is a core principle.** AI TTS models iterate fast and the underlying
  inference model will be swapped over time, so the reader/playback layer must never depend on a
  specific model. Models plug in behind a `TtsEngine` SPI; adding or replacing a model is one new
  engine + one spec entry, with no changes to playback, UI, or storage.
- Do not fake TTS quality with a system fallback voice. If a selected model's engine is not yet
  implemented, playback stays disabled with an honest message — never silently substitute a
  lower-quality voice. This is about not misleading users, NOT about withholding a working feature:
  a real working engine ships as the default voice.

## Current Architecture

- UI: Jetpack Compose. `MainActivity.kt` is a thin navigation host (bottom tab bar + mini-player
  dock + full-screen overlays + bottom sheets). The visual layer was ported from the Claude Design
  prototype (the `ui/*.jsx` + `styles.css` + `念 Aloud · 离线听书.html` reference files) and lives in
  `ui/Design*.kt`:
  - `DesignTheme.kt` — warm-paper light/dark tokens (oklch→sRGB), accent, typography, shapes,
    `AudioBooksTheme`, plus in-memory dark/serif appearance overrides.
  - `DesignIcons.kt` — line/solid icons rebuilt as `ImageVector`s via `PathParser` from the
    prototype's SVG path data; `AppIcon` tints at draw time.
  - `DesignComponents.kt` — shared primitives (procedural `BookCover` with hue from title, animated
    `Waveform`, `AppBottomSheet`, `Chip`, `AppSwitch`, `Segmented`, `OnDeviceBadge`, buttons) and
    AudioBook→design mapping helpers (`progressFraction`, `chapterOf`, `isStarted`, `isFinished`).
  - `DesignScreens.kt` — Library (hero + filter chips + cover grid), Search (client-side filter),
    Settings, and the real model-management list (`ModelListContent`).
  - `DesignPlayer.kt` — Player / Chapters / Import overlays + `OverlayHeader`.
  - `DesignReader.kt` — Reader (sentence paragraphs, active highlight, tap-to-jump, font cycle,
    listen/read toggle), In-book search, the full reader-tools sheet, and the EPUB `HtmlReader`.
  - `DesignSheets.kt` — Speed / Sleep / Model / Book bottom sheets.
  - The original prototype files under `ui/` (`*.jsx`, `styles.css`, the `.html`) are kept only as
    design reference; they are NOT part of the Android build (only `*.kt` is compiled).
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
- TTS (pluggable engine architecture):
  - `tts/engine/TtsEngine.kt` — the SPI. `TtsEngine` = synthesize(text)->PCM + sampleRate;
    `TtsEngineFactory` = readiness (`isImplemented`, `requiredFiles`) + `create()`.
  - `tts/engine/TtsEngineRegistry.kt` — maps `engineId` -> factory. The single seam between models
    and runtimes. Adding a model family = register a factory here; nothing else changes.
  - `tts/EngineTtsPlayer.kt` — engine-agnostic playback (prefetch queue + AudioTrack streaming +
    inter-sentence pauses + events). It resolves the engine for the selected model via the registry
    and never references a concrete model.
  - `TtsModelSpec` is pure metadata: it references an `engineId` and passes runtime-specific paths
    through `engineParams` (e.g. modelName/lexicon/dictDir/ruleFsts for sherpa-vits). No model logic.
  - Model availability across the app is DERIVED, not hard-coded: a model is "installed/playable"
    iff (engine registered AND `isImplemented`) AND all `requiredFiles` are present on disk.
  - Engines:
    - `sherpa-vits` (SherpaVitsEngine) — IMPLEMENTED. End-to-end VITS via sherpa-onnx OfflineTts.
      Default model = MeloTTS zh_en, the first real offline voice. Good fit for narration (a single
      stable voice; no voice-cloning overhead).
    - `cosyvoice2`, `cosyvoice3`, `fish-speech` — engine slots present but `isImplemented = false`.
      Selectable + downloadable; playback disabled until each pipeline ships. See CosyVoice2Engine.kt
      for the required multi-stage pipeline (LLM token decode -> flow -> hift + speech tokenizer +
      speaker embedding) — these are NOT runnable through sherpa-onnx OfflineTts.
  - Download is also decoupled via `DownloadStrategy`: `MODELSCOPE_FILES` (file whitelist) or
    `ARCHIVE_TAR_BZ2` (single .tar.bz2 from e.g. a sherpa-onnx GitHub release, reusing `extractTarBz2`).
  - The engine must be created lazily on first playback, not during app startup. Eager native
    initialization can crash the app before the first screen renders.

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
  - resumable downloads for large model files
  - per-model download progress
  - per-model delete
  - resource size labels
- APK output includes version in filename.

## Recent Changes (UI redesign — Claude Design "念 Aloud")

- Replaced the Material-default Compose UI with the warm-paper "念 Aloud" design, ported from the
  Claude Design prototype into `ui/Design*.kt` (see Current Architecture). Navigation is now a bottom
  tab bar (书架 / 搜索 / 设置) + a persistent mini-player dock + full-screen overlays (player, reader,
  in-book search, chapters, import) + bottom sheets (speed, sleep, model/voice, book detail).
- No backend/functionality was removed. The new UI is wired to the existing `AudioBookViewModel`
  callbacks 1:1. Conflicts with the prototype were resolved (per product decisions) as follows:
  - Voice picker maps to the REAL model registry: only implemented + downloaded models are playable;
    others are selectable/downloadable and show "引擎开发中". (Future voice-cloning models can add
    multiple timbres behind the same selector.)
  - The prototype's per-chapter "端侧实时生成" progress is driven by real playback state only (no fake
    animation). The waveform animates while `isPlaying`. Per-chapter pre-generation is a documented
    future enhancement, not yet implemented.
  - The reader keeps EVERY original reader-tool (bookmarks, A-B repeat, repeat count, line spacing,
    background, gestures, speed, sleep, reset, and a text/original-EPUB layout toggle) inside a
    "阅读工具" bottom sheet so nothing was lost relative to the old reader.
  - Import uses the real SAF document picker; a "粘贴文本生成" entry was added (new
    `BookRepository.importPastedText` + `AudioBookViewModel.importText`, saved as a TXT book).
- Procedural book covers derive a stable hue from the title (real books carry no cover art).
- Dark mode + serif/sans body font are in-memory appearance toggles in Settings (consistent with the
  existing non-persisted `ReaderSettings`). UI language (zh/ja/en) selector retained in Settings.
- The original prototype files under `ui/` (`*.jsx`, `styles.css`, `念 Aloud · 离线听书.html`) are kept
  as design reference only and are not compiled.

## Recent Changes (v0.3.0 prep — model/runtime decoupling)

- Added bounded concurrent TTS synthesis in `EngineTtsPlayer`: up to two engine instances pre-render
  upcoming sentences while playback remains strictly ordered. Generated chunks are lightly RMS/peak
  leveled before streaming to reduce sentence-to-sentence volume jumps.
- Reworked text segmentation for narration: keep paragraph boundaries as longer breathing pauses,
  split first on sentence-ending punctuation, split long clauses on comma/colon-like soft breaks,
  and avoid making overly long synthesis chunks.
- Improved book chapter detection per `book_chunking_guide.md`: EPUB uses EPUB3 nav / EPUB2 NCX
  `href#anchor` entries as actual split points before heading/spine fallback, TXT/Markdown detects
  standalone heading lines, and PDF now tries document Outline before page-based fallback.
- Switched the CosyVoice2 target package to ModelScope official `iic/CosyVoice2-0.5B` and added the
  previously missing development artifacts to the download whitelist. It is still not playable on
  Android because the text-speech LLM stage is published as `llm.pt`; a mobile runnable export/runtime
  for that stage is still required.
- Model downloads now keep partial files and resume with HTTP `Range` requests. ModelScope file
  downloads resume per whitelisted file under `<model>.tmp`; archive downloads resume the archive
  file before extraction. The app retries transient failures before surfacing an error.
- Introduced the `TtsEngine` SPI + `TtsEngineRegistry`; models now plug in behind a stable engine id.
- Refactored `SherpaTtsPlayer` -> engine-agnostic `EngineTtsPlayer` (playback no longer references a
  concrete model). Deleted `SherpaTtsPlayer.kt`.
- Reworked `TtsModelSpec`: removed the hard-coded `availableInApp` flag and sherpa-specific fields
  (`modelName`/`lexicon`/`ruleFar`/`requiredFiles`); added `engineId`, `engineParams`,
  `downloadStrategy`. Availability is now derived from the registry + on-disk files.
- Added a working default voice: MeloTTS zh_en (engine `sherpa-vits`), downloaded as a `.tar.bz2`
  release asset and extracted on device. The old "not playable until ONNX runtime is integrated"
  block/message is gone.
- CosyVoice2/3 and Fish remain as engine slots (`isImplemented = false`) with documented pipelines.

### Earlier (v0.2.7 prep)

- Added `x86_64` to `abiFilters` so debug APKs run on standard Android emulators.
- Fixed Sleep Timer button row overflow on narrow screens (split into 2×2 grid with weight).
- Fixed Language selector button row overflow (added weight to each button).
- Fixed Reader tools panel obscuring playback controls (hide controls when tools panel is open).
- Added `android:windowSoftInputMode="adjustResize"` to prevent keyboard from hiding search input.

## Known Traps

- Downloaded model files are not the same as a working runtime. CosyVoice/Fish need ONNX Runtime Mobile plus tokenizer, acoustic/vocoder wiring, and post-processing — they do NOT run through sherpa-onnx OfflineTts.
- The older ModelScope repo `Lourdle/CosyVoice2-0.5B_ONNX` ships ONLY the flow + hift back half (no LLM, no speech tokenizer, no speaker embedding). The app now targets official `iic/CosyVoice2-0.5B`, but that package still does not make Android playback automatic because the text-speech LLM stage is `llm.pt`, not a mobile-ready ONNX/runtime integration.
- New models go through the engine SPI. Do not add model-specific branching into `EngineTtsPlayer` / UI / ViewModel; put runtime logic inside a `TtsEngineFactory`/`TtsEngine` and reference it via `engineId`.
- Do not reintroduce Android system TTS as a quality fallback. It hides runtime gaps and makes test results misleading.
- Do not initialize TTS native engines in `AudioBookViewModel` construction. Keep startup independent from model runtime availability.
- ModelScope repositories may contain multiple variants. For `MODELSCOPE_FILES` models download only files listed in `TtsModelSpec.downloadFiles`; do not download the whole repository.
- Previously imported books keep old parsed metadata. Parser fixes usually require deleting and re-importing the book.
- Android file managers often restrict `Android/data`. Use USB or a capable file manager to inspect app-specific external files.
- EPUB WebView reading preserves more layout but is not yet synchronized to sentence index.

## Current Gaps

- Default offline playback works via the `sherpa-vits` engine (MeloTTS zh_en). This is the shippable voice.
- CosyVoice2/Fun-CosyVoice3/Fish engines are not yet implemented (slots only). Each needs its own multi-stage mobile pipeline; CosyVoice2 now has a fuller ModelScope development package, but still needs a mobile runnable text-speech LLM stage and post-processing. On-device real-time factor for the 0.5B autoregressive decode must be validated (quantize if RTF >= 1) before it can stream.
- The MeloTTS `engineParams` (lexicon/dict/ruleFsts filenames) should be confirmed against the actual extracted release contents on first integration test; adjust the map if the release layout differs.
- Reader settings are partially localized; not every small UI string is in the language map yet.
- EPUB chapter extraction now reads EPUB3 nav / EPUB2 NCX href anchors before falling back to headings/spine.
- PDF chapter navigation uses document Outline when available; page-based chapters are now only the fallback for PDFs without usable bookmarks.
- Search jump is sentence-based in text reader; WebView EPUB mode does not yet scroll to matching HTML location.

## Verification Checklist

- Run `.\scripts\build-debug.ps1`.
- Confirm generated APK name contains version, for example:
  `AudioBooks-debug-v0.2.3-4.apk`.
- Run `git diff --check`.
- Scan for personal path leakage before final response.
- Re-import affected books after parser changes before testing parser fixes.
