package nl.lector.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import nl.lector.platform.rememberCoverImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Decorative cover art, drawn when a cover exists (embedded or fetched). */
enum class CoverArt { Canal, Wave, Grain, Plain }

/**
 * A cover for a scanned book, with the `GEN` / `OL` flag applied for you.
 *
 * Books that carry their own artwork show it. The generated placeholder, and the flag
 * that admits it is generated, are for the ones that do not (PRD §6.7).
 */
@Composable
fun BookCover(
    book: nl.lector.data.Book,
    coverFetched: Boolean,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onFetch: (() -> Unit)? = null,
    fetching: Boolean = false,
) {
    val artwork = rememberCoverImage(book.coverImagePath)

    // Real artwork needs none of the generated furniture: no mark, no scrim, no
    // title plate, no flag. The placeholder is what all of that exists for.
    if (artwork != null) {
        Image(
            bitmap = artwork,
            contentDescription = "${book.title} by ${book.author}",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(Shape.xs)),
        )
        return
    }

    val hasCover = book.hasEmbeddedCover || coverFetched
    Cover(
        title = book.title,
        author = book.author,
        bg = book.coverBackground,
        fg = book.coverForeground,
        art = book.coverArt,
        hasCover = hasCover,
        modifier = modifier,
        large = large,
        flag = when {
            book.hasEmbeddedCover -> null
            coverFetched -> "OL"
            else -> "GEN"
        },
        onFetch = if (!hasCover) onFetch else null,
        fetching = fetching,
    )
}

/**
 * A book cover. Real covers are not in the prototype's scope, so every cover here
 * is generated from the book's own two-colour palette plus one of four abstract
 * marks — which is also exactly what the app shows for a book with no cover
 * (PRD §6.7). The `GEN` / `OL` flag says which you are looking at, so a generated
 * placeholder never quietly passes as artwork.
 */
@Composable
fun Cover(
    title: String,
    author: String,
    bg: Oklch,
    fg: Oklch,
    art: CoverArt,
    hasCover: Boolean,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    flag: String? = null,
    onFetch: (() -> Unit)? = null,
    fetching: Boolean = false,
) {
    val cbg = bg.toColor()
    val cfg = fg.toColor()

    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(Shape.xs))
            .background(cbg),
    ) {
        if (hasCover) {
            Canvas(Modifier.fillMaxSize()) { drawCoverArt(art, cfg) }
            // Bottom scrim so the title stays legible over the art.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.58f to cbg.copy(alpha = 0.92f),
                            1f to cbg,
                        ),
                    ),
            )
        }

        // Spine shadow down the left edge.
        Box(
            Modifier
                .fillMaxHeight()
                .width(9.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent),
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 9.dp, vertical = 10.dp),
        ) {
            Text(
                title,
                style = TextStyle(
                    fontFamily = LocalFonts.current.display,
                    fontSize = if (large) 14.sp else 11.5.sp,
                    lineHeight = if (large) 16.24.sp else 13.34.sp,
                    fontWeight = FontWeight.Bold,
                    color = cfg,
                ),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Text(
                author.uppercase(),
                modifier = Modifier.padding(top = 5.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.mono,
                    fontSize = if (large) 8.5.sp else 8.sp,
                    letterSpacing = 0.64.sp,
                    color = cfg.copy(alpha = 0.72f),
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        // GEN until a cover is fetched, OL once Open Library answered.
        if (flag != null) {
            Text(
                flag,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(cfg.copy(alpha = 0.88f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = TextStyle(
                    fontFamily = LocalFonts.current.mono, fontSize = 7.5.sp,
                    letterSpacing = 0.75.sp, color = cbg,
                ),
            )
        }

        if (onFetch != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(cfg.copy(alpha = 0.92f))
                    .clickable(enabled = !fetching, onClick = onFetch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    LectorIcons.Refresh, "Fetch cover for $title",
                    Modifier.size(14.dp).then(if (fetching) Modifier.rotate(45f) else Modifier),
                    tint = cbg,
                )
            }
        }
    }
}

/** The four marks, transcribed from the prototype's inline SVGs (viewBox 60×90). */
private fun DrawScope.drawCoverArt(art: CoverArt, color: Color) {
    // Cover aspect is exactly 2:3, so the viewBox scales uniformly.
    val s = size.width / 60f
    scale(s, s, pivot = Offset.Zero) {
        when (art) {
            CoverArt.Canal -> {
                val houses = listOf(
                    listOf(4f, 28f, 9f, 34f), listOf(15f, 20f, 8f, 42f),
                    listOf(25f, 32f, 10f, 30f), listOf(37f, 24f, 7f, 38f),
                    listOf(46f, 34f, 10f, 28f),
                )
                houses.forEach { (x, y, w, h) ->
                    drawRect(color.copy(alpha = 0.5f), Offset(x, y), Size(w, h))
                }
                listOf(
                    listOf(0f, 62f, 60f, 2f), listOf(0f, 67f, 60f, 1.5f), listOf(0f, 72f, 60f, 1f),
                ).forEach { (x, y, w, h) ->
                    drawRect(color.copy(alpha = 0.22f), Offset(x, y), Size(w, h))
                }
            }

            CoverArt.Wave -> {
                listOf(30f, 39f, 48f, 57f, 66f).forEach { y ->
                    // "M-4 {y}q15-9 30 0t30 0" — a quad, then its smooth reflection.
                    val p = Path().apply {
                        moveTo(-4f, y)
                        quadraticTo(11f, y - 9f, 26f, y)
                        quadraticTo(41f, y + 9f, 56f, y)
                    }
                    drawPath(p, color.copy(alpha = 0.42f), style = Stroke(width = 1.1f))
                }
                drawCircle(color.copy(alpha = 0.3f), radius = 7f, center = Offset(42f, 20f))
            }

            CoverArt.Grain -> listOf(7f, 13f, 19f, 25f, 31f).forEach { r ->
                drawCircle(
                    color.copy(alpha = 0.34f), radius = r, center = Offset(30f, 38f),
                    style = Stroke(width = 1f),
                )
            }

            CoverArt.Plain -> Unit
        }
    }
}
