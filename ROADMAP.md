# Lector Android: features and epics

**Purpose:** the working list for getting the Android app from "the shape is real" to
"daily driver", per PRD §10.
**Companions:** `PRD.md` (what and why), `TECHNICALPRD.md` (how), `Design/HANDOFF.md`
(tokens and interaction contracts).
**Last updated:** 2026-07-27, after Epics 11, 1, 2 and 9.1

Status vocabulary used below:

| Mark | Meaning |
|---|---|
| **Done** | Built and verified running on a device |
| **Partial** | Real, but a stated requirement is still missing |
| **Faked** | The interface says it works, the code does not do it |
| **Not built** | Absent |

---

## 1. Where the build actually is

### Done

- **Library from a real folder.** SAF grant, recursive scan, hidden folders and
  `.sdr` directories skipped, non-EPUB files ignored. Metadata (title, author,
  language, cover presence) read from each file's own package document.
- **Embedded covers.** Extracted from the zip during the scan, cached, rendered.
  Generated placeholder plus a `GEN` flag only for books with no artwork.
- **Reader on Readium 3.1.1.** Real EPUB rendering, paginated, opened straight from
  the SAF `content://` URI with no copy into app storage.
- **The design through Readium.** All four reading themes, the continuous warmth
  transform with per-theme ceilings, and the three bundled faces (Literata, Atkinson
  Hyperlegible, OpenDyslexic), applied live through `EpubPreferences`. No custom CSS
  injection was needed.
- **Tap contract.** Outer thirds turn, centre toggles chrome, hidden chrome stays
  click-through with the progress hairline and the play glyph (HANDOFF §5).
- **Speech on real content.** Text segments come from Readium, so the engine reads
  the actual book. Word timing from the platform engine's range callbacks, spoken
  segment highlighted through Readium's decoration layer, page follows the voice.
- **KOReader sidecar round-trip.** `<book>.sdr/metadata.epub.lua` written through
  SAF. Verified end to end: wipe app data, re-grant, rescan, progress returns.
- **Library search**, pull-to-scan, sleep timer, symmetric page progress.
- **Contents from the book itself.** Readium's navigation document, flattened, with
  each entry's real progression from the positions service. Jumps land on the
  chapter's own locator. Books without one fall back to the spine (Epic 2).
- **Reopening lands on the line.** The engine's own locator is restored on open, not
  a percentage that happens to be nearby (Epic 9.1).
- **Open Library cover lookup, for real.** Two plain GETs behind the consent toggle,
  cached next to the embedded covers, honest about what did not come back (Epic 1).

### Partial

- **Text to speech** uses the platform engine, not `sherpa-onnx`. It speaks and it
  reports word boundaries, but it is not the offline neural TTS the product is for
  (PRD §6.6). See **Epic 3**.
- **Voices screen** lists a fictional catalogue. Preview animates, install and remove
  flip a boolean. See **Epic 5**.

### Faked

Nothing left. The three screens that claimed something the code did not do (covers,
contents, the spike reader) now either do it or are gone.

### Not built

Background playback and lock-screen controls, the resume prompt when a sidecar is
ahead, library sort, scrolling reading mode, pitch, tablet layout, app icon, release
build configuration, iOS.

---

## 2. Epics

Ordered by my recommendation, not by size. Rationale for the order is in §3.

### Epic 1: Covers tell the truth — **Done**

**Why:** the app claimed Open Library answered when it did not. Everything else here
is honest, and this was the one screen that lied.

| # | Story | Acceptance | Status |
|---|---|---|---|
| 1.1 | Look up a missing cover by title and author | A real request goes out, only when the toggle is on | Done |
| 1.2 | Cache the result next to the other covers | Second launch shows it with no network | Done |
| 1.3 | `OL` flag only after a real fetch, `GEN` otherwise | Flag never contradicts what is on screen | Done |
| 1.4 | Failure is visible | No match, or offline, says so and stays `GEN` | Done |
| 1.5 | Batch "fetch covers now" | Reports how many actually came back | Done |

