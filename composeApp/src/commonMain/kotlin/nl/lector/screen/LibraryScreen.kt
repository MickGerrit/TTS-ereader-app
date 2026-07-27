package nl.lector.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lector.data.Book
import nl.lector.design.BookCover
import nl.lector.design.Eyebrow
import nl.lector.design.IconBtn
import nl.lector.design.LectorIcons
import nl.lector.design.LocalChrome
import nl.lector.design.LocalFonts
import nl.lector.design.Mono
import nl.lector.design.Note
import nl.lector.design.ProgressBar
import nl.lector.design.Shape
import nl.lector.design.Wordmark
import nl.lector.design.LargeTopBar
import nl.lector.state.LectorState
import nl.lector.state.Screen
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The library has no refresh button. It scans when the app opens, and again when
 * you pull down — the two moments a reader would actually expect it (HANDOFF §5).
 */
private const val PullThreshold = 90f

@Composable
fun LibraryScreen(
    state: LectorState,
    onOpenBook: (String) -> Unit,
    onToggleListen: () -> Unit,
    onScan: () -> Unit,
    onFetchCover: (String) -> Unit,
) {
    val c = LocalChrome.current
    val scroll = rememberScrollState()
    var pull by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val nested = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Swallow upward drag to unwind the indicator before the list moves.
                if (available.y < 0 && pull > 0f) {
                    val used = min(-available.y, pull)
                    pull -= used
                    return Offset(0f, -used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0 && scroll.value == 0 && !state.scanning) {
                    pull += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull > PullThreshold && !state.scanning) onScan()
                pull = 0f
                return Velocity.Zero
            }
        }
    }

    val indicatorHeight by animateDpAsState(
        when {
            state.scanning -> 44.dp
            pull > 0f -> min(52f, pull * 0.5f).dp
            else -> 0.dp
        },
        label = "ptr",
    )

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .background(c.bg)
                .nestedScroll(nested)
                .verticalScroll(scroll),
        ) {
            // pull-to-scan indicator
            Row(
                Modifier.fillMaxWidth().height(indicatorHeight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (indicatorHeight > 0.dp) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (state.scanning) c.accent else c.border),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            state.scanning -> "SCANNING…"
                            pull > PullThreshold -> "RELEASE TO SCAN"
                            else -> "PULL TO SCAN"
                        },
                        style = TextStyle(
                            fontFamily = LocalFonts.current.mono, fontSize = 10.sp,
                            letterSpacing = 0.9.sp, color = c.muted,
                        ),
                    )
                }
            }

            LargeTopBar("Library") {
                Wordmark(size = 17.sp, modifier = Modifier.padding(start = 20.dp))
                Spacer(Modifier.weight(1f))
                IconBtn(LectorIcons.Sort, "Sort", {
                    state.show(
                        "Sorted by *recently read*. Title and author are the other options.",
                        "Change",
                    )
                })
            }

            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                SearchField(
                    query = state.query,
                    onQuery = { state.query = it },
                    modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
                )

                if (state.books.isEmpty()) {
                    EmptyLibrary(state)
                    return@Column
                }

                val matches = state.matchingBooks()
                if (state.query.isNotBlank()) {
                    Eyebrow(
                        "${matches.size} match${if (matches.size == 1) "" else "es"} · “${state.query}”",
                        Modifier.padding(bottom = 14.dp),
                    )
                    if (matches.isEmpty()) {
                        Note("No book in ${state.folder?.label ?: "your library"} matches that.")
                    }
                    Shelf(state, matches, onOpenBook, onFetchCover)
                    Spacer(Modifier.height(8.dp))
                    return@Column
                }

                Eyebrow("Continue reading", Modifier.padding(bottom = 10.dp))
                ContinueCard(state, onOpenBook, onToggleListen)

                Row(
                    Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Eyebrow("All books · ${state.books.size}")
                    Eyebrow("Recently read")
                }

                Shelf(state, state.books, onOpenBook, onFetchCover)

                Note(
                    "Reading from ${state.folder?.label ?: "no folder"} · " +
                        "${state.books.size} EPUBs · scanned ${state.lastScan}",
                )
            }
        }
    }
}

