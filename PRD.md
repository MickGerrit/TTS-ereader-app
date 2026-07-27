# Product Requirements Document (PRD)

**Product:** Local-first ereader with built-in offline TTS (working name: TBD)
**Author:** Mick Gerritsen
**Status:** Draft v1 for designer + prototyper handoff
**Date:** 2026-07-27
**Audience:** the designer and prototyper building the **visual, clickable
prototype**. This document defines *what* the app is and *how it should look and
behave*, not how it is built. Engineering and architecture are out of scope here
and live in `TECHNICAL-PRD.md` (for the engineer, development phase).
**Document scope:** MVP (version 1). Post-MVP ideas listed under "Future".

---

## 1. Summary

A minimalist, local-first ereader for **Android first, iOS soon after**, with a
built-in **offline neural text-to-speech** engine that turns any book into an
audiobook-like listening experience without a network connection or a cloud
account. The app reads **EPUB** files, is fully bilingual (**Dutch and English**,
comfortably switchable), and stores reading progress in a
**KOReader-compatible sidecar** so a book can be continued on another device, or
in the KOReader app itself, once the user syncs the files across devices
themselves.

The guiding feeling is **minimalist yet complete**: it should just work out of the
box, with sensible defaults, and never feel unfinished.

## 2. Background and motivation

This is a personal product, built by and for the author. The core desire is an
**audiobook experience that runs entirely on-device**: pick up a book, press
play, and listen with a natural voice, without subscriptions, streaming, or
sending data anywhere. Existing readers either lack good offline TTS, feel
bloated, or lock progress into a proprietary cloud. This app deliberately avoids
a backend: the user owns their files and syncs them on their own terms.

## 3. Goals and non-goals

### 3.1 Goals (MVP)
- Read EPUB books with a clean, distraction-free reading surface.
- High-quality **offline** neural TTS in Dutch and English.
- Comfortable, fast switching between Dutch and English reading and listening.
- Rich but simple typography and color controls (fonts, bold, size, themes,
  warmth).
- One-time, low-friction setup: point the app at a folder, it finds the books.
- A tidy library view with covers, auto-fetching missing covers.
- Cross-device continuation of reading progress via KOReader-compatible sidecar
  files that the user syncs themselves.
- Works out of the box with good defaults; minimal configuration required.

### 3.2 Non-goals (MVP)
- No cloud account, no server, no automatic cloud sync. The user syncs files
  themselves (Syncthing, Dropbox, a cable, etc.).
- No formats other than EPUB in v1 (no PDF, MOBI, CBZ).
- No local AI features yet (recaps, summaries): reserved for v2.
- No store/catalog, no DRM-protected book support, no purchasing.
- No social features, annotations sharing, or multi-user accounts.
- No built-in file transfer between devices.

## 4. Target user

A single primary persona: the author. A technically comfortable reader who:
- Reads in both Dutch and English, often mixing the two.
- Wants to listen to books on-device, privately, offline.
- Already syncs files across devices (or is willing to).
- Values a calm, minimal interface over feature sprawl.
- May also use KOReader on an e-ink device and wants progress to carry over.

Designing for one opinionated user keeps scope tight. It should still be robust
enough to share later.

## 5. Platforms and scope

- **v1:** Android (phone first; tablet-friendly layout is a plus, not required).
- **Next:** iOS, coming soon after Android. The architecture must make this a
  build-out, not a rewrite (see Technical PRD).
- Fully offline capable. The only network calls in v1 are optional cover
  lookups, which the user can decline.

## 6. Key features (MVP)

### 6.1 Onboarding and setup
- First launch presents a short, friendly setup: choose the **books folder**.
- After folder selection the app **scans smartly** for EPUB files (recursively,
  skipping obvious non-book files), and populates the library.
- Sensible defaults are pre-selected so the user can start reading immediately
  without touching settings.
- TTS voices: the app ships ready to speak, or guides a one-tap download of the
  required voice model(s) if they are not bundled (see Technical PRD for the
  bundle-vs-download decision). This step must feel effortless and be clearly
  explained (size, language).

### 6.2 Library
- Grid or list of books with **covers**, title, author.
- **Missing covers are auto-fetched** from an open cover service (see 6.7).
- Basic sorting (recently read, title, author) and a simple search/filter.
- Continue-reading affordance: most recent book surfaced prominently.
- Re-scan action to pick up newly added files.
- Clear, quiet empty state guiding the user to add books.

