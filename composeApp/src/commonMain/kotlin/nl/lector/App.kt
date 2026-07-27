package nl.lector

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.lector.data.CoverResult
import nl.lector.data.CoverSource
import nl.lector.data.LibrarySource
import nl.lector.data.NoCoverSource
import nl.lector.data.NoLibrary
import nl.lector.data.NoOpSidecarWriter
import nl.lector.data.NoPlaybackHost
import nl.lector.data.NoStorage
import nl.lector.data.NowPlaying
import nl.lector.data.PlaybackCommand
import nl.lector.data.PlaybackHost
import nl.lector.data.SidecarWriter
import nl.lector.data.Storage
import nl.lector.data.formatBytes
import nl.lector.platform.rememberFolderPicker
import nl.lector.design.Appearance
import nl.lector.design.BottomSheet
import nl.lector.design.Chrome
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.NavBar
import nl.lector.design.Snackbar
import nl.lector.design.rememberFonts
import nl.lector.engine.SimulatedTts
import nl.lector.reader.PlatformReader
import nl.lector.engine.TtsEngine
import nl.lector.screen.AppearanceSheetBody
import nl.lector.screen.ContentsSheetBody
import nl.lector.screen.ImportScreen
import nl.lector.screen.LibraryScreen
import nl.lector.screen.ListenScreen
import nl.lector.screen.PlaybackSheetBody
import nl.lector.screen.SettingsScreen
import nl.lector.screen.VoicesScreen
import nl.lector.screen.fmt1
import nl.lector.state.LectorState
import nl.lector.state.MemoryPrefs
import nl.lector.state.Prefs
import nl.lector.state.Screen
import nl.lector.state.Sheet

/**
 * Lector — a local-first ereader with offline neural TTS.
 *
 * Everything below this function is shared code: screens, tokens, state and the
 * reading-theme maths all live in `commonMain`, so the iOS build is a target to add
 * rather than a second implementation. Only the genuinely platform-bound pieces —
 * preferences, system back, and later the Readium and `sherpa-onnx` bindings — sit
 * behind a small interface or `expect`/`actual`.
 */
