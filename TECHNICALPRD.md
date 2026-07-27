# Technical PRD

**Product:** Local-first ereader with offline neural TTS (working name: TBD)
**Companion to:** `PRD.md`
**Status:** Draft v1 for engineering handoff (development phase)
**Audience:** the engineer(s) who will build the app. The visual clickable
prototype is driven by `PRD.md`, not this document.
**Date:** 2026-07-27
**Author:** Mick Gerritsen

This document specifies the technical approach for the MVP. It is opinionated
where a default is clearly better, and explicit about the two or three genuinely
hard bets so the engineer can de-risk them early. Where something is a real
fork in the road, it is flagged as a **Key decision** with a recommendation.

---

## 1. Architecture overview

**Recommended stack: Kotlin Multiplatform (KMP) shared core + native UI per
platform.**

Rationale (given "Android first, iOS soon"):
- The shared, non-visual logic is the bulk of the app and benefits most from
  reuse: library scanning, the KOReader sidecar reader/writer, TTS orchestration,
  settings/preferences, cover fetching, and (later) local-AI plumbing. Writing
  this once in Kotlin and reusing it on iOS avoids a second implementation.
- The **reading surface and system integrations** are best done natively for
  quality and control: **Jetpack Compose** on Android now, **SwiftUI** on iOS
  later. Typography, gestures, and lock-screen/audio integration are areas where
  native pays off.
- The EPUB engine (Readium, see below) ships as **parallel native toolkits**
  (Kotlin for Android, Swift for iOS), which fits KMP-core + native-UI cleanly.

Layering:

```
+-----------------------------------------------------------+
|  UI (native)   Compose (Android)  |  SwiftUI (iOS, later)  |
+-----------------------------------------------------------+
|  Platform adapters: reader view, audio session, file I/O  |
+-----------------------------------------------------------+
|  Shared KMP core (Kotlin)                                  |
|   - Library model + scanner                                |
|   - Reading position + KOReader sidecar (read/write)       |
|   - TTS orchestrator (segmentation, queueing, highlight)   |
|   - Preferences / theming model                            |
|   - Cover service client + cache                           |
|   - (v2) local AI orchestration                            |
+-----------------------------------------------------------+
|  Native engines behind interfaces:                        |
|   - EPUB rendering: Readium (Kotlin / Swift toolkits)      |
|   - Neural TTS inference: ONNX Runtime / sherpa-onnx (JNI) |
|   - Audio playback: ExoPlayer/AudioTrack (AVAudioEngine)   |
+-----------------------------------------------------------+
```

**Alternative considered:** a single-codebase framework (Flutter, or Compose
Multiplatform for UI too). Rejected for v1 because native TTS inference, the
audio session, background playback, and fine typography all need platform work
anyway, so a shared-UI framework saves less than it appears while adding a layer
between us and the OS features this app leans on. Compose Multiplatform for the
non-reader screens can be revisited once iOS work starts if it proves worthwhile;
the KMP-core boundary keeps that option open.

**Pragmatic note for the first engineering build:** the very first working build
may be done **Android-native (Kotlin/Compose) directly**, with the shared logic
already written as plain Kotlin modules structured so they can be lifted into a
KMP `commonMain` when iOS work begins. This avoids KMP setup overhead slowing the
first working build while preserving the reuse path. Decide this with the
engineer based on their comfort with KMP.

## 2. EPUB rendering engine

**Key decision (most important in the project): Readium vs. crengine.**

The reader engine choice is entangled with KOReader compatibility (section 4),
because "reading position" is defined by the engine.

- **Option A: Readium (recommended for MVP).** The Readium Mobile toolkits
  (`readium/kotlin-toolkit`, `readium/swift-toolkit`) are mature, actively
  maintained, support EPUB reflow, custom fonts, theming, pagination, TOC, search,
  and expose text/DOM for TTS. They exist for both Android and iOS, matching the
  KMP-core + native-UI plan. Downside: Readium's position model (Locators, CSS
  selectors / progression) is **not** KOReader's `crengine` xpointer, so exact
  position interop with KOReader is not free (see 4.3).