### 6.3 Reader
- Reflowable EPUB rendering, paginated (page turns) as the default; scrolling
  mode is a nice-to-have.
- Distraction-free by default: tap to reveal minimal chrome (progress, TTS,
  settings), tap to hide.
- Progress indicator (chapter and overall percentage).
- Table of contents / chapter navigation.
- Remembers exact position per book.

### 6.4 Typography and appearance
Three fonts, each chosen for readability and openly licensed:
1. **Dyslexia-friendly** (e.g. OpenDyslexic).
2. **Sans-serif**, highly legible (e.g. Atkinson Hyperlegible or Inter).
3. **A nice readable serif** designed for screen reading (e.g. Literata).

Controls:
- **Bold** toggle (heavier weight for the body text).
- **Font size** (a comfortable range, live preview).
- **Line spacing and margins** (recommended to include; keeps "complete" feeling).
- Font choices apply instantly and persist per the user's global preference.

### 6.5 Colors and themes
Four coordinated reading themes plus a warmth control:
1. **Pure black background, white text** (OLED-friendly, true black).
2. **Black text on white**.
3. Implied middle grounds are optional; the two above are required poles.
4. **Warmth / yellow slider**: continuously warms the background toward a sepia
   or amber tone and reduces blue light. Works across the light themes; on the
   pure-black theme it warms the text/background subtly for night reading.

The warmth control is a **slider**, not fixed presets, so the user dials in their
comfort. Themes and warmth apply to the whole reading surface instantly.

### 6.6 Text-to-speech (the centerpiece)
- **Local, offline neural TTS.** No network, no cloud, no per-use cost.
- **Dutch and English voices.** The Dutch voice must handle **English words
  embedded in Dutch text** gracefully (common loanwords like "meeting",
  "deadline", "app", and ideally short English phrases). Full, natural
  mid-sentence code-switching is a stretch target; see the Technical PRD for the
  realistic bar and the fallback approach.
- **Comfortable language switching:** the app should pick the right voice per
  book (and ideally per passage) with minimal fuss, and let the user override
  the language/voice quickly.
- **Playback experience like an audiobook:**
  - Play / pause, skip sentence or paragraph, skip chapter.
  - Adjustable speaking rate (and ideally pitch).
  - **Word or sentence highlighting** synced to the voice as it reads.
  - Auto-scroll / auto-advance pages as it reads.
  - Background playback with lock-screen / notification controls and headphone
    controls.
  - A sleep timer (nice-to-have, strongly fits the audiobook use case).
- Starting TTS from the current reading position, and resuming reading from where
  TTS left off, should be seamless (position is shared).

### 6.7 Covers
- When a book has no embedded cover, the app looks it up from an **open,
  free cover service** (e.g. Open Library Covers, with Google Books as a possible
  secondary), matched by ISBN or title/author.
- Fetched covers are cached locally.
- Cover lookup is **optional and privacy-respecting**: it can be turned off, and
  the app clearly notes that this step uses the network. A tasteful generated
  placeholder (title/author on a colored card) is used when no cover is found or
  lookups are disabled.

