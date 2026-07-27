package nl.lector.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lector.design.BookCover
import nl.lector.design.Eyebrow
import nl.lector.design.IconBtn
import nl.lector.design.LectorIcons
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.Mono
import nl.lector.design.ProgressBar
import nl.lector.design.Shape
import nl.lector.state.LectorState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Listening is a destination, not a drawer (HANDOFF §6.3).
 *
 * Cover, progress and transport only. Speed, voices and the sleep timer live one
 * swipe up, in the Playback sheet — and there is no stop button, because pause is
 * the transport and leaving keeps your position.
 */
@Composable
fun ListenScreen(
    state: LectorState,
    onReadAlong: () -> Unit,
    onPlayback: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekSentence: (Int) -> Unit,
    onTurnPage: (Int) -> Unit,
) {
    val c = LocalChrome.current
    val book = state.book ?: return
    val p = state.pct

    val totalMin = (book.pages * 1.4f).roundToInt()
    val done = (totalMin * p).roundToInt()
    val left = totalMin - done

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            // Swipe up anywhere raises Playback. Buttons are unaffected: this
            // observes the gesture without consuming it.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var last = first
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        last = change
                        if (!change.pressed) break
                    }
                    val dy = last.position.y - first.position.y
                    val dx = last.position.x - first.position.x
                    if (dy < -50.dp.toPx() && abs(dx) < 60.dp.toPx()) onPlayback()
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.fill)
                    .clickable(onClick = onReadAlong)
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(LectorIcons.ReadAlong, null, Modifier.size(18.dp), tint = c.fg)
                Text(
                    "Read along",
                    style = TextStyle(
                        fontFamily = LocalFonts.current.body, fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = c.fg,
                    ),
                )
            }
            IconBtn(LectorIcons.Tune, "Playback settings", onPlayback)
        }

        // Centred in the space left over, but still scrollable if the cover, the
        // transport and a long title cannot all fit at once.
        BoxWithConstraints(Modifier.weight(1f)) {
            val available = maxHeight
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .defaultMinSize(minHeight = available)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Eyebrow("Listening · on-device")

            BookCover(
                book = book,
                coverFetched = state.fetched[book.id] == true,
                modifier = Modifier.width(150.dp).padding(top = 20.dp, bottom = 22.dp),
                large = true,
            )

            Text(
                book.title,
                style = TextStyle(
                    fontFamily = LocalFonts.current.display, fontSize = 23.sp,
                    lineHeight = 26.68.sp, letterSpacing = (-0.58).sp, color = c.fg,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                listOfNotNull(book.author, state.chapter?.title).joinToString(" · "),
                modifier = Modifier.padding(top = 5.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.body, fontSize = 13.5.sp, color = c.muted,
                ),
                textAlign = TextAlign.Center,
            )

            Column(Modifier.fillMaxWidth().padding(top = 34.dp)) {
                ProgressBar(p)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Mono("${done / 60}:${(done % 60).toString().padStart(2, '0')}", size = 11.sp)
                    Mono(
                        "${left / 60} h ${(left % 60).toString().padStart(2, '0')} m left",
                        size = 11.sp,
                    )
                }
            }

            Row(
                Modifier.padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TransportButton(LectorIcons.PrevParagraph, "Previous paragraph") { onTurnPage(-1) }
                TransportButton(LectorIcons.PrevSentence, "Previous sentence") { onSeekSentence(-1) }

                Box(
                    Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(Shape.xl))
                        .background(c.accent)
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.ttsOn) LectorIcons.Pause else LectorIcons.Play,
                        if (state.ttsOn) "Pause" else "Play",
                        Modifier.size(26.dp), tint = c.onAccent,
                    )
                }

                TransportButton(LectorIcons.NextSentence, "Next sentence") { onSeekSentence(1) }
                TransportButton(LectorIcons.NextParagraph, "Next chapter") { onTurnPage(1) }
            }

                // Only present while a timer is running, so it reads as state rather
                // than another control competing with the transport.
                state.sleepMinutesLeft?.let { minutes ->
                    Mono(
                        "SLEEP · $minutes MIN",
                        Modifier.padding(top = 20.dp),
                        size = 10.sp,
                        color = c.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalChrome.current
    Box(
        Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = c.fg)
    }
}