/** The 3-up grid, shared by the full shelf and the search results. */
@Composable
private fun Shelf(
    state: LectorState,
    books: List<Book>,
    onOpenBook: (String) -> Unit,
    onFetchCover: (String) -> Unit,
) {
    books.chunked(3).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            row.forEach { b ->
                Box(Modifier.weight(1f)) { BookCard(state, b, onOpenBook, onFetchCover) }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Search over the scanned library. Filters title and author as you type. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalChrome.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(c.fill)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(LectorIcons.Search, null, Modifier.size(18.dp), tint = c.muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    "Search title or author",
                    style = TextStyle(
                        fontFamily = LocalFonts.current.body, fontSize = 15.sp, color = c.muted,
                    ),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = LocalFonts.current.body, fontSize = 15.sp, color = c.fg,
                ),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "×",
                    style = TextStyle(
                        fontFamily = LocalFonts.current.body, fontSize = 20.sp, color = c.muted,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    state: LectorState,
    onOpenBook: (String) -> Unit,
    onToggleListen: () -> Unit,
) {
    val c = LocalChrome.current
    val book = state.book ?: return
    val p = state.pct
    val page = (p * book.pages).roundToInt().coerceAtLeast(1)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Shape.lg))
            .background(c.tonal)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.weight(1f).clickable { onOpenBook(book.id) },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BookCover(book, state.fetched[book.id] == true, Modifier.width(70.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(
                    if (p > 0f) "In progress" else "Not started",
                    Modifier.padding(bottom = 6.dp),
                    color = c.accent,
                )
                Text(
                    book.title,
                    style = TextStyle(
                        fontFamily = LocalFonts.current.display, fontSize = 17.sp,
                        lineHeight = 20.74.sp, fontWeight = FontWeight.SemiBold, color = c.fg,
                    ),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    book.author,
                    modifier = Modifier.padding(top = 3.dp),
                    style = TextStyle(
                        fontFamily = LocalFonts.current.body, fontSize = 13.sp, color = c.muted,
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
                ProgressBar(p, Modifier.padding(top = 12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Mono("${fmt1(p * 100)}% · p. $page", size = 10.5.sp)
                    Mono(if (p > 0f) "ch. 1" else "${book.pages} pages", size = 10.5.sp)
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.CenterVertically)
                .size(48.dp)
                .clip(CircleShape)
                .background(c.accent)
                .clickable(onClick = onToggleListen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.ttsOn) LectorIcons.Pause else LectorIcons.Play,
                if (state.ttsOn) "Pause listening" else "Listen to this book",
                Modifier.size(18.dp), tint = c.onAccent,
            )
        }
    }
}

@Composable
private fun BookCard(
    state: LectorState,
    book: Book,
    onOpenBook: (String) -> Unit,
    onFetchCover: (String) -> Unit,
) {
    val c = LocalChrome.current
    val p = state.pctOf(book.id)

    Column(Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }) {
        BookCover(
            book = book,
            coverFetched = state.fetched[book.id] == true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            onFetch = { onFetchCover(book.id) },
            fetching = state.fetchingCover == book.id,
        )
        Text(
            book.title,
            modifier = Modifier.heightIn(min = 32.5.dp),
            style = TextStyle(
                fontFamily = LocalFonts.current.body, fontSize = 12.5.sp,
                lineHeight = 16.25.sp, fontWeight = FontWeight.Medium, color = c.fg,
            ),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            book.author,
            modifier = Modifier.padding(top = 2.dp),
            style = TextStyle(fontFamily = LocalFonts.current.body, fontSize = 11.sp, color = c.muted),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            when {
                p >= 1f -> "Finished"
                p > 0f -> "${fmt1(p * 100)}%"
                else -> "Not started"
            },
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(
                fontFamily = LocalFonts.current.mono, fontSize = 9.5.sp,
                letterSpacing = 0.38.sp, color = if (p > 0f) c.accent else c.muted,
            ),
        )
    }
}

/**
 * The granted folder held no readable EPUBs.
 *
 * Not in the prototype (HANDOFF §7 lists it as undesigned), but reachable the moment
 * the library is real, so it says plainly what happened and what to do next rather
 * than showing an empty shelf.
 */
@Composable
private fun EmptyLibrary(state: LectorState) {
    val c = LocalChrome.current
    Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
        Text(
            "Nothing to read yet.",
            style = TextStyle(
                fontFamily = LocalFonts.current.display, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, color = c.fg,
            ),
        )
        Note(
            "Lector found no EPUB files in ${state.folder?.label ?: "the chosen folder"}. " +
                "Drop some books in and pull down to scan again, or pick a different folder " +
                "in Settings.",
        )
    }
}

/** One decimal place, without pulling in a formatting dependency. */
fun fmt1(v: Float): String {
    val scaled = (v * 10f).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}
