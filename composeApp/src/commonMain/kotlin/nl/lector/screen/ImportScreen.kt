package nl.lector.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lector.data.LibrarySource
import nl.lector.data.ScanResult
import nl.lector.design.BtnStyle
import nl.lector.design.CardM3
import nl.lector.design.Eyebrow
import nl.lector.design.LectorButton
import nl.lector.design.LectorIcons
import nl.lector.design.LectorRow
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.M3Switch
import nl.lector.design.Mono
import nl.lector.design.Note
import nl.lector.design.Pill
import nl.lector.design.ProgressBar
import nl.lector.design.RowDivider
import nl.lector.design.Shape
import nl.lector.design.Wordmark
import nl.lector.design.toColor
import nl.lector.platform.rememberFolderPicker
import nl.lector.state.LectorState
import nl.lector.state.Screen

private enum class Phase { Pick, Scanning, Ready }

/**
 * First run: grant a folder, watch it scan, consent to covers, confirm voices.
 *
 * The prototype draws its own folder tree; a real app cannot. SAF owns that picker,
 * and going through it is exactly what grants the persistable permission that lets
 * Lector write sibling `.sdr` sidecars without all-files access (PRD §6.1, §6.8).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(state: LectorState, library: LibrarySource) {
    val c = LocalChrome.current
    var phase by remember { mutableStateOf(Phase.Pick) }
    var filesSeen by remember { mutableIntStateOf(0) }
    var booksFound by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<ScanResult?>(null) }

    val pickFolder = rememberFolderPicker { grant ->
        state.folder = grant
        state.save()
        phase = Phase.Scanning
    }

    LaunchedEffect(phase) {
        if (phase != Phase.Scanning) return@LaunchedEffect
        val grant = state.folder ?: return@LaunchedEffect
        filesSeen = 0
        booksFound = 0
        val scan = library.scan(grant) { files, books ->
            filesSeen = files
            booksFound = books
        }
        state.books.clear()
        state.books += scan.books
        result = scan
        state.lastScan = "just now"
        state.save()
        phase = Phase.Ready
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(start = 22.dp, end = 22.dp, top = 24.dp)
            .padding(bottom = 22.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Wordmark()

            Text(
                "Point it at a folder.\nIt finds your books.",
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.display, fontSize = 30.sp,
                    lineHeight = 34.8.sp, letterSpacing = (-0.45).sp, color = c.fg,
                ),
            )
            Text(
                "No account, no server. Your EPUBs and your reading position stay in your own folder.",
                style = TextStyle(
                    fontFamily = LocalFonts.current.body, fontSize = 15.sp,
                    lineHeight = 21.75.sp, color = c.muted,
                ),
            )

            if (phase == Phase.Pick) {
                Eyebrow("Step 1 — Books folder", Modifier.padding(top = 26.dp, bottom = 10.dp))
                CardM3 {
                    LectorRow(
                        title = state.folder?.label ?: "Choose a folder",
                        subtitle = state.folder?.let { "Granted. Tap to pick a different one." }
                            ?: "Opens the system folder picker",
                        onClick = pickFolder,
                    ) {
                        Icon(
                            LectorIcons.Folder, null,
                            Modifier.size(20.dp),
                            tint = if (state.folder != null) c.accent else c.muted,
                        )
                    }
                }
                Note(
                    "Uses the Storage Access Framework. Granting this folder lets Lector create " +
                        "sibling .sdr sidecars, with no all-files permission.",
                )
            }

            if (phase != Phase.Pick) {
                Eyebrow("Step 2 — Scanning", Modifier.padding(top = 26.dp, bottom = 10.dp))
                CardM3 {
                    LectorRow(
                        title = when {
                            phase == Phase.Ready && booksFound == 0 -> "No EPUBs found"
                            phase == Phase.Ready -> "$booksFound book${if (booksFound == 1) "" else "s"} found"
                            else -> "Reading folder…"
                        },
                        subtitle = result?.let { r ->
                            if (r.books.isEmpty()) {
                                "Searched ${r.filesSeen} files in ${state.folder?.label.orEmpty()}"
                            } else {
                                "${r.withEmbeddedCover} embedded covers · ${r.withoutCover} without · " +
                                    r.languageSummary
                            }
                        } ?: "$filesSeen files · $booksFound EPUB",
                        subtitleMono = phase != Phase.Ready,
                    )
                }
                // Files seen has no known total, so the bar tracks books found instead
                // of faking a percentage.
                ProgressBar(
                    fraction = if (phase == Phase.Ready) 1f else (booksFound / 12f).coerceIn(0f, 0.9f),
                    modifier = Modifier.padding(top = 14.dp),
                    trackColor = c.fill,
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    state.books.take(18).forEach { b ->
                        Box(
                            Modifier
                                .size(34.dp, 46.dp)
                                .clip(RoundedCornerShape(Shape.xs))
                                .background(b.coverBackground.toColor()),
                        )
                    }
                }
            }

            AnimatedVisibility(phase == Phase.Ready && booksFound > 0) {
                val withoutCover = result?.withoutCover ?: 0
                Column {
                    Eyebrow("Step 3 — Ready", Modifier.padding(top = 26.dp, bottom = 10.dp))
                    CardM3 {
                        LectorRow(
                            "Fetch missing covers",
                            subtitle = if (withoutCover > 0) {
                                "$withoutCover book${if (withoutCover == 1) "" else "s"} have no " +
                                    "embedded cover. Sends only title and author to Open Library. " +
                                    "Off by default."
                            } else {
                                "Every book carries its own cover. Nothing to look up."
                            },
                            onClick = { state.covers = !state.covers; state.save() },
                        ) { M3Switch(state.covers) }
                        RowDivider()
                        LectorRow("Voices", subtitle = "Dutch and English, bundled · 126 MB") {
                            Pill("Ready", on = true, dot = true)
                        }
                    }
                    Note(
                        "The app speaks straight out of the box. Higher-quality per-language " +
                            "voices are an optional download in Settings.",
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        LectorButton(
            label = when {
                phase == Phase.Scanning -> "Scanning…"
                phase == Phase.Ready && booksFound == 0 -> "Choose a different folder"
                phase == Phase.Ready -> "Open library"
                state.folder != null -> "Scan “${state.folder?.label}”"
                else -> "Choose a folder to continue"
            },
            enabled = phase != Phase.Scanning,
            style = BtnStyle.Filled,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                when {
                    phase == Phase.Ready && booksFound == 0 -> { phase = Phase.Pick; pickFolder() }
                    phase == Phase.Ready -> {
                        state.books.firstOrNull()?.let { state.openBook(it.id) }
                        state.finishOnboarding()
                        state.screen = Screen.Library
                    }

                    state.folder != null -> phase = Phase.Scanning
                    else -> pickFolder()
                }
            },
        )
    }
}
