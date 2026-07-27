package nl.lector.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lector.data.Toc
import nl.lector.data.chapterIndexFor
import nl.lector.design.CardM3
import nl.lector.design.ChevronRow
import nl.lector.design.Eyebrow
import nl.lector.design.LectorRow
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.M3Slider
import nl.lector.design.M3Switch
import nl.lector.design.Mono
import nl.lector.design.Note
import nl.lector.design.Pill
import nl.lector.design.ReadingFont
import nl.lector.design.ReadingTheme
import nl.lector.design.RowDivider
import nl.lector.design.Segmented
import nl.lector.design.Shape
import nl.lector.design.family
import nl.lector.design.toColor
import nl.lector.state.LectorState

/** Line spacing and margin presets, exactly the prototype's values. */
private val LeadOptions = listOf(1.38f to "Tight", 1.62f to "Normal", 1.92f to "Loose")
private val MarginOptions = listOf(14 to "Narrow", 24 to "Normal", 38 to "Wide")

@Composable
fun ColumnScope.AppearanceSheetBody(state: LectorState) {
    val c = LocalChrome.current

    Column(Modifier.padding(bottom = 24.dp)) {
        Eyebrow("Typeface", Modifier.padding(bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingFont.entries.forEach { f ->
                val on = state.font == f
                Column(
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 64.dp)
                        .clip(RoundedCornerShape(Shape.sm))
                        .background(if (on) c.tonal else Color.Transparent)
                        .then(
                            if (on) Modifier else Modifier.border(1.dp, c.muted, RoundedCornerShape(Shape.sm)),
                        )
                        .clickable { state.font = f; state.save() }
                        .padding(top = 10.dp, bottom = 8.dp, start = 6.dp, end = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        f.sample,
                        style = TextStyle(
                            fontFamily = f.family(), fontSize = 21.sp,
                            lineHeight = 21.sp, color = c.fg,
                        ),
                    )
                    Text(
                        f.label,
                        modifier = Modifier.padding(top = 6.dp),
                        style = TextStyle(
                            fontFamily = LocalFonts.current.body, fontSize = 10.5.sp,
                            color = if (on) c.accent else c.muted,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Note(
            "All three are bundled and openly licensed. OpenDyslexic ships with the app.",
        )
    }

    Column(Modifier.padding(bottom = 24.dp)) {
        Eyebrow("Size & weight", Modifier.padding(bottom = 10.dp))
        CardM3 {
            LectorRow("Text size") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StepperButton("−") {
                        state.size = (state.size - 1).coerceAtLeast(13); state.save()
                    }
                    Mono(
                        "${state.size} sp",
                        modifier = Modifier.widthIn(min = 52.dp),
                        size = 13.sp,
                        color = c.fg,
                    )
                    StepperButton("+") {
                        state.size = (state.size + 1).coerceAtMost(28); state.save()
                    }
                }
            }
            RowDivider()
            LectorRow(
                "Bold text",
                subtitle = "Heavier weight for the body",
                onClick = { state.bold = !state.bold; state.save() },
            ) { M3Switch(state.bold) }
            RowDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                RowLabel("Line spacing")
                Segmented(
                    options = LeadOptions,
                    selected = LeadOptions.firstOrNull { it.first == state.lead } ?: LeadOptions[1],
                    label = { it.second },
                    onSelect = { state.lead = it.first; state.save() },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            RowDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                RowLabel("Margins")
                Segmented(
                    options = MarginOptions,
                    selected = MarginOptions.firstOrNull { it.first == state.margin } ?: MarginOptions[1],
                    label = { it.second },
                    onSelect = { state.margin = it.first; state.save() },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    Column(Modifier.padding(bottom = 24.dp)) {
        Eyebrow("Theme", Modifier.padding(bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingTheme.entries.forEach { t ->
                val on = state.theme == t
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Shape.sm))
                        .border(
                            width = if (on) 2.dp else 1.dp,
                            color = if (on) c.accent else c.border,
                            shape = RoundedCornerShape(Shape.sm),
                        )
                        .clickable { state.theme = t; state.save() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(42.dp).background(t.bg.toColor()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Aa",
                            style = TextStyle(
                                fontFamily = LocalFonts.current.display,
                                fontSize = 16.sp, color = t.fg.toColor(),
                            ),
                        )
                    }
                    Text(
                        t.label,
                        modifier = Modifier.padding(vertical = 5.dp),
                        style = TextStyle(
                            fontFamily = LocalFonts.current.body, fontSize = 9.5.sp,
                            color = if (on) c.accent else c.muted,
                        ),
                    )
                }
            }
        }
    }

    Column {
        Eyebrow("Warmth · ${state.warmth}%", Modifier.padding(bottom = 10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Mono("Neutral", Modifier.width(34.dp), size = 10.sp)
            Box(Modifier.weight(1f)) {
                M3Slider(
                    value = state.warmth / 100f,
                    onValue = { state.warmth = (it * 100f).toInt(); state.save() },
                )
            }
            Mono("Amber", Modifier.width(34.dp), size = 10.sp)
        }
        Note(
            "Continuous, not presets. On the black theme it warms the text and leaves the " +
                "background at true black for OLED.",
        )
    }
}

/** Jumping to a chapter moves the reading position, not just the list selection. */
@Composable
fun ColumnScope.ContentsSheetBody(state: LectorState, onSelect: (Int) -> Unit) {
    val here = chapterIndexFor(state.pct)
    CardM3 {
        Toc.forEachIndexed { i, chapter ->
            if (i > 0) RowDivider()
            LectorRow(
                "${chapter.number}. ${chapter.title}",
                subtitle = "pp. ${chapter.pages}",
                subtitleMono = true,
                onClick = { onSelect(i) },
            ) { if (i == here) Pill("Here", on = true) }
        }
    }
}

/** The sleep timer's options, in minutes. */
private val SleepOptions = listOf(15, 30, 45, 60)

@Composable
fun ColumnScope.PlaybackSheetBody(state: LectorState, onVoices: () -> Unit) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Eyebrow("Speed · ${fmt1(state.rate)}×", Modifier.padding(bottom = 10.dp))
        M3Slider(
            value = (state.rate * 100f - 70f) / 130f,
            onValue = { state.rate = rateFromSlider(it); state.save() },
        )
    }

    CardM3 {
        ChevronRow("Voices", subtitle = state.voicesSummary(), onClick = onVoices)
        RowDivider()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RowLabel("Sleep timer")
                Mono(
                    state.sleepMinutesLeft?.let { "$it min left" } ?: "Off",
                    size = 13.5.sp,
                    color = if (state.sleepMinutesLeft != null) LocalChrome.current.accent
                    else LocalChrome.current.muted,
                )
            }
            Text(
                "Stops at the end of the current sentence",
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.body, fontSize = 12.5.sp,
                    color = LocalChrome.current.muted,
                ),
            )
            Segmented(
                options = listOf(0) + SleepOptions,
                selected = state.sleepMinutesLeft ?: 0,
                label = { if (it == 0) "Off" else "$it" },
                onSelect = { state.sleepMinutesLeft = it.takeIf { m -> m > 0 } },
            )
        }
    }
}

@Composable
internal fun RowLabel(text: String) {
    Text(
        text,
        style = TextStyle(
            fontFamily = LocalFonts.current.body, fontSize = 15.sp,
            fontWeight = FontWeight.Medium, color = LocalChrome.current.fg,
        ),
    )
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    val c = LocalChrome.current
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, c.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = TextStyle(
                fontFamily = LocalFonts.current.body, fontSize = 20.sp, color = c.fg,
            ),
        )
    }
}