**Built:** `CoverSource` in `commonMain`, `OpenLibraryCovers` in `androidMain` using
`HttpURLConnection` — two requests do not justify an HTTP client dependency.
Covers land in the same cache file the scanner uses, so a fetched cover survives a
rescan; the scanner picks it up again through `cachedCover(id)`. The flag now reads
`GEN` whenever a placeholder is actually on screen and `OL` only over artwork that
came back, so a cleared cache cannot leave it lying. `INTERNET` is the only
permission added.
**Left:** verify against a real shelf on a device, and confirm the User-Agent
Open Library sees is acceptable to them.

---

### Epic 2: Real navigation — **Done**

**Why:** the Contents sheet showed six chapters from a Dutch novel regardless of
which book was open. It was the most visible remaining fiction.

| # | Story | Acceptance | Status |
|---|---|---|---|
| 2.1 | Read the publication's navigation document | Contents lists the book's own chapters | Done |
| 2.2 | Jump to a chapter by its Locator | Reader lands at the chapter start, not a percentage guess | Done |
| 2.3 | "Here" marks the chapter you are actually in | Derived from the current position | Done |
| 2.4 | Chapter shown in reader chrome and on Listening | Same source, so the three cannot disagree | Done |
| 2.5 | Books with no navigation document | Falls back to the spine | Done |

**Built:** `Publication.readChapters()` flattens `tableOfContents` (nested entries
kept, so a book that files its chapters under part headings still lists them) and
takes each entry's progression from `positionsByReadingOrder()`. `Chapter` in
`commonMain` carries title, progression and the locator JSON; `LectorState.chapter`
is the single answer to "where am I", used by the sheet, the reader chrome, the
Listening screen and the library card. `chapterFor(pct)` and the hard-coded `Toc`
are gone, as is `ReaderController.goTo(progression)`, replaced by a locator jump.
**Left:** "Here" only follows the locator flow while the reader is on screen; on
Listening it tracks the percentage, which is the same number one step later.

---

### Epic 3: Offline neural TTS (Spike A)

**Why:** this is the product. PRD §2 is explicit that the reason for building it is an
audiobook experience that runs entirely on device, with no cloud. The platform engine
is a stand-in, and on the emulator its Dutch voice already needed the network, which
is exactly the failure this epic exists to remove.

**Spike first, then integrate.** Do not build UI on it until the spike reports.

| # | Story | Acceptance |
|---|---|---|
| 3.1 | Stand up `sherpa-onnx` on a device | Synthesises a paragraph offline, aeroplane mode |
| 3.2 | Dutch with English loanwords | Author judges quality acceptable, per PRD §6.6 |
| 3.3 | Report time to first audio and model size | Numbers written down, decision recorded |
| 3.4 | Word or sentence boundaries out of the engine | Highlight driven by real audio, as it is now |
| 3.5 | Implement `TtsEngine` with it | The interface does not change; nothing above it changes |
| 3.6 | Segmentation fallback for mixed language | Per TECHNICALPRD §5.2 |
| 3.7 | Decide bundle versus download | Feeds Epic 5 and the import screen's "126 MB" claim |

**Notes:** the `TtsEngine` seam already exists and already carries language, rate and
a word callback, so this is a swap rather than a rewrite. `sherpa-onnx` ships JNI
bindings and prebuilt AARs; no NDK toolchain needed on this machine today.
**Size:** large. **Risk:** high, and it is the only remaining true unknown.

---

### Epic 4: Listening away from the app

**Why:** PRD §6.6 asks for background playback with lock-screen and headphone
controls. Right now closing the app stops the voice, which makes the audiobook use
case unusable in practice.