### 6.8 Progress sync (cross-device, self-managed)
- Reading progress is written to a **KOReader-compatible sidecar** stored next to
  each book (KOReader's `.sdr` folder / `metadata.epub.lua` convention).
- The user **syncs the book folder themselves** across devices. When they open a
  book whose sidecar shows newer progress from another device (or from KOReader),
  the app offers to resume at that position.
- No accounts, no server. Interoperability with the real KOReader app is a
  first-class goal; see the Technical PRD for the realistic fidelity (percentage
  vs. exact position) and the associated risk.

### 6.9 Settings
- Single, well-organized settings screen: appearance, TTS (voice, rate, language
  behavior), library folder, covers on/off, sync/sidecar info.
- Everything has a sane default. Settings are the exception, not the price of
  entry.

## 7. UX principles

- **Minimalist yet complete.** Few visible controls, but nothing important
  missing. Restraint over feature-count.
- **Works out of the box.** Great defaults; the happy path needs zero
  configuration beyond choosing a folder.
- **Content first.** The book and the voice are the product; chrome recedes.
- **Calm and quiet.** No badges, upsells, popups, or noise.
- **Fast and responsive.** Page turns, theme changes, and TTS start feel instant.
- **Respectful and private.** Local by default; the network is opt-in and
  explained.

## 8. Primary user flows

1. **First run:** open app -> welcome -> choose books folder -> app scans and
   shows library -> (optional) fetch missing covers -> tap a book -> read.
2. **Read:** open book -> resume at last position -> adjust font/size/theme once
   -> read with tap-to-toggle chrome.
3. **Listen:** open book -> press play -> voice reads with synced highlight and
   auto-advance -> lock phone, controls on lock screen -> pause anytime -> reopen
   to see the reading position exactly where the voice stopped.
4. **Switch language:** open an English book -> app uses the English voice
   automatically; open a Dutch book with English loanwords -> Dutch voice handles
   them acceptably, user can override voice if needed.
5. **Continue on another device:** finish a session -> sidecar updated -> user
   syncs folder (their tool) -> open the same book on device B (this app or
   KOReader) -> offered to resume at synced position.
6. **Add books later:** drop new EPUBs in the folder -> open app -> re-scan (or
   auto-detect) -> new books appear with covers.

## 9. Screens for the designer

Minimum set to design for the MVP:
1. Onboarding / folder picker / voice-setup.
2. Library (grid and/or list) with covers, sort, search, empty state.
3. Reader surface (chrome hidden and chrome revealed states).
4. Reading settings panel (fonts, bold, size, spacing, themes, warmth slider).
5. TTS controls (in-reader mini player + expanded player, lock-screen /
   notification controls).
6. Table of contents / chapter navigation.
7. Book detail (optional): cover, metadata, progress, actions.
8. Settings.
9. Small system moments: cover-fetch consent, voice download progress,
   sync/resume prompt, re-scan.

Design deliverables should cover **light, pure-black, and warmed** variants of the
reading surface, and both **Dutch and English** UI copy (the interface itself is
bilingual; confirm whether UI language follows system locale or is a manual
toggle: see Open Questions).

## 10. Success criteria (MVP)

- The author uses it as a daily driver on Android for both reading and listening.
- TTS quality in Dutch and English is good enough to listen to a full book
  comfortably, with acceptable handling of English-in-Dutch.
- Setup to first page takes under a minute for someone with a folder of EPUBs.
- Reading progress reliably continues across the author's own devices, and
  interoperates with KOReader at least at chapter/percentage granularity.
- The app feels minimal and finished, not like a prototype.

## 11. Future (v2 and beyond)

Explicitly out of scope for v1, captured so the architecture leaves room:
- **Local AI features:** on-device recaps ("what happened so far"), chapter
  summaries, character/glossary helpers, ask-the-book, all running locally.
- Additional formats (PDF, MOBI/AZW3, comics).
- More voices, voice cloning, or per-character voices.
- In-app annotations, highlights, and notes (and syncing them via the sidecar).
- Statistics (reading time, streaks) kept local.
- Optional automatic sync backends for those who want them.

## 12. Assumptions

- Books are DRM-free EPUB files the user already owns and manages.
- The user is willing and able to sync a folder across devices themselves.
- Cover lookup by ISBN/title/author against an open service is acceptable and
  legal for personal use.
- The primary language pair is Dutch and English; other languages are not a v1
  requirement.

## 13. Open questions for Mick

These do not block the prototype but are worth deciding during design:

1. **UI language:** should the interface language follow the system locale
   automatically, or be a manual Dutch/English toggle in settings? (Reading and
   TTS language are separate and handled per book.)
2. **Auto vs. manual TTS language detection:** is per-book language detection
   enough for v1, or do you want automatic per-passage detection from the start
   (harder, ties into the code-switching work)?
3. **Reading mode default:** paginated page-turns (recommended) or scrolling,
   and should both be offered in v1?
4. **Tablet/large-screen:** is a phone-only layout acceptable for v1, or should
   the designer also cover tablet?
5. **Sleep timer and reading statistics:** in or out for v1? (Both fit the
   audiobook feel; both are cheap to design now.)
6. **Cover placeholder style:** happy with a generated title/author card when no
   cover is found, or do you want a specific look?

---

*Companion document (for the engineer, development phase): `TECHNICAL-PRD.md`
covers architecture, the TTS engine and code-switching approach, the KOReader
sidecar format and its fidelity limits, file access on Android, theming, cover
fetching, risks, and a build order.*
