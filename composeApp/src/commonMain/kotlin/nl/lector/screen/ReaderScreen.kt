package nl.lector.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import nl.lector.data.PageModel
import nl.lector.data.chapterFor
import nl.lector.data.pageModel
import nl.lector.design.consumePointer
import nl.lector.design.IconBtn
import nl.lector.design.LectorIcons
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.ProgressBar
import nl.lector.design.backgroundAt
import nl.lector.design.family
import nl.lector.design.foregroundAt
import nl.lector.state.LectorState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The reading surface, and the chrome you reveal with a tap.
 *
 * Interaction contract (HANDOFF §5), all of it load-bearing:
 *  - tap the centre toggles chrome; tap the outer thirds turns the page
 *  - a horizontal swipe turns the page, and must not also fire the tap under it
 *  - hidden chrome is not nothing: a 2dp progress hairline stays, plus a small
 *    play glyph while a listening session is live
 *  - the hidden bar is click-through, or it would eat the centre tap
 */
@Composable
fun ReaderScreen(
    state: LectorState,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onAppearance: () -> Unit,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onTurnPage: (Int) -> Unit,
) {
    val c = LocalChrome.current
    val warmth = state.warmth / 100f
    val surface = state.theme.backgroundAt(warmth)
    val ink = state.theme.foregroundAt(warmth)
    var direction by remember { mutableIntStateOf(1) }

    val bg by animateColorAsState(surface, tween(300), label = "surface")
    val fg by animateColorAsState(ink, tween(300), label = "ink")

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            // One gesture handler for taps and swipes together. Deciding at pointer-up
            // is what stops a swipe that ends over a tap zone from also turning a
            // second page — the prototype needs an explicit `eatSwipe()` for this.
            .pointerInput(Unit) {
                val swipeMin = 45.dp.toPx()
                val verticalTolerance = 70.dp.toPx()
                awaitEachGesture {
                    // requireUnconsumed matters: the chrome consumes its own taps, and
                    // without this a tap on the back button or the scrubber would also
                    // be read as a tap on the page underneath it.
                    val first = awaitFirstDown(requireUnconsumed = true)
                    var last = first
                    var consumed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (change.isConsumed) consumed = true
                        last = change
                        if (!change.pressed) break
                    }
                    if (consumed) return@awaitEachGesture
                    val dx = last.position.x - first.position.x
                    val dy = last.position.y - first.position.y

                    if (abs(dx) > swipeMin && abs(dy) < verticalTolerance) {
                        direction = if (dx < 0) 1 else -1
                        onTurnPage(direction)
                    } else if (abs(dx) < swipeMin && abs(dy) < swipeMin) {
                        val third = size.width / 100f
                        when {
                            first.position.x < third * 28 -> { direction = -1; onTurnPage(-1) }
                            first.position.x > third * 72 -> { direction = 1; onTurnPage(1) }
                            else -> state.chromeHidden = !state.chromeHidden
                        }
                    }
                }
            },
    ) {
        AnimatedContent(
            targetState = state.page,
            transitionSpec = {
                val d = if (direction > 0) 1 else -1
                (slideInHorizontally(tween(260)) { it / 12 * d } + fadeIn(tween(260))) togetherWith
                    fadeOut(tween(120))
            },
            label = "page",
        ) { pageIndex ->
            PageContent(state, pageModel(pageIndex), pageIndex, fg)
        }

        ReaderTopChrome(state, fg, bg, onBack, onContents, onAppearance)
        ReaderBottomChrome(state, fg, onTogglePlay, onExpand)
    }
}

