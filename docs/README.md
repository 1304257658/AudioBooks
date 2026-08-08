# AudioBooks

Offline-first Android audiobook app. The app imports local books, parses readable text, stores all app data under one private app data root, and plays with a local TTS runtime when an integrated model is available.

See [`project/PROJECT.md`](project/PROJECT.md) for maintained implementation notes, architecture, known traps, and current gaps.

## Current Features

- Imports EPUB, TXT, and text-based PDF files.
- Parses EPUB spine XHTML/HTML, plain TXT, and embedded PDF text.
- Splits Chinese and English text into sentence-sized playback units.
- Separate Library, Reader, and Settings pages.
- Reader page is intentionally minimal: text, current position, previous/start-stop/next controls.
- Settings page owns global model management. Reader-specific controls live in the Reader tools panel.
- Follows the Android system light/dark theme.
- Stores book metadata, parsed text, and playback progress locally.
- Restores the last listened position and sorts recently listened books first.
- Playback speed supports 0.01x steps from 0.50x to 3.00x.
- Model download plumbing supports status-bar progress notifications and in-app progress display.

## Model Policy

Large model files are not committed to this repository and are not bundled into the APK.

The in-app model list now follows the provided mobile offline TTS research report:

- CosyVoice2-0.5B ONNX
- Fun-CosyVoice3-0.5B ONNX
- Fish Speech 1.5

These models are listed as target models, but their mobile runtime is not integrated yet. CosyVoice and Fish Speech need ONNX Runtime Mobile plus model-specific tokenizer, acoustic, vocoder, and post-processing work. The app does not use Android system TTS as a fallback because it would hide runtime gaps and produce misleading quality tests.

## Local Build

Use the repo scripts:

```powershell
.\scripts\build-debug.ps1
```

The generated debug APK is:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Install on a connected Android phone with USB debugging enabled:

```powershell
.\scripts\install-debug.ps1
```

You can also copy the APK to the phone and install it manually. Android may ask you to allow installing apps from that file manager or browser.

## Data And Privacy

The app does not upload books, reading history, device data, or generated audio. Public model downloads are allowed by design, but user content stays local. App data is kept under the app-private project data directory, so removing the app data removes books, parsed text, settings, downloads, models, and listening history together.

## Notes

- Debug APK currently targets `arm64-v8a`.
- Text PDF means PDFs that already contain embedded selectable text. Scanned/OCR-only PDFs are not implemented yet.
- Status-bar notifications on Android 13+ require the notification permission.
