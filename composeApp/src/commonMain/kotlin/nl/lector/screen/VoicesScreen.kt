package nl.lector.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lector.data.BundledVoices
import nl.lector.data.Voice
import nl.lector.data.VoiceCatalogue
import nl.lector.data.VoiceSamples
import nl.lector.design.CardM3
import nl.lector.design.ChevronRow
import nl.lector.design.Eyebrow
import nl.lector.design.LectorIcons
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.Note
import nl.lector.design.Pill
import nl.lector.design.RowDivider
import nl.lector.design.Shape
import nl.lector.design.TopBar
import nl.lector.state.LectorState

/**
 * Voices are packages you install, not a language setting.
 *
 * Nothing here asks which language a book is in, and the runtime behind the voices
 * is never named: it is identical for all of them, so it could not differentiate
 * anything (HANDOFF §6.1, §6.2).
 */
@Composable
fun VoicesScreen(state: LectorState, onBack: () -> Unit) {
    val c = LocalChrome.current
    var previewLang by remember { mutableStateOf(state.installedVoices().first().lang) }
    var previewWord by remember { mutableIntStateOf(-1) }
    var previewRun by remember { mutableIntStateOf(0) }

    val sampleWords = (VoiceSamples[previewLang] ?: VoiceSamples.getValue("en")).split(" ")

    LaunchedEffect(previewRun) {
        if (previewRun == 0) return@LaunchedEffect
        previewWord = -1
        sampleWords.indices.forEach { i ->
            previewWord = i
            delay((260 / state.rate).toLong())
        }
        previewWord = -1
    }

    fun preview(lang: String) {
        previewLang = lang
        previewRun++
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Voices", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
                .padding(bottom = 30.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            // Said plainly rather than left to be discovered: this screen is the one
            // place still describing something that does not exist yet. It becomes
            // real in Epic 5, which waits on the engine decision in Epic 3.
            Note(
                "*Not real yet.* Lector currently speaks with the voices already installed " +
                    "on your device. The catalogue below is a placeholder for the offline " +
                    "voices that will ship once the speech engine is settled, and the " +
                    "preview animates the words rather than speaking them.",
            )

            Eyebrow("Preview", Modifier.padding(top = 18.dp, bottom = 10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Shape.md))
                    .background(c.surface)
                    .padding(16.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        sampleWords.forEachIndexed { i, w ->
                            if (i > 0) append(" ")
                            val start = length
                            append(w)
                            if (i == previewWord) {
                                addStyle(
                                    SpanStyle(background = c.accent, color = c.onAccent),
                                    start, length,
                                )
                            }
                        }
                    },
                    style = TextStyle(
                        fontFamily = LocalFonts.current.display, fontSize = 15.5.sp,
                        lineHeight = 23.25.sp, color = c.fg,
                    ),
                )
            }
            Note(
                "Tap ▶ on any voice to hear it. There is no language setting: Lector picks " +
                    "the voice that fits whatever it is reading.",
            )

            Eyebrow("Installed", Modifier.padding(top = 22.dp, bottom = 10.dp))
            CardM3 {
                val installed = state.installedVoices()
                installed.forEachIndexed { i, v ->
                    if (i > 0) RowDivider()
                    VoiceRow(
                        voice = v,
                        trailing = {
                            if (v in BundledVoices) {
                                Pill("Bundled")
                            } else {
                                Text(
                                    "REMOVE",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Shape.xs))
                                        .clickable {
                                            state.added.remove(v.id)
                                            state.save()
                                            state.show("Removed *${v.name}*.", "OK")
                                        }
                                        .padding(6.dp),
                                    style = TextStyle(
                                        fontFamily = LocalFonts.current.mono,
                                        fontSize = 13.5.sp, color = c.muted,
                                    ),
                                )
                            }
                        },
                        onPreview = { preview(v.lang) },
                        onClick = { preview(v.lang) },
                    )
                }
            }

            Eyebrow("Available to download", Modifier.padding(top = 22.dp, bottom = 10.dp))
            CardM3 {
                val available = VoiceCatalogue.filter { state.added[it.id] != true }
                if (available.isEmpty()) {
                    Box(Modifier.padding(16.dp)) {
                        Text(
                            "Every voice in the catalogue is installed.",
                            style = TextStyle(
                                fontFamily = LocalFonts.current.body,
                                fontSize = 12.5.sp, color = c.muted,
                            ),
                        )
                    }
                } else {
                    available.forEachIndexed { i, v ->
                        if (i > 0) RowDivider()
                        VoiceRow(
                            voice = v,
                            trailing = {
                                Text(
                                    "GET",
                                    style = TextStyle(
                                        fontFamily = LocalFonts.current.mono,
                                        fontSize = 13.5.sp, color = c.accent,
                                    ),
                                )
                            },
                            onPreview = { preview(v.lang) },
                            onClick = {
                                state.show(
                                    "Downloading *${v.name}* — ${v.detail}. One tap, then it " +
                                        "works offline forever.",
                                    "OK",
                                )
                                state.added[v.id] = true
                                state.save()
                            },
                        )
                    }
                }
            }

            Box(Modifier.padding(top = 22.dp)) {
                CardM3 {
                    ChevronRow(
                        "Add a voice from a file",
                        subtitle = "Point at an .onnx voice model in your files. Anything the " +
                            "engine can load shows up here.",
                    ) {
                        state.show(
                            "Pick an `.onnx` voice model from your files. Anything the engine " +
                                "can load shows up here.",
                            "OK",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: Voice,
    trailing: @Composable () -> Unit,
    onPreview: () -> Unit,
    onClick: () -> Unit,
) {
    val c = LocalChrome.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 68.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(c.tonal)
                .clickable(onClick = onPreview),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LectorIcons.Play, "Preview ${voice.name}", Modifier.size(15.dp), tint = c.accent)
        }

        Column(Modifier.weight(1f)) {
            Text(
                voice.name,
                style = TextStyle(
                    fontFamily = LocalFonts.current.body, fontSize = 15.sp,
                    lineHeight = 18.75.sp, fontWeight = FontWeight.Medium, color = c.fg,
                ),
            )
            Text(
                voice.detail,
                modifier = Modifier.padding(top = 3.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.mono, fontSize = 11.sp,
                    letterSpacing = 0.33.sp, color = c.muted,
                ),
            )
        }

        QualityBars(voice.quality)
        trailing()
    }
}

/** The little meter that says how good a voice is, without naming a runtime. */
@Composable
private fun QualityBars(quality: Int) {
    val c = LocalChrome.current
    Row(
        Modifier.height(13.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        (1..5).forEach { i ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((3 + i * 2).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= quality) c.accent else c.border),
            )
        }
    }
}