| # | Story | Acceptance |
|---|---|---|
| 4.1 | Playback survives leaving the app | Screen off, voice continues |
| 4.2 | `MediaSession` with metadata | Title, author and cover on the lock screen |
| 4.3 | Transport from the notification | Play, pause, skip sentence and chapter |
| 4.4 | Headphone buttons | Pause and resume from the cable or buds |
| 4.5 | Audio focus | Ducks for notifications, pauses for calls, resumes after |
| 4.6 | Position survives being killed | Reopen lands where the voice stopped |

**Notes:** HANDOFF §7 lists this as specified but not designed, so there is visual
work here too. `readium-navigator-media-tts` may cover part of it; evaluate against
a plain `MediaSessionService` before adopting.
**Size:** medium to large. **Risk:** medium.

---

### Epic 5: Voices become real packages

**Why:** depends on Epic 3 deciding what actually ships. Until then the screen is a
mock and should not be built on.

| # | Story | Acceptance |
|---|---|---|
| 5.1 | Real catalogue | Names, sizes and languages from a manifest, not source code |
| 5.2 | Download with progress | Real progress, cancellable, resumable |
| 5.3 | Install and remove | Files land on disk, removal frees the space |
| 5.4 | Preview speaks | Uses the actual voice, not a timer |
| 5.5 | Add from file | The `.onnx` picker the row already promises |
| 5.6 | Bundled size claim is true | The import screen's "126 MB" matches reality |

**Size:** medium. **Risk:** low once Epic 3 lands. **Blocked by:** Epic 3.

---

### Epic 6: Cross-device continuation

**Why:** the write half is done and verified. The read-and-reconcile half is what
makes it a feature rather than a file format.

| # | Story | Acceptance |
|---|---|---|
| 6.1 | Detect a sidecar ahead of local progress | On open, compare and notice |
| 6.2 | Offer to resume | Prompt, per PRD §6.8. Not yet designed, see HANDOFF §7 |
| 6.3 | Respect the decline | Local position wins and is written back |
| 6.4 | Round-trip against real KOReader | Test on an actual e-ink device |
| 6.5 | Do not clobber KOReader's own fields | Merge rather than replace |

**Notes:** 6.5 matters. The writer currently emits only fields we own, which is safe,
but a real KOReader sidecar carries bookmarks and highlights that a naive rewrite
would delete.
**Size:** small to medium. **Risk:** medium, because the failure mode is data loss in
someone else's file.

---

### Epic 7: Library completeness

| # | Story | Acceptance |
|---|---|---|
| 7.1 | Sort by recent, title, author | The control works, choice persists |
| 7.2 | Error states | Revoked permission, unreadable file, folder gone |
| 7.3 | Large libraries stay fast | Several hundred EPUBs scan without blocking |
| 7.4 | Incremental rescan | Unchanged files are not reparsed |
| 7.5 | Book detail (optional, PRD §9.7) | Cover, metadata, progress, actions |

**Notes:** 7.3 and 7.4 matter more than they look. Every scan currently reopens and
reparses every file, including extracting covers again.
**Size:** medium. **Risk:** low.

---

### Epic 8: Settings that do what they say

| # | Story | Acceptance |
|---|---|---|
| 8.1 | Interface language | The toggle changes the language, or the row goes away |
| 8.2 | Sleep timer default | Feeds the actual default |
| 8.3 | Speaking pitch | PRD §6.6 "and ideally pitch" |
| 8.4 | Reading mode | Paginated or scrolling, if PRD §13.3 says both |
| 8.5 | Storage and cache | Show what is used, allow clearing |

**Notes:** 8.1 is an open question in PRD §13.1, not just work. Decide follow-system
versus manual before building.
**Size:** small. **Risk:** low.

---

### Epic 9: Reading experience polish

