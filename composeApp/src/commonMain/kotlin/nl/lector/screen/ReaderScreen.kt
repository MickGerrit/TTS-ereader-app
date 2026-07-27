package nl.lector.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lector.design.IconBtn
import nl.lector.design.LectorIcons
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.ProgressBar
import nl.lector.design.backgroundAt
import nl.lector.design.consumePointer
import nl.lector.design.foregroundAt
import nl.lector.state.LectorState
import kotlin.math.roundToInt

/**
 * The reader's chrome, wrapped around whatever renders the book.
 *
 * The page itself is now a Readium WebView, supplied by the platform through
 * [surface] — the EPUB engine ships as parallel native toolkits, so the surface is
 * per-platform while all of this stays shared (TECHNICALPRD §1).
 *
 * Gestures are no longer handled here either: Readium owns the touch stream inside
 * the WebView, so the tap contract from HANDOFF §5 is implemented against its input
 * listener. What remains shared is the part that is design rather than engine.
 */
@Composable
fun ReaderScreen(
    state: LectorState,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onAppearance: () -> Unit,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    surface: @Composable () -> Unit,
) {
    val warmth = state.warmth / 100f
    val bg = state.theme.backgroundAt(warmth)
    val ink = state.theme.foregroundAt(warmth)

    Box(Modifier.fillMaxSize().background(bg)) {
        surface()
        ReaderTopChrome(state, ink, bg, onBack, onContents, onAppearance)
        ReaderBottomChrome(state, ink, onTogglePlay, onExpand)
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
                            val chapter = state.chapter?.title ?: "—"
                            MetaText("$chapter$speed", ink, Modifier.weight(1f, fill = false))
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
                .border(1.dp, if (on) c.accent else ink.copy(alpha = 0.24f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(15.dp), tint = if (on) c.onAccent else ink)
        }
    }
}

@Composable
private fun MetaText(text: String, ink: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = LocalFonts.current.mono, fontSize = 10.sp,
            letterSpacing = 0.5.sp, color = ink.copy(alpha = 0.65f),
        ),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}
