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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.lector.data.BookPages
import nl.lector.data.LibrarySource
import nl.lector.data.SampleLibrary
import nl.lector.data.Toc
import nl.lector.data.chapterStart
import nl.lector.data.pageModel
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
import nl.lector.reader.ReaderEngineSpike
import nl.lector.engine.TtsEngine
import nl.lector.screen.AppearanceSheetBody
import nl.lector.screen.ContentsSheetBody
import nl.lector.screen.ImportScreen
import nl.lector.screen.LibraryScreen
import nl.lector.screen.ListenScreen
import nl.lector.screen.PlaybackSheetBody
import nl.lector.screen.ReaderScreen
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
    library: LibrarySource = SampleLibrary(),
    engine: TtsEngine = SimulatedTts(),
    now: () -> String = { "--:--" },
) {
    val state = remember(prefs) { LectorState(prefs).apply { load() } }
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
        val next = state.page + direction
        when {
            next < 0 -> state.show("Start of the chapter.", "OK")
            next >= BookPages.size -> state.show("End of the sample text.", "OK")
            else -> {
                state.page = next
                state.word = -1
                state.ttsEpoch++
                // Symmetric: turning back moves the position back. The prototype only
                // ever counted up, which lets progress drift away from where you are.
                state.bumpProgress(if (direction > 0) state.pageStep else -state.pageStep)
                state.save()
            }
        }
    }

    /** Jumping from the Contents sheet moves the position, not just the highlight. */
    fun goToChapter(index: Int) {
        val id = state.book?.id ?: return
        state.progress[id] = chapterStart(index)
        state.page = (index * BookPages.size / Toc.size).coerceIn(0, BookPages.lastIndex)
        state.word = -1
        state.ttsEpoch++
        state.save()
    }

    fun seekSentence(direction: Int) {
        val sentences = pageModel(state.page).sentences
        val current = sentences.indexOfLast { state.word >= it.firstWord }.coerceAtLeast(0)
        val target = (current + direction).coerceIn(0, sentences.lastIndex)
        state.word = sentences[target].firstWord
        state.ttsEpoch++
        state.save()
    }

    /** One entry point for start/stop, so the card, the reader and the player agree. */
    fun toggleListen() {
        if (state.ttsOn) {
            state.ttsOn = false
        } else {
            state.ttsOn = true
            state.screen = Screen.Listen
        }
    }

    fun togglePlay() {
        state.ttsOn = !state.ttsOn
    }

    fun openBook(id: String) {
        state.openBook(id)
        // Until Readium renders the real file, every book opens onto the same sample
        // text. Saying so beats letting it look like the book you picked.
        state.show("Sample text — Readium rendering is not wired up yet.", "OK")
        state.screen = Screen.Reader
    }

    fun closeBook() {
        state.ttsOn = false
        state.lastWrite = now()
        state.save()
        state.screen = Screen.Library
        val pct = state.pct
        val title = state.book?.title ?: return
        // ponytail: says the sidecar was written; actually writing it is Spike C.
        state.show(
            "Wrote `percent_finished = ${fmt3(pct)}` to `$title.sdr` — " +
                "library now shows ${fmt1(pct * 100)}%.",
            "OK",
            durationMs = 5000,
        )
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

    // ── effects ───────────────────────────────────────────────────────────

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
        val result = library.scan(grant)
        state.books.clear()
        state.books += result.books
        state.scanning = false
        state.lastScan = "just now"
        state.save()

        if (!auto) {
            val added = result.books.count { it.id !in before }
            state.show(
                if (added > 0) {
                    "Scanned `${grant.label}` — *$added new*. ${result.books.size} indexed."
                } else {
                    "Scanned `${grant.label}` — no new EPUBs. ${result.books.size} indexed."
                },
                "OK",
            )
        }
    }

    LaunchedEffect(state.snack) {
        val snack = state.snack ?: return@LaunchedEffect
        delay(snack.durationMs)
        if (state.snack === snack) state.snack = null
    }

    LaunchedEffect(state.fetchingCover) {
        val id = state.fetchingCover ?: return@LaunchedEffect
        delay(1100)
        state.fetched[id] = true
        state.fetchingCover = null
        state.save()
        val title = state.books.firstOrNull { it.id == id }?.title ?: return@LaunchedEffect
        state.show("Cover for *$title* came back from Open Library and is cached locally.", "OK")
    }

    // Playback. Re-entered whenever the position moves under the engine's feet.
    LaunchedEffect(state.ttsOn, state.rate, state.ttsEpoch, state.bookId) {
        if (!state.ttsOn) return@LaunchedEffect

        // A missing voice should say so, not just fail to make a sound.
        engine.ensureReady(state.book?.language)?.let { reason ->
            state.ttsOn = false
            state.show(reason, "OK", durationMs = 5000)
            return@LaunchedEffect
        }

        while (state.ttsOn) {
            val words = pageModel(state.page).sentences.flatMap { it.words }
            val from = (state.word + 1).coerceIn(0, words.size)
            val failure = engine.speak(words, from, state.rate, state.book?.language) {
                state.word = it
            }

            // Without this the loop would "read" the whole book in silence, one
            // failed page at a time.
            if (failure != null) {
                state.ttsOn = false
                state.save()
                state.show(failure, "OK", durationMs = 6000)
                break
            }

            if (!state.ttsOn) break

            if (state.page + 1 < BookPages.size) {
                state.page++
                state.word = -1
                state.bumpProgress(state.pageStep)
            } else {
                state.word = -1
                state.ttsOn = false
                state.save()
                state.show("End of the sample text. Position saved.", "OK")
            }
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
    SystemBack(enabled = state.sheet == null && state.screen == Screen.EngineSpike) {
        state.screen = Screen.Settings
    }
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

                            Screen.Reader -> ReaderScreen(
                                state = state,
                                onBack = ::closeBook,
                                onContents = { state.sheet = Sheet.Contents },
                                onAppearance = {
                                    state.chromeHidden = false
                                    state.sheet = Sheet.Appearance
                                },
                                onTogglePlay = ::togglePlay,
                                onExpand = { state.screen = Screen.Listen },
                                onTurnPage = ::turnPage,
                            )

                            Screen.Listen -> ListenScreen(
                                state = state,
                                onReadAlong = { state.screen = Screen.Reader },
                                onPlayback = { state.sheet = Sheet.Playback },
                                onTogglePlay = ::togglePlay,
                                onSeekSentence = ::seekSentence,
                                onTurnPage = ::turnPage,
                            )

                            Screen.EngineSpike ->
                                ReaderEngineSpike(state) { state.screen = Screen.Settings }

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
                                onFetchCovers = { fetchAllCovers(state) },
                                onPickFolder = pickFolder,
                                onEngineSpike = { state.screen = Screen.EngineSpike },
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
                    onAction = { state.snack = null },
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

private fun fetchAllCovers(state: LectorState) {
    val pending = state.booksWithoutCover()
    when {
        pending.isEmpty() -> state.show("Every book already has a cover.", "OK")
        !state.covers -> state.show(
            "Cover lookup is off. Turn on *Fetch missing covers* first.", "OK",
        )

        else -> {
            pending.forEach { state.fetched[it.id] = true }
            state.save()
            val n = pending.size
            state.show("$n cover${if (n > 1) "s" else ""} fetched and cached locally.", "OK")
        }
    }
}

/** Three decimals, for the sidecar's `percent_finished`. */
private fun fmt3(v: Float): String {
    val scaled = kotlin.math.round(v * 1000.0).toInt()
    return "${scaled / 1000}.${(scaled % 1000).toString().padStart(3, '0')}"
}
