package nl.lector.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lector.data.formatBytes
import nl.lector.design.Appearance
import nl.lector.design.CardM3
import nl.lector.design.ChevronRow
import nl.lector.design.Eyebrow
import nl.lector.design.LargeTopBar
import nl.lector.design.LectorRow
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.M3Slider
import nl.lector.design.M3Switch
import nl.lector.design.Mono
import nl.lector.design.Note
import nl.lector.design.Pill
import nl.lector.design.RowDivider
import nl.lector.design.Segmented
import nl.lector.state.LectorState

@Composable
fun SettingsScreen(
    state: LectorState,
    onAppearance: () -> Unit,
    onVoices: () -> Unit,
    onFetchCovers: () -> Unit,
    onPickFolder: () -> Unit,
    onClearCache: () -> Unit,
    cacheBytes: Long,
) {
    val c = LocalChrome.current
    val pending = state.booksWithoutCover().size

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        LargeTopBar("Settings")

        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            Eyebrow("Reading", Modifier.padding(bottom = 10.dp))
            CardM3 {
                ChevronRow(
                    "Appearance",
                    subtitle = "${state.font.label} · ${state.size} sp · ${state.theme.label} · warmth ${state.warmth}%",
                    onClick = onAppearance,
                )
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    RowLabel("Reading mode")
                    Text(
                        if (state.scrolling) {
                            "One continuous column. The tap zones still turn by a screen."
                        } else {
                            "Tap left or right to turn the page."
                        },
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                        style = TextStyle(
                            fontFamily = LocalFonts.current.body, fontSize = 12.5.sp,
                            color = c.muted,
                        ),
                    )
                    Segmented(
                        options = listOf(false, true),
                        selected = state.scrolling,
                        label = { if (it) "Scrolling" else "Paginated" },
                        onSelect = { state.scrolling = it; state.save() },
                    )
                }
                RowDivider()
                LectorRow(
                    "Keep the screen on",
                    subtitle = "While a book is open. Released the moment you leave it.",
                    onClick = { state.keepAwake = !state.keepAwake; state.save() },
                ) { M3Switch(state.keepAwake) }
            }

            Eyebrow("Voice & playback", Modifier.padding(top = 22.dp, bottom = 10.dp))
            CardM3 {
                ChevronRow("Voices", subtitle = state.voicesSummary(), onClick = onVoices)
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Speaking rate",
                            style = TextStyle(
                                fontFamily = LocalFonts.current.body, fontSize = 15.sp,
                                fontWeight = FontWeight.Medium, color = c.fg,
                            ),
                        )
                        Mono("${fmt1(state.rate)}×", size = 13.5.sp)
                    }
                    M3Slider(
                        value = (state.rate * 100f - 70f) / 130f,
                        onValue = { state.rate = rateFromSlider(it); state.save() },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        RowLabel("Speaking pitch")
                        Mono("${fmt1(state.pitch)}×", size = 13.5.sp)
                    }
                    M3Slider(
                        value = (state.pitch - 0.7f) / 0.7f,
                        onValue = { state.pitch = pitchFromSlider(it); state.save() },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                RowDivider()
                LectorRow(
                    "Listening in the background",
                    subtitle = "Lock-screen controls, headphone buttons, and audio that " +
                        "pauses for a call and picks itself up after",
                ) { Pill("On", on = true, dot = true) }
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    RowLabel("Sleep timer default")
                    Text(
                        if (state.sleepDefault > 0) {
                            "Every listening session starts a ${state.sleepDefault} minute timer."
                        } else {
                            "No timer unless you set one in Playback."
                        },
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                        style = TextStyle(
                            fontFamily = LocalFonts.current.body, fontSize = 12.5.sp,
                            color = c.muted,
                        ),
                    )
                    Segmented(
                        options = listOf(0, 15, 30, 45, 60),
                        selected = state.sleepDefault,
                        label = { if (it == 0) "Off" else "$it" },
                        onSelect = { state.sleepDefault = it; state.save() },
                    )
                }
            }

            Eyebrow("Library", Modifier.padding(top = 22.dp, bottom = 10.dp))
            CardM3 {
                LectorRow(
                    "Books folder",
                    subtitle = state.folder?.label ?: "None granted",
                    subtitleMono = true,
                    onClick = onPickFolder,
                ) { Mono("${state.books.size}", size = 13.5.sp) }
                RowDivider()
                LectorRow(
                    "Last scan",
                    subtitle = "Runs when the app opens, and whenever you pull down on the library",
                ) { Mono(state.lastScan, size = 13.5.sp) }
                RowDivider()
                LectorRow(
                    "Fetch missing covers",
                    subtitle = "The only outbound request this app makes. Sends title and author " +
                        "to Open Library, nothing else.",
                    onClick = {
                        state.covers = !state.covers
                        state.save()
                        state.show(
                            if (state.covers) {
                                "Covers on. Title and author go to Open Library for the " +
                                    "$pending books without one."
                            } else {
                                "Covers off. The app makes no network requests at all."
                            },
                            "Undo",
                        )
                    },
                ) { M3Switch(state.covers) }
                RowDivider()
                ChevronRow(
                    "Fetch covers now",
                    subtitle = if (pending > 0) {
                        "$pending book${if (pending > 1) "s" else ""} still " +
                            "show${if (pending > 1) "" else "s"} a generated placeholder"
                    } else {
                        "All ${state.books.size} books have a cover"
                    },
                    onClick = onFetchCovers,
                )
                RowDivider()
                LectorRow("Storage mode", subtitle = "No all-files permission requested") {
                    Pill("SAF")
                }
            }

            Eyebrow("Progress & sync", Modifier.padding(top = 22.dp, bottom = 10.dp))
            CardM3 {
                LectorRow(
                    "Sidecar",
                    subtitle = state.book?.let { "${it.title}.sdr/metadata.epub.lua" }
                        ?: "No book open",
                    subtitleMono = true,
                )
                RowDivider()
                LectorRow("Last written") { Mono(state.lastWrite ?: "never", size = 13.5.sp) }
                RowDivider()
                // Says out loud what round-trips and what does not. No screen may
                // imply more than chapter and percentage (HANDOFF §6.6).
                LectorRow(
                    "KOReader compatibility",
                    subtitle = "Reliable at chapter and percentage level. Exact position parity " +
                        "between Readium and crengine is not promised.",
                ) { Pill("Percentage") }
            }
            Note(
                "No sync transport is built in, by design. Sync the folder yourself with " +
                    "Syncthing, Dropbox or a cable; Lector reads the sidecar fresh on open and " +
                    "offers to resume if another device is ahead.",
            )

            Eyebrow("App theme", Modifier.padding(top = 22.dp, bottom = 10.dp))
            CardM3 {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Interface theme",
                        style = TextStyle(
                            fontFamily = LocalFonts.current.body, fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, color = c.fg,
                        ),
                    )
                    Text(
                        "Chrome only. The four reading themes stay independent, so a dark app " +
                            "can still open a paper-white page.",
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                        style = TextStyle(
                            fontFamily = LocalFonts.current.body, fontSize = 12.5.sp,
                            lineHeight = 17.5.sp, color = c.muted,
                        ),
                    )
                    Segmented(
                        options = Appearance.entries,
                        selected = state.appearance,
                        label = { it.label },
                        onSelect = { state.appearance = it; state.save() },
                    )
                }
            }

            Eyebrow("Storage", Modifier.padding(top = 22.dp, bottom = 10.dp))
            CardM3 {
                LectorRow(
                    "Books",
                    subtitle = "Stay in your own folder. Lector never copies them, so they " +
                        "cost nothing here.",
                ) { Mono("0 B", size = 13.5.sp) }
                RowDivider()
                LectorRow(
                    "Cover cache",
                    subtitle = "Artwork taken out of your EPUBs, plus anything Open Library " +
                        "sent. Tap to clear: the next scan rebuilds it.",
                    onClick = onClearCache,
                ) { Mono(formatBytes(cacheBytes), size = 13.5.sp) }
            }
            Note(
                "The interface is English only. A language toggle that changed nothing was " +
                    "worse than no toggle, so the row is gone until the strings are " +
                    "translatable (PRD §13.1). Reading and speech language come from each " +
                    "book and were never tied to it.",
            )
        }
    }
}

/** Slider 0..1 → speaking rate 0.7×..2.0×, in the prototype's 0.05 steps. */
fun rateFromSlider(t: Float): Float {
    val stepped = ((70f + t * 130f) / 5f).toInt() * 5
    return stepped / 100f
}

/** Slider 0..1 → pitch 0.7×..1.4×, in the same 0.05 steps as the rate. */
fun pitchFromSlider(t: Float): Float {
    val stepped = ((70f + t * 70f) / 5f).toInt() * 5
    return stepped / 100f
}