@Composable
fun App(
    prefs: Prefs = MemoryPrefs(),
    library: LibrarySource = NoLibrary(),
    engine: TtsEngine = SimulatedTts(),
    sidecar: SidecarWriter = NoOpSidecarWriter(),
    coverSource: CoverSource = NoCoverSource(),
    storage: Storage = NoStorage(),
    playback: PlaybackHost = NoPlaybackHost(),
    now: () -> String = { "--:--" },
) {
    val state = remember(prefs) { LectorState(prefs).apply { load() } }
    // Recomputed when something changed it, not on every recomposition.
    var cacheGeneration by remember { mutableIntStateOf(0) }
    val cacheBytes = remember(cacheGeneration, state.books.size) { storage.usedBytes() }
    val fonts = rememberFonts()
    val pickFolder = rememberFolderPicker { grant ->
        state.folder = grant
        state.save()
        state.scanIsAuto = false
        state.scanning = true
    }

    val dark = when (state.appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    val chrome = if (dark) Chrome.Dark else Chrome.Light

    // ── behaviours shared by more than one entry point ────────────────────

    fun turnPage(direction: Int) {
        // The engine owns pagination now; progress follows from its locator.
        val controller = state.readerController
        if (controller != null) {
            if (direction > 0) controller.goForward() else controller.goBackward()
            return
        }
        // No reader on screen (the Listening transport), so move the position itself.
        state.bumpProgress(if (direction > 0) state.pageStep else -state.pageStep)
        state.save()
    }

    /** Jumping from the Contents sheet moves the position, not just the highlight. */
    fun goToChapter(index: Int) {
        val id = state.book?.id ?: return
        val chapter = state.chapters.getOrNull(index) ?: return
        // The reader lands on the chapter's own locator; the percentage follows from
        // it once the engine reports back, and stands in until then.
        state.progress[id] = chapter.progression
        state.readerController?.goTo(chapter.locatorJson)
        state.word = -1
        state.ttsEpoch++
        state.save()
    }

    /**
     * The transport's outer buttons say chapter, so they move a chapter (story 9.2).
     *
     * Back from the middle of a chapter goes to that chapter's start first, which is
     * what every audio player does and what "previous" means to a listener.
     */
    fun seekChapter(direction: Int) {
        val chapters = state.chapters
        // A book with no navigation document has no chapters to skip; the page is
        // the only unit it has.
        if (chapters.isEmpty()) return turnPage(direction)

        val here = state.chapterIndex.coerceAtLeast(0)
        val restart = direction < 0 && state.pct > chapters[here].progression + 0.01f
        val target = (if (restart) here else here + direction).coerceIn(0, chapters.lastIndex)
        goToChapter(target)
    }

    fun seekSentence(direction: Int) {
        if (state.spokenSegments.isEmpty()) return
        state.spokenIndex = (state.spokenIndex + direction)
            .coerceIn(0, state.spokenSegments.lastIndex)
        state.ttsEpoch++
    }

    /** A session that starts arms the sleep timer, if a default is set (story 8.2). */
    fun startListening() {
        state.ttsOn = true
        if (state.sleepMinutesLeft == null && state.sleepDefault > 0) {
            state.sleepMinutesLeft = state.sleepDefault
        }
    }

    /** One entry point for start/stop, so the card, the reader and the player agree. */
    fun toggleListen() {
        if (state.ttsOn) {
            state.ttsOn = false
        } else {
            startListening()
            state.screen = Screen.Listen
        }
    }

    fun togglePlay() {
        if (state.ttsOn) state.ttsOn = false else startListening()
    }

    /**
     * Another device got further. Offer to follow it; ignoring the offer keeps this
     * device's position, which is then what gets written back on close (story 6.3).
     */
    fun offerResume(book: nl.lector.data.Book) {
        val there = book.sidecarProgress
        val here = state.pctOf(book.id)
        state.show(
            "Another device is at *${fmt1(there * 100)}%* of `${book.title}`. " +
                "This one is at ${fmt1(here * 100)}%.",
            "Resume there",
            durationMs = 8000,
        ) {
            state.progress[book.id] = there
            val locator = book.sidecarLocator
            if (locator != null) {
                // Written by Lector on the other device, so it is a real position.
                state.readerLocator = locator
                state.readerController?.goTo(locator)
            } else {
                // Written by KOReader: percentage is all the two engines share.
                state.readerLocator = null
                state.readerController?.goToProgression(there)
            }
            state.save()
        }
    }

    fun openBook(id: String) {
        state.openBook(id)
        state.screen = Screen.Reader
        state.books.firstOrNull { it.id == id }
            ?.takeIf { state.sidecarAhead(it) }
            ?.let(::offerResume)
    }

    fun closeBook() {
        state.ttsOn = false
        state.screen = Screen.Library
        state.writeSidecarOnClose = state.book
    }

    fun fetchCover(id: String) {
        // Gated on the covers toggle, because that toggle is the user's consent for
        // the only network call the app makes (PRD §6.7).
        if (!state.covers) {
            state.show(
                "Cover lookup is off. Turn on *Fetch missing covers* in Settings first.",
                "Settings",
            )
            return
        }
        state.fetchingCover = id
    }

    /** A cover that actually came back replaces the placeholder for that book. */
    fun applyCover(id: String, path: String) {
        val index = state.books.indexOfFirst { it.id == id }
        if (index >= 0) state.books[index] = state.books[index].copy(coverImagePath = path)
        state.fetched[id] = true
        state.save()
    }

    // ── effects ───────────────────────────────────────────────────────────

    /**
     * The lock screen, the notification and the headphone buttons all arrive here,
     * and go to the same functions the on-screen transport calls (Epic 4).
     */
    LaunchedEffect(Unit) {
        playback.onCommand { command ->
            when (command) {
                PlaybackCommand.Play -> startListening()
                PlaybackCommand.Pause -> state.ttsOn = false
                PlaybackCommand.Next -> seekChapter(1)
                PlaybackCommand.Previous -> seekChapter(-1)
                PlaybackCommand.Stop -> {
                    state.ttsOn = false
                    state.spokenIndex = -1
                    state.save()
                }
            }
        }
    }

    // What the outside world is told. Keyed on what a lock screen actually shows, so
    // it is not rebuilt on every word.
    LaunchedEffect(state.listening, state.ttsOn, state.bookId, state.chapter?.title) {
        val book = state.book
        if (!state.listening || book == null) {
            playback.hide()
            return@LaunchedEffect
        }
        playback.show(
            NowPlaying(
                title = book.title,
                author = book.author,
                chapter = state.chapter?.title,
                coverPath = book.coverImagePath,
                playing = state.ttsOn,
            ),
        )
    }

    // Pausing is where a listening session usually ends, killed process or not, so
    // it is where the position is worth writing down (story 4.6).
    LaunchedEffect(state.ttsOn) { if (!state.ttsOn) state.save() }

    // The folder is scanned every time the app opens, quietly.
    LaunchedEffect(Unit) {
        if (state.onboarded && state.folder != null) {
            state.scanIsAuto = true
            state.scanning = true
        }
    }

    LaunchedEffect(state.scanning) {
        if (!state.scanning) return@LaunchedEffect
        val auto = state.scanIsAuto
        val grant = state.folder
        if (grant == null) {
            state.scanning = false
            return@LaunchedEffect
        }
        val before = state.books.map { it.id }.toSet()
        // What the last scan found, so unchanged files are not reparsed (story 7.4).
        val result = library.scan(grant, state.books.associateBy { it.id })
        state.scanning = false

        if (result.error != null) {
            // The shelf we already have is more useful than an empty one, so it stays
            // on screen under the explanation.
            state.scanError = result.error
            state.show(result.error, "OK", durationMs = 6000)
            return@LaunchedEffect
        }

        state.scanError = null
        state.books.clear()
        state.books += result.books
        state.lastScan = "just now"
        state.save()

        if (!auto) {
            val added = result.books.count { it.id !in before }
            state.show(
                if (added > 0) {
                    "Scanned `${grant.label}` — *$added new*. ${result.books.size} indexed."
                } else {
                    "Scanned `${grant.label}` — no new EPUBs. ${result.books.size} indexed, " +
                        "${result.reused} unchanged."
                },
                "OK",
            )
        }
    }

    // Leaving a book writes the sidecar, and says what actually happened.
    LaunchedEffect(state.writeSidecarOnClose) {
        val book = state.writeSidecarOnClose ?: return@LaunchedEffect
        state.writeSidecarOnClose = null
        val pct = state.pctOf(book.id)
        val failure = sidecar.write(book, pct, state.readerLocator)
        if (failure == null) {
            state.lastWrite = now()
            state.save()
            state.show(
                "Wrote `percent_finished = ${fmt3(pct)}` to `${book.title}.sdr` — " +
                    "library now shows ${fmt1(pct * 100)}%.",
                "OK",
                durationMs = 5000,
            )
        } else {
            state.show(failure, "OK", durationMs = 6000)
        }
    }

    LaunchedEffect(state.snack) {
        val snack = state.snack ?: return@LaunchedEffect
        delay(snack.durationMs)
        if (state.snack === snack) state.snack = null
    }

    // One cover, from the button on the placeholder. The batch below drives the same
    // flag for its spinner, so this stands aside while that is running.
    LaunchedEffect(state.fetchingCover) {
        val id = state.fetchingCover ?: return@LaunchedEffect
        if (state.fetchingAll) return@LaunchedEffect
        val book = state.books.firstOrNull { it.id == id }
        val result = book?.let { coverSource.fetch(it) }
        state.fetchingCover = null
        when (result) {
            null -> Unit
            is CoverResult.Found -> {
                applyCover(id, result.path)
                state.show(
                    "Cover for *${book.title}* came back from Open Library and is cached locally.",
                    "OK",
                )
            }

            // Failure is said out loud rather than left as a placeholder that looks
            // like nothing happened (story 1.4).
            is CoverResult.Missing -> state.show(result.reason, "OK", durationMs = 5000)
        }
    }

    // "Fetch covers now": every placeholder in one pass, reporting what came back.
    LaunchedEffect(state.fetchingAll) {
        if (!state.fetchingAll) return@LaunchedEffect
        val pending = state.booksWithoutCover()
        when {
            !state.covers -> state.show(
                "Cover lookup is off. Turn on *Fetch missing covers* first.", "OK",
            )

            pending.isEmpty() -> state.show("Every book already has a cover.", "OK")

            else -> {
                var found = 0
                pending.forEach { book ->
                    state.fetchingCover = book.id
                    val result = coverSource.fetch(book)
                    if (result is CoverResult.Found) {
                        applyCover(book.id, result.path)
                        found++
                    }
                }
                state.fetchingCover = null
                state.show(
                    "$found of ${pending.size} cover${if (pending.size > 1) "s" else ""} " +
                        "came back from Open Library. " +
                        "${pending.size - found} still show a generated placeholder.",
                    "OK",
                    durationMs = 5000,
                )
            }
        }
        state.fetchingAll = false
    }

    // Playback. Re-entered whenever the position moves under the engine's feet.
    LaunchedEffect(state.ttsOn, state.rate, state.pitch, state.ttsEpoch, state.bookId) {
        if (!state.ttsOn) return@LaunchedEffect

        // A missing voice should say so, not just fail to make a sound.
        engine.ensureReady(state.book?.language)?.let { reason ->
            state.ttsOn = false
            state.show(reason, "OK", durationMs = 5000)
            return@LaunchedEffect
        }

        // The engine parses the book asynchronously; playback waits for it rather
        // than falling back to something that is not this book.
        if (state.spokenSegments.isEmpty()) {
            state.show("Still opening the book — try play again in a moment.", "OK")
            state.ttsOn = false
            return@LaunchedEffect
        }

        var index = state.spokenIndex.coerceAtLeast(0)
        while (state.ttsOn && index <= state.spokenSegments.lastIndex) {
            state.spokenIndex = index
            val segment = state.spokenSegments[index]
            val failure = engine.speak(
                words = segment.text.split(" ").filter { it.isNotBlank() },
                from = 0,
                rate = state.rate,
                pitch = state.pitch,
                language = state.book?.language,
                onWord = { state.word = it },
            )

            if (failure != null) {
                state.ttsOn = false
                state.save()
                state.show(failure, "OK", durationMs = 6000)
                return@LaunchedEffect
            }
            index++
        }

        if (state.ttsOn) {
            state.ttsOn = false
            state.spokenIndex = -1
            state.save()
            state.show("End of the book. Position saved.", "OK")
        }
    }

    // Sleep timer: counts down only while something is actually playing, and pauses
    // rather than stopping, so the position is exactly where the voice left off.
    LaunchedEffect(state.sleepMinutesLeft != null, state.ttsOn) {
        while (state.sleepMinutesLeft != null && state.ttsOn) {
            delay(60_000)
            val left = (state.sleepMinutesLeft ?: break) - 1
            if (left <= 0) {
                state.sleepMinutesLeft = null
                state.ttsOn = false
                state.show("Sleep timer finished. Paused, position saved.", "OK")
            } else {
                state.sleepMinutesLeft = left
            }
        }
    }

    // ── back ──────────────────────────────────────────────────────────────

    SystemBack(enabled = state.sheet != null) { closeSheet(state) }
    SystemBack(enabled = state.sheet == null && state.screen == Screen.Reader) { closeBook() }
    SystemBack(enabled = state.sheet == null && state.screen == Screen.Voices) {
        state.screen = if (state.listening) Screen.Listen else Screen.Settings
    }
    SystemBack(
        enabled = state.sheet == null &&
            (state.screen == Screen.Listen || state.screen == Screen.Settings),
    ) { state.screen = Screen.Library }

    // ── tree ──────────────────────────────────────────────────────────────

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalChrome provides chrome, LocalFonts provides fonts) {
            Box(Modifier.fillMaxSize().background(chrome.bg)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (state.screen) {
                            Screen.Import -> ImportScreen(state, library)

                            Screen.Library -> LibraryScreen(
                                state = state,
                                onOpenBook = ::openBook,
                                onToggleListen = ::toggleListen,
                                onScan = { state.scanIsAuto = false; state.scanning = true },
                                onFetchCover = ::fetchCover,
                            )

                            Screen.Reader -> PlatformReader(
                                state = state,
                                onBack = ::closeBook,
                                onContents = { state.sheet = Sheet.Contents },
                                onAppearance = {
                                    state.chromeHidden = false
                                    state.sheet = Sheet.Appearance
                                },
                                onTogglePlay = ::togglePlay,
                                onExpand = { state.screen = Screen.Listen },
                            )

                            Screen.Listen -> ListenScreen(
                                state = state,
                                onReadAlong = { state.screen = Screen.Reader },
                                onPlayback = { state.sheet = Sheet.Playback },
                                onTogglePlay = ::togglePlay,
                                onSeekSentence = ::seekSentence,
                                onSeekChapter = ::seekChapter,
                            )

                            Screen.Voices -> VoicesScreen(state) {
                                state.screen = if (state.listening) Screen.Listen else Screen.Settings
                            }

                            Screen.Settings -> SettingsScreen(
                                state = state,
                                onAppearance = {
                                    state.sheetReturn = Screen.Settings
                                    state.screen = Screen.Reader
                                    state.sheet = Sheet.Appearance
                                },
                                onVoices = { state.screen = Screen.Voices },
                                onFetchCovers = { state.fetchingAll = true },
                                onPickFolder = pickFolder,
                                onClearCache = {
                                    val freed = storage.clear()
                                    cacheGeneration++
                                    // The covers are gone from memory too, so rebuild
                                    // them rather than showing placeholders until the
                                    // next launch.
                                    state.scanIsAuto = true
                                    state.scanning = true
                                    state.show(
                                        "Cleared ${formatBytes(freed)} of cached covers. " +
                                            "Rebuilding from your books.",
                                        "OK",
                                    )
                                },
                                cacheBytes = cacheBytes,
                            )
                        }
                    }

                    // Reader and Voices are pushed screens, not tabs.
                    if (state.screen in setOf(Screen.Library, Screen.Listen, Screen.Settings)) {
                        NavBar(state.screen) { destination ->
                            // "Listening" means take me to what is playing, which is
                            // always the last book — playing or not.
                            state.screen = destination
                        }
                    }
                }

                BottomSheet(
                    visible = state.sheet == Sheet.Appearance,
                    title = "Appearance",
                    onDismiss = { closeSheet(state) },
                ) { AppearanceSheetBody(state) }

                BottomSheet(
                    visible = state.sheet == Sheet.Contents,
                    title = "Contents",
                    onDismiss = { closeSheet(state) },
                ) { ContentsSheetBody(state) { index -> goToChapter(index); closeSheet(state) } }

                BottomSheet(
                    visible = state.sheet == Sheet.Playback,
                    title = "Playback",
                    onDismiss = { closeSheet(state) },
                ) { PlaybackSheetBody(state) { state.screen = Screen.Voices; closeSheet(state) } }

                val navBarVisible = state.screen in setOf(Screen.Library, Screen.Listen, Screen.Settings)
                Snackbar(
                    snack = state.snack,
                    onAction = {
                        val snack = state.snack
                        state.snack = null
                        snack?.onAction?.invoke()
                    },
                    bottomPadding = (if (navBarVisible) 92.dp else 24.dp) +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                )
            }
        }
    }
}

/** Closing a sheet returns to wherever it was opened from, not to whatever is under it. */
private fun closeSheet(state: LectorState) {
    state.sheet = null
    state.sheetReturn?.let {
        state.screen = it
        state.sheetReturn = null
    }
}

/** Three decimals, for the sidecar's `percent_finished`. */
private fun fmt3(v: Float): String {
    val scaled = kotlin.math.round(v * 1000.0).toInt()
    return "${scaled / 1000}.${(scaled % 1000).toString().padStart(3, '0')}"
}
