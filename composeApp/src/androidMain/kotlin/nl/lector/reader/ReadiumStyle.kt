package nl.lector.reader

import androidx.compose.ui.graphics.toArgb
import nl.lector.design.ReadingFont
import nl.lector.design.ReadingTheme
import nl.lector.design.backgroundAt
import nl.lector.design.foregroundAt
import nl.lector.state.LectorState
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

/**
 * Lector's design tokens, expressed as Readium preferences.
 *
 * This is the answer to Spike B's real question. Readium ships three theme presets
 * (LIGHT / DARK / SEPIA) which would not cover four reading themes, let alone a
 * continuous warmth slider — but `backgroundColor` and `textColor` accept arbitrary
 * values and override the preset. So the whole token system in HANDOFF §3, warmth
 * ceilings included, goes through the public API with no custom CSS injection.
 *
 * The colours are still computed by our own Oklch code; Readium only receives the
 * resolved sRGB values.
 */
fun lectorPreferences(state: LectorState): EpubPreferences {
    val warmth = state.warmth / 100f
    val theme = state.theme

    return EpubPreferences(
        backgroundColor = Color(theme.backgroundAt(warmth).toArgb()),
        textColor = Color(theme.foregroundAt(warmth).toArgb()),
        // The preset only sets Readium's own defaults; our two colours win. It still
        // matters for things we do not override, like selection and link colours.
        theme = if (theme.isDarkSurface) Theme.DARK else Theme.LIGHT,

        fontFamily = state.font.readiumFamily,
        // Readium sizes are multipliers on its own base, not absolute sp.
        fontSize = state.size / BaseFontSize,
        fontWeight = if (state.bold) 1.5 else 1.0,
        lineHeight = state.lead.toDouble(),
        pageMargins = state.margin / BaseMargin,

        // Paginated, one column: page turns are the default (PRD §6.3).
        scroll = false,
        columnCount = ColumnCount.ONE,

        // Without this the book's own stylesheet keeps winning and the reader's
        // typography settings only half apply.
        publisherStyles = false,
    )
}

/** Readium's default body size in points; our `size` is expressed against it. */
private const val BaseFontSize = 16.0

/** Readium's page margin unit; our 14/24/38dp presets map onto multiples of it. */
private const val BaseMargin = 20.0

private val ReadingFont.readiumFamily: FontFamily
    get() = when (this) {
        ReadingFont.Serif -> LiterataFamily
        ReadingFont.Sans -> AtkinsonFamily
        ReadingFont.Dyslexic -> OpenDyslexicFamily
    }

val LiterataFamily = FontFamily("Literata")
val AtkinsonFamily = FontFamily("Atkinson Hyperlegible")
val OpenDyslexicFamily = FontFamily("OpenDyslexic")

/**
 * The navigator configuration: the three bundled faces, declared to Readium's CSS.
 *
 * The files live in `androidMain/assets/fonts/` because the navigator serves them to
 * its own WebView over an internal HTTP server; Compose loads the same faces from
 * `composeResources` for the chrome. Two copies of each file is the cost of the
 * reading surface being a WebView rather than Compose text.
 */
fun lectorNavigatorConfiguration(): EpubNavigatorFragment.Configuration =
    EpubNavigatorFragment.Configuration {
        // Patterns for what the internal server is allowed to expose.
        servedAssets = listOf("fonts/.*")

        declareFamily(LiterataFamily, "literata_regular.ttf", "literata_bold.ttf")
        declareFamily(AtkinsonFamily, "atkinson_regular.ttf", "atkinson_bold.ttf")
        declareFamily(OpenDyslexicFamily, "opendyslexic_regular.otf", "opendyslexic_bold.otf")
    }

private fun EpubNavigatorFragment.Configuration.declareFamily(
    family: FontFamily,
    regular: String,
    bold: String,
) = addFontFamilyDeclaration(family) {
    addFontFace {
        addSource("fonts/$regular")
        setFontWeight(FontWeight.NORMAL)
    }
    addFontFace {
        addSource("fonts/$bold")
        setFontWeight(FontWeight.BOLD)
    }
}