| # | Story | Acceptance | Status |
|---|---|---|---|
| 9.1 | Restore the exact Locator | Reopen lands on the same line, not the same percent | **Done** |
| 9.2 | Skip chapter in the transport | The button says chapter, so it should move a chapter |
| 9.3 | Highlight granularity | Check segment size against real prose |
| 9.4 | Page-turn animation | Match the prototype's slide |
| 9.5 | Keep the screen on while reading | Configurable |
| 9.6 | Selection and copy | Or deliberately disable it |

**Notes:** 9.1 was nearly free, as expected. The saved locator opens the book, with
the percentage as the fallback for books last read before this existed; opening a
different book clears it, so it can never be another book's position. Reading it
back out of the sidecar rather than out of preferences is Epic 6.
**Size:** medium. **Risk:** low.

---

### Epic 10: Release readiness

| # | Story | Acceptance |
|---|---|---|
| 10.1 | App icon and name | Not the default Android icon |
| 10.2 | Release build | Minify and shrink, verified working, size reported |
| 10.3 | Permission revoked mid-use | Recovers with an explanation, no crash |
| 10.4 | Rotation and process death | State survives both |
| 10.5 | Accessibility pass | TalkBack, large text, touch targets |
| 10.6 | Tablet layout | HANDOFF §7 lists it as undesigned |
| 10.7 | Instrumented tests | The flows this document claims are done stay done |

**Notes:** 10.2 needs care. Readium and the TTS engine both use reflection in places,
so ProGuard rules need checking rather than assuming.
**Size:** medium. **Risk:** medium, mostly 10.2.

---

### Epic 11: Cleanup — **Done**

| # | Story | Status |
|---|---|---|
| 11.1 | Delete the Spike B screen in Settings, now redundant | Done, with `ReadiumSurface` which only it used |
| 11.2 | Remove vestigial state (`page`, and `word` if the new engine does not need it) | `page` gone; `word` stays until Epic 3 decides |
| 11.3 | Delete `SampleLibrary` fixtures, or move them to test sources only | Deleted; `NoLibrary` is the preview default |
| 11.4 | First commit, and a commit per epic after that | Done, one commit per epic from here |

**Size:** tiny.

---

### Epic 12: iOS

Out of scope for Android completion, listed so it is not forgotten. Roughly: add the
`iosArm64` target, implement the four `expect` declarations (preferences, folder
picker, cover decoding, system back), wrap the Readium Swift toolkit behind
`PlatformReader`, and `sherpa-onnx` behind `TtsEngine`. Everything else in
`commonMain` is already shared.

---

## 3. Suggested order, and why

Epics 11, 1, 2 and 9.1 are done. What is left, in order:

1. **Epic 3, the spike only** (3.1 to 3.3). It is the last thing that can invalidate
   decisions, and TECHNICALPRD §12 puts the spikes first for exactly that reason. Stop
   after the report and decide. This one needs a device and the author's ear, so it
   cannot be finished from the desk: 3.2 is a judgement call by definition.
2. **Epic 3 integration** (3.4 to 3.7), then **Epic 5**, which it unblocks.
3. **Epic 4.** Large, and the app is not a daily driver without it.
4. **Epics 6, 7, 8**, then **Epic 10** last, because release work spoils.

The small honest epics went first in the end, which was the defensible alternative.
The argument against it still stands for everything that remains: a week spent
polishing around a TTS engine that turns out to be unacceptable is a week wasted,
and the current engine's Dutch voice already failed offline once. Nothing further
should be built on the speech path until the spike reports.

---

## 4. What "done" means for Android v1

From PRD §10, unchanged:

- The author uses it daily on Android, for reading and for listening.
- Dutch and English TTS good enough for a full book, English-in-Dutch acceptable.
- Setup to first page under a minute.
- Progress carries between the author's devices and interoperates with KOReader at
  chapter and percentage level.
- It feels finished, not like a prototype.

Against that list today: setup and progress are met and verified. Reading is met.
Listening is not, on two counts: the voice is not the neural one, and it stops when
the app does. Those are Epics 3 and 4, and they are most of the remaining work.
