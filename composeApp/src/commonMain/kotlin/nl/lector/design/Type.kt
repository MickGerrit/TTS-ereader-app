package nl.lector.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.lector.res.Res
import nl.lector.res.atkinson_bold
import nl.lector.res.atkinson_regular
import nl.lector.res.literata_bold
import nl.lector.res.literata_regular
import nl.lector.res.literata_semibold
import nl.lector.res.opendyslexic_bold
import nl.lector.res.opendyslexic_regular
import org.jetbrains.compose.resources.Font

/**
 * Three roles (HANDOFF.md §2):
 *  - display: Literata. Headings, book titles, the wordmark, default reading face.
 *  - body:    system sans (Roboto on Android).
 *  - mono:    system mono, for every number, size, percentage and eyebrow label.
 *
 * The mono rule is load-bearing: it is what makes the interface read as an
 * instrument rather than an app. Resist putting figures in the body face.
 */
@Immutable
class Fonts(
    val display: FontFamily,
    val body: FontFamily,
    val mono: FontFamily,
    val readSerif: FontFamily,
    val readSans: FontFamily,
    val readDyslexic: FontFamily,
)

val LocalFonts = staticCompositionLocalOf<Fonts> { error("Fonts not provided") }

@Composable
fun rememberFonts(): Fonts {
    val literata = FontFamily(
        Font(Res.font.literata_regular, FontWeight.Normal),
        Font(Res.font.literata_semibold, FontWeight.SemiBold),
        Font(Res.font.literata_bold, FontWeight.Bold),
    )
    val atkinson = FontFamily(
        Font(Res.font.atkinson_regular, FontWeight.Normal),
        Font(Res.font.atkinson_bold, FontWeight.Bold),
    )
    val dyslexic = FontFamily(
        Font(Res.font.opendyslexic_regular, FontWeight.Normal),
        Font(Res.font.opendyslexic_bold, FontWeight.Bold),
    )
    return Fonts(
        display = literata,
        body = FontFamily.SansSerif,
        mono = FontFamily.Monospace,
        readSerif = literata,
        readSans = atkinson,
        readDyslexic = dyslexic,
    )
}

/** The three reading faces offered in the Appearance sheet (PRD §6.4). */
enum class ReadingFont(val label: String, val sample: String) {
    Serif("Literata", "Ag"),
    Sans("Atkinson", "Ag"),
    Dyslexic("OpenDyslexic", "Ag"),
}

@Composable
fun ReadingFont.family(): FontFamily = with(LocalFonts.current) {
    when (this@family) {
        ReadingFont.Serif -> readSerif
        ReadingFont.Sans -> readSans
        ReadingFont.Dyslexic -> readDyslexic
    }
}

/** `.eyebrow` — mono, 10.5px, wide tracking, uppercase, muted. */
@Composable
fun eyebrowStyle(color: androidx.compose.ui.graphics.Color = LocalChrome.current.muted) = TextStyle(
    fontFamily = LocalFonts.current.mono,
    fontSize = 10.5.sp,
    letterSpacing = 1.05.sp,
    color = color,
)

/** `.mono` — tabular figures for anything numeric. */
@Composable
fun monoStyle(
    size: androidx.compose.ui.unit.TextUnit = 12.5.sp,
    color: androidx.compose.ui.graphics.Color = LocalChrome.current.muted,
) = TextStyle(fontFamily = LocalFonts.current.mono, fontSize = size, color = color)