- **Option B: crengine (KOReader's own engine).** Embedding `crengine`
  (KOReader's C++ rendering engine) would give **native, exact** KOReader position
  compatibility. Cost: crengine is a large C++ codebase designed for KOReader's
  Lua environment; wrapping it for a Compose/SwiftUI app is a significant
  undertaking with its own rendering/typography integration, and a much heavier
  iOS story. High risk for an MVP.

**Recommendation:** build on **Readium** for the MVP and interoperate with
KOReader at **percentage / chapter granularity** (reliable and achievable),
treating full xpointer-exact interop as a research spike, not an MVP commitment.
Re-evaluate crengine only if percentage-level interop proves insufficient in real
use. This keeps the biggest risk contained while still delivering the core
promise (continue roughly where you left off, across devices and KOReader).

## 3. File access and the books folder (Android)

The user picks a folder; the app scans it and must also **write sidecar files
next to the books** (KOReader's `.sdr` folders). Two mechanisms:

- **Storage Access Framework (SAF), `ACTION_OPEN_DOCUMENT_TREE`
  (recommended default).** Yields a persistable tree URI with read/write to the
  whole subtree, so we can enumerate EPUBs (`DocumentFile` / `contentResolver`)
  and create sibling `.sdr` folders and metadata files. Portable, Play-Store-safe,
  no broad storage permission. Slightly slower enumeration and a URI-based file
  model to work around.
- **`MANAGE_EXTERNAL_STORAGE` (all-files access).** Simpler direct `File` I/O and
  trivial sibling writes, but it is a sensitive permission that Play Store
  restricts. **Acceptable for a personal, sideloaded build**; risky if the app is
  ever published. Keep it behind an abstraction so it can be swapped.

**Recommendation:** implement a `BookStorage` interface with a SAF-backed
implementation as the default, and optionally a direct-file implementation for
personal builds. The scanner and sidecar writer must not assume raw file paths.

**Smart scan:** recursive walk of the chosen tree, include `*.epub`, skip hidden
folders, `.sdr` metadata, and obvious non-books; dedupe; extract embedded
metadata (title, author, language, cover, ISBN if present) via Readium's parser;
cache results in a local index (see 6) so re-scans are incremental (by file
size + modified time + name).

## 4. Reading progress and KOReader-compatible sidecar

### 4.1 What KOReader does
KOReader stores per-book state in a sibling folder named `<book>.sdr` containing
`metadata.epub.lua` (a serialized Lua table). Relevant fields include
`percent_finished` (0..1 overall progress), `last_xpointer` (crengine position),
bookmarks/highlights, and various view settings. It is a Lua table, not JSON.

### 4.2 What we implement (MVP)
- A **Lua-table serializer/deserializer** in the shared core sufficient to read
  and write the fields we use (KOReader's format is stable and simple enough to
  target without a full Lua runtime; a focused parser/emitter is enough).
- On close/pause/checkpoint, write `percent_finished` and our own precise
  position, plus a timestamp, into the sidecar.
- On open, if a sidecar exists with a **newer** timestamp than our last-known
  local state (i.e. progress came from another device or from KOReader), offer to
  resume there.
- Store our engine-native position (Readium Locator) in a namespaced field so our
  own cross-device continuation is exact between installs of this app, while
  `percent_finished` remains the interop lingua franca with KOReader.

### 4.3 Fidelity and the honest limit
- **Reliable:** `percent_finished` round-trips with KOReader, so "continue at
  roughly the same place / same chapter" works both directions.
- **Hard / research:** mapping KOReader's `last_xpointer` (crengine DOM pointer)
  to a Readium Locator and back for **exact** position parity. The two engines
  paginate and address text differently. Do not promise pixel-exact interop for
  MVP. A spike can attempt xpointer->CFI/Locator mapping, but treat any success
  as a bonus.
- Conflict handling: last-writer-wins by timestamp, with a resume prompt when the
  incoming position is ahead of the local one, so the user is never silently
  moved backward.

### 4.4 Sync transport
None built in, by design. The user syncs the folder (Syncthing, Dropbox, cable).
The app only reads/writes sidecar files in place and reacts to what it finds.
Because sync is external, the app must tolerate files changing under it and read
sidecars fresh on open.

## 5. Text-to-speech (offline neural)

This is the product's centerpiece and its second-biggest technical bet
(alongside the reader engine). Requirements: fully offline, natural Dutch and
English, and Dutch that copes with embedded English.

### 5.1 Inference runtime
- **`sherpa-onnx` (recommended)** or ONNX Runtime directly, called from the
  shared core through a native inference interface (JNI on Android; C-interop on
  iOS). `sherpa-onnx` is cross-platform (Android + iOS), supports several neural
  TTS model families (VITS/Piper-style, Kokoro, Matcha), does on-device streaming
  synthesis, and gives phoneme/segment timing usable for highlight sync. This
  keeps the runtime identical across platforms, which fits KMP.

### 5.2 Voice models: the code-switching problem
The requirement is a Dutch voice that also handles English words inside Dutch
text. Realistic assessment:

- **Single-language Piper-style voices** (one Dutch, one English) are small,
  fast, and high quality per language, but a Dutch-only model **mispronounces**
  English words. This alone does not meet the goal.
- **Multilingual models** (e.g. Kokoro, or other multilingual VITS/XTTS-family
  models) can pronounce multiple languages and handle **common loanwords** far
  better. Full, natural mid-sentence code-switching quality varies by model and
  is the genuinely hard part.

**Recommended two-layer approach:**
1. **Primary voice:** evaluate multilingual on-device models (start with Kokoro
   and any strong multilingual VITS available in the `sherpa-onnx` ecosystem) for
   Dutch quality *with* acceptable English-loanword handling. The realistic MVP
   bar: Dutch narration that pronounces everyday English loanwords ("meeting",
   "deadline", "app", "update") acceptably, not jarringly.
2. **Language-segmentation fallback (robustness):** in the orchestrator, run a
   lightweight **language detector over each text segment** (sentence or clause).
   Runs detected as a different language are synthesized with the appropriate
   voice and concatenated. This guarantees correct pronunciation even when the
   primary model struggles, at the cost of a small seam at switch points. Tune
   the granularity (whole-sentence switching sounds smoother than per-word).

**Key decision to validate early:** which model best balances Dutch quality,
English-in-Dutch handling, size, and speed on a mid-range Android phone. This
should be one of the first engineering spikes, because it drives voice download
size, latency, and whether the segmentation fallback is needed often or rarely.
Note licensing when selecting (e.g. some XTTS weights are non-commercial; fine
for personal use, relevant if ever distributed).

### 5.3 Bundle vs. download
Neural voice models range from a few MB to tens/hundreds of MB. Options: bundle a
default Dutch + English (or one multilingual) voice for out-of-the-box speech, or
download on first run with a clear, one-tap flow. **Recommendation:** bundle one
capable default voice so the app speaks immediately (PRD promise: works out of the
box), and allow optional additional/higher-quality voices via download. Confirm
the acceptable app-size budget with Mick.

### 5.4 TTS pipeline
1. **Text extraction:** pull the current chapter's text in reading order from
   Readium, with mapping back to DOM ranges/locators for highlighting.
2. **Segmentation:** split into sentences/clauses; run language detection per
   segment (5.2).
3. **Synthesis:** stream audio per segment from the neural model(s); prefetch the
   next segment(s) to avoid gaps.
4. **Playback:** ExoPlayer/AudioTrack (Android), AVAudioEngine/AVAudioPlayer
   (iOS), with an audio focus / audio session, background playback, and
   `MediaSession` (Android) / `MPNowPlayingInfoCenter` + remote commands (iOS) for
   lock-screen and headphone controls.
5. **Highlight + auto-advance:** use segment/word timing to highlight the current
   sentence (word-level if timing allows) and auto-scroll / turn the page; keep
   the reading position continuously updated so switching between reading and
   listening is seamless.
6. **Controls:** play/pause, skip sentence/paragraph/chapter, rate (and pitch if
   supported), sleep timer.

### 5.5 Performance targets
- Time-to-first-audio after pressing play: aim < ~1s on a mid-range phone
  (prefetch + stream).
- No audible gaps between segments during continuous playback.
- Reasonable battery use for long listening sessions (favor efficient models;
  consider NNAPI/GPU delegates where available).

## 6. Data model and local storage

- **Local index / library DB:** SQLite via a KMP-friendly layer (e.g. SQLDelight)
  storing per-book: stable id (content hash or path+size), title, author,
  language, file reference (URI), cover cache reference, last-known position and
  timestamp, and scan metadata. This is a **cache/index**, not the source of
  truth: the EPUB files and their sidecars are authoritative, so the index can be
  rebuilt by re-scanning.
- **Preferences:** typography (font, size, bold, spacing, margins), theme +
  warmth value, TTS (default voice, rate, language behavior), covers on/off,
  storage mode. Stored via a KMP settings mechanism, synced to the reader
  instantly.
- **Cover cache:** downloaded/generated covers in app storage, keyed by book id.

## 7. Cover fetching

- **Primary source:** Open Library Covers API (free, open), matched by ISBN when
  present in EPUB metadata, else by title + author search. Optional secondary
  (e.g. Google Books) if Open Library misses.
- **Flow:** only for books with no embedded cover; results cached locally;
  network use is **opt-in and clearly disclosed** (PRD 6.7). When disabled or no
  match, render a **generated placeholder** (title/author on a deterministic
  colored card).
- **Privacy:** the only data sent is the ISBN or title/author needed for lookup,
  and only when the user has covers enabled. No other telemetry.
- Implement behind a `CoverSource` interface so sources can be swapped/added.

## 8. Theming and typography system

- A shared theming model exposes: active theme (pure-black/white text,
  white/black text), a continuous **warmth** value, and the typography settings.
- Warmth maps to a background (and, on pure-black, a subtle text/bg) color
  transform toward sepia/amber plus blue-light reduction, applied live to the
  reader surface. Implement as a function of the warmth slider so it is
  continuous, not preset steps.
- Fonts are **bundled** (OpenDyslexic; a legible sans such as Atkinson
  Hyperlegible or Inter; a screen serif such as Literata) with open licenses
  (SIL OFL or similar). Verify and record each font's license. Readium supports
  injecting custom fonts and CSS for size, weight (bold), line height, and
  margins.
- All appearance changes apply instantly and persist globally (per PRD).

## 9. Internationalization

- UI strings externalized for Dutch and English from day one. Decide (Open
  Question in PRD) whether UI language follows system locale or is a manual
  toggle; keep the string layer ready for both.
- Reading language and TTS language are per book and independent of UI language.

## 10. Privacy and offline posture

- Fully functional with no network. The only outbound calls are optional cover
  lookups. No accounts, no analytics, no crash reporting that leaves the device
  unless explicitly added and disclosed later.
- All book content, positions, and voices stay on device. Sidecars stay in the
  user's own folder.

## 11. Key risks and mitigations

| # | Risk | Impact | Mitigation |
|---|------|--------|------------|
| 1 | Dutch-with-English TTS quality below expectation | Core promise weakened | Early model-selection spike; multilingual primary voice + language-segmentation fallback (5.2) |
| 2 | Exact KOReader position interop not feasible | Cross-device resume less precise | Commit to percentage-level interop for MVP; xpointer mapping as optional spike (4.3) |
| 3 | Readium limitations vs. desired reading feel | Reader UX compromise | Prototype the reader surface early; crengine remains a documented fallback (2) |
| 4 | Android file access friction (SAF URIs) + writing `.sdr` siblings | Setup/sync bugs | `BookStorage` abstraction; SAF default, optional all-files for personal build (3) |
| 5 | Neural TTS latency / battery on mid-range devices | Poor listening experience | Streaming + prefetch, efficient models, hardware delegates; measure early (5.5) |
| 6 | Voice model size vs. app size / bundle-vs-download | Onboarding friction | Bundle one default voice, optional downloads; agree size budget (5.3) |
| 7 | KMP setup overhead slows first build | Slower first build | Option to start Android-native with KMP-ready module boundaries, lift to `commonMain` when iOS starts (1) |
| 8 | External sync changing files under the app | Corrupt/stale state | Treat files as source of truth, read sidecars fresh, tolerate concurrent changes (4.4) |

## 12. Suggested build order (de-risk first)

1. **Spike A (TTS):** stand up `sherpa-onnx` on Android, run candidate Dutch /
   multilingual voices on real text, evaluate English-in-Dutch, measure latency
   and size. Decide primary voice and whether segmentation fallback is needed.
2. **Spike B (Reader):** Readium on Android rendering an EPUB with custom fonts,
   size, bold, and the theme/warmth transforms. Validate the reading feel.
3. **Spike C (Storage + sidecar):** SAF folder pick, scan, and read/write a
   KOReader `.sdr` `metadata.epub.lua`; round-trip `percent_finished` with a real
   KOReader install.
4. **Library:** index, covers (Open Library + placeholder), list/grid, re-scan.
5. **Reader integration:** position persistence, TOC, settings panel, theming.
6. **TTS integration:** pipeline, highlight sync, background playback + lock-screen
   controls, rate, sleep timer.
7. **Polish:** onboarding, empty states, resume prompts, performance pass.
8. **Structure for iOS:** ensure shared logic sits in KMP `commonMain`; begin
   SwiftUI + Readium Swift + `sherpa-onnx` iOS build.

## 13. Open technical questions for Mick

1. **App-size budget** for bundled voice model(s)? This drives bundle-vs-download
   and which models are viable.
2. **KMP now, or Android-native-first with KMP-ready boundaries?** Depends on the
   engineer's KMP experience and how fast you want the first working build.
3. **All-files access acceptable for your personal build**, or SAF-only from the
   start? (SAF is more portable; all-files is simpler for `.sdr` siblings.)
4. **Exact-position KOReader interop:** worth a research spike in v1, or is
   percentage/chapter-level resume enough for you?
5. **Target Android version / device** to tune TTS performance against (your
   daily phone)?

---

*Companion document: `PRD.md` for product scope, features, flows, and screens.*