@Composable
private fun PageContent(state: LectorState, model: PageModel, pageIndex: Int, ink: Color) {
    val c = LocalChrome.current
    val family = state.font.family()

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(horizontal = state.margin.dp, vertical = 76.dp),
    ) {
        if (pageIndex == 0) {
            Text(
                "EERSTE HOOFDSTUK",
                modifier = Modifier.padding(bottom = 22.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.mono, fontSize = 10.5.sp,
                    letterSpacing = 1.47.sp, color = ink.copy(alpha = 0.5f),
                ),
            )
        }

        model.paragraphs.forEach { paragraph ->
            val text = buildAnnotatedString {
                var spokenRange: IntRange? = null
                paragraph.forEachIndexed { si, sentence ->
                    if (si > 0) append(" ")
                    val sentenceStart = length
                    sentence.words.forEachIndexed { wi, word ->
                        if (wi > 0) append(" ")
                        val wordStart = length
                        append(word)
                        if (sentence.firstWord + wi == state.word) {
                            spokenRange = wordStart until length
                        }
                    }
                    if (state.word in sentence.firstWord..sentence.lastWord) {
                        addStyle(
                            SpanStyle(background = c.accent.copy(alpha = 0.13f)),
                            sentenceStart, length,
                        )
                    }
                }
                // The spoken word paints over its sentence, so it is applied last.
                spokenRange?.let {
                    addStyle(
                        SpanStyle(background = c.accent.copy(alpha = 0.78f), color = c.onAccent),
                        it.first, it.last + 1,
                    )
                }
            }

            Text(
                text,
                modifier = Modifier.padding(bottom = (state.size * 0.85f).dp),
                style = TextStyle(
                    fontFamily = family,
                    fontSize = state.size.sp,
                    lineHeight = (state.size * state.lead).sp,
                    fontWeight = if (state.bold) FontWeight.SemiBold else FontWeight.Normal,
                    color = ink,
                ),
            )
        }
    }
}

@Composable
private fun ReaderTopChrome(
    state: LectorState,
    ink: Color,
    surface: Color,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onAppearance: () -> Unit,
) {
    AnimatedVisibility(
        visible = !state.chromeHidden,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(surface)
                .consumePointer()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconBtn(LectorIcons.Back, "Back to library", onBack, tint = ink)
            Text(
                state.book?.title.orEmpty(),
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.display, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = ink,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            IconBtn(LectorIcons.Contents, "Contents", onContents, tint = ink)
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onAppearance),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Aa",
                    style = TextStyle(
                        fontFamily = LocalFonts.current.display, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, color = ink,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomChrome(
    state: LectorState,
    ink: Color,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
) {
    val c = LocalChrome.current
    val hidden = state.chromeHidden
    val book = state.book ?: return
    val p = state.pct

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                // Hidden chrome stays click-through so the centre tap still reaches
                // the page; only the play glyph takes pointer events (HANDOFF §5).
                .consumePointer(enabled = !hidden)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = if (hidden) 28.dp else 24.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (hidden) 9.dp else 12.dp),
            ) {
                // While hidden, only the glyph takes pointer events — everything
                // else must stay click-through so the centre tap still lands.
                if (!hidden || state.listening) {
                    PlayGlyph(state, hidden, ink, onTogglePlay)
                }

                Column(Modifier.weight(1f)) {
                    ProgressBar(
                        fraction = p,
                        height = if (hidden) 2.dp else 4.dp,
                        trackColor = ink.copy(alpha = if (hidden) 0.10f else 0.16f),
                        fillColor = if (hidden) c.accent.copy(alpha = 0.55f) else c.accent,
                    )
                    AnimatedVisibility(!hidden, enter = fadeIn(), exit = fadeOut()) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).height(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Short by necessity: this line shares its width with the
                            // page counter and two 44dp buttons. Speed only appears
                            // when it is off the default.
                            val speed = if (state.rate != 1f) " · ${fmt1(state.rate)}×" else ""
                            MetaText("Ch. ${chapterFor(p).number}$speed", ink)
                            MetaText(
                                "${fmt1(p * 100)}% · p. ${(p * book.pages).roundToInt().coerceAtLeast(1)}/${book.pages}",
                                ink,
                            )
                        }
                    }
                }

                // The way back to the full player exists only while a session does.
                if (!hidden && state.listening) {
                    IconBtn(
                        LectorIcons.Headphones, "Open the full player", onExpand,
                        Modifier.size(44.dp), tint = ink.copy(alpha = 0.7f), iconSize = 19.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayGlyph(state: LectorState, hidden: Boolean, ink: Color, onClick: () -> Unit) {
    val c = LocalChrome.current
    val on = state.ttsOn
    val icon = if (on) LectorIcons.Pause else LectorIcons.Play
    val label = if (on) "Pause" else "Listen to this book"

    if (hidden) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(12.dp), tint = ink.copy(alpha = 0.34f))
        }
    } else {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (on) c.accent else Color.Transparent)
                .border(
                    1.dp,
                    if (on) c.accent else ink.copy(alpha = 0.24f),
                    CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(15.dp), tint = if (on) c.onAccent else ink)
        }
    }
}

@Composable
private fun MetaText(text: String, ink: Color) {
    Text(
        text,
        style = TextStyle(
            fontFamily = LocalFonts.current.mono, fontSize = 10.sp,
            letterSpacing = 0.5.sp, color = ink.copy(alpha = 0.65f),
        ),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}
