package nl.lector.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The prototype's icons, kept as their original SVG path data.
 *
 * Compose ships an SVG path parser, so the icons stay one line each and can be
 * diffed against `mobile-android.html` by eye. Rects in the source SVGs are the
 * only thing rewritten, as equivalent rounded-rect paths.
 */
private fun stroked(name: String, viewBox: Float, width: Float, vararg d: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = viewBox, viewportHeight = viewBox,
    ).apply {
        d.forEach {
            addPath(
                pathData = PathParser().parsePathString(it).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun filled(name: String, viewBox: Float, vararg d: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = viewBox, viewportHeight = viewBox,
    ).apply {
        d.forEach {
            addPath(pathData = PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black))
        }
    }.build()

object LectorIcons {
    // ── top app bar / rows ────────────────────────────────────────────────
    val Back = stroked("back", 24f, 1.8f, "M19 12H5m6-7-7 7 7 7")
    val Sort = stroked("sort", 24f, 1.8f, "M4 7h16M6 12h12M9 17h6")
    val Contents = stroked("contents", 24f, 1.8f, "M4 6h16M4 12h16M4 18h10")
    val ReadAlong = stroked("readalong", 24f, 1.9f, "M4 7h16M4 12h16M4 17h10")
    val Chevron = stroked("chevron", 24f, 2f, "m9 5 7 7-7 7")
    val Search = stroked("search", 20f, 2f, "M15 9a6 6 0 1 1-12 0 6 6 0 1 1 12 0", "m13.5 13.5 4 4")
    val Refresh = stroked("refresh", 24f, 2.4f, "M20 12a8 8 0 1 1-2.3-5.6M20 4v5h-5")

    // ── navigation bar ────────────────────────────────────────────────────
    val Library = stroked(
        "library", 24f, 1.7f,
        "M4 5.5A1.5 1.5 0 0 1 5.5 4H9v16H5.5A1.5 1.5 0 0 1 4 18.5v-13ZM10.5 4H14v16h-3.5V4ZM16 4.8l3.2.9a1.5 1.5 0 0 1 1 1.9l-3.4 12.2-3.3-.9",
    )
    val Headphones = stroked(
        "headphones", 24f, 1.7f,
        "M4 14v-2a8 8 0 0 1 16 0v2",
        "M5 13h0a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h0a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
        "M19 13h0a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h0a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
    )
    val Settings = stroked(
        "settings", 24f, 1.7f,
        "M15 12a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
        "M12 3v2m0 14v2M3 12h2m14 0h2M5.6 5.6l1.4 1.4m10 10 1.4 1.4m0-12.8-1.4 1.4m-10 10L5.6 18.4",
    )

    /** Playback settings — the sliders glyph on the Listening screen. */
    val Tune = stroked(
        "tune", 24f, 1.8f,
        "M4 8h4M13 8h7M4 16h8M17 16h3",
        "M12.9 8a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 1 1 4.8 0",
        "M16.9 16a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 1 1 4.8 0",
    )

    /** Folder glyph on the import screen (viewBox is 20×16 in the source). */
    val Folder = filled(
        "folder", 20f,
        "M1 3.4A1.4 1.4 0 0 1 2.4 2h4.2l2 2.2h9A1.4 1.4 0 0 1 19 5.6v8.9A1.4 1.4 0 0 1 17.6 16H2.4A1.4 1.4 0 0 1 1 14.5V3.4Z",
    )

    // ── transport ─────────────────────────────────────────────────────────
    val Play = filled("play", 24f, "M8 5.5v13l11-6.5-11-6.5Z")
    val Pause = filled(
        "pause", 24f,
        "M8.2 5.5h1.2a1.2 1.2 0 0 1 1.2 1.2v10.6a1.2 1.2 0 0 1-1.2 1.2h-1.2a1.2 1.2 0 0 1-1.2-1.2V6.7a1.2 1.2 0 0 1 1.2-1.2z",
        "M14.6 5.5h1.2a1.2 1.2 0 0 1 1.2 1.2v10.6a1.2 1.2 0 0 1-1.2 1.2h-1.2a1.2 1.2 0 0 1-1.2-1.2V6.7a1.2 1.2 0 0 1 1.2-1.2z",
    )
    val PrevParagraph = filled("prevpara", 24f, "M18 6 9 12l9 6V6ZM7 6h2v12H7z")
    val PrevSentence = filled("prevsent", 24f, "M17 6 8 12l9 6V6Z")
    val NextSentence = filled("nextsent", 24f, "m7 6 9 6-9 6V6Z")
    val NextParagraph = filled("nextpara", 24f, "m6 6 9 6-9 6V6ZM15 6h2v12h-2z")
}
