package nl.lector.data

/**
 * Sample reading content.
 *
 * This is the last hard-coded content in the app, and it is here because rendering
 * a real EPUB's text needs Readium (TECHNICALPRD §12, Spike B). The library itself
 * is real — see [LibrarySource] — so what remains fixed is the *page content* of an
 * opened book, not which books exist.
 *
 * Multatuli, *Max Havelaar* (1860): public domain, original spelling.
 */
val BookPages: List<List<List<String>>> = listOf(
    listOf(
        listOf(
            "Ik ben makelaar in koffi, en woon op de Lauriergracht, N° 37.",
            "Het is myn gewoonte niet, romans te schryven, of zulke dingen, en het heeft dan ook lang geduurd, voor ik er toe overging een paar riem papier extra te bestellen, en het werk aantevangen, dat gy, lieve lezer, zoo-even in de hand hebt genomen, en dat ge lezen moet als ge makelaar in koffi zyt, of als ge wat anders zyt.",
        ),
        listOf(
            "Niet alleen dat ik nooit iets schreef wat naar een roman geleek, maar ik houd er zelfs niet van, iets dergelyks te lezen, omdat ik een man van zaken ben.",
            "Ik vraag sedert jaren: waartoe dienen zulke dingen?",
        ),
    ),
    listOf(
        listOf(
            "Ik sta verbaasd over de onbeschaamdheid waarmede een dichter u een leugen durft opdringen, die hy zelf voelt en weet.",
            "Hy vertelt u van een kind dat in de wieg ligt te lachen, en dat de moeder het kust met tranen in de oogen.",
        ),
        listOf(
            "Ik heb daarvan nooit iets gezien, en het is dus niet waar.",
            "Wie zulke dingen schryft, bedriegt de menschen die hem gelooven, en dat is niet eerlyk.",
        ),
    ),
    listOf(
        listOf(
            "Ik ben makelaar in koffi, en woon op de Lauriergracht N° 37.",
            "Doch ik heb het onaangename van myn beroep zoo lang gedragen, dat ik het recht meen te hebben iets te zeggen over de waarheid.",
        ),
        listOf(
            "Want de waarheid is een zaak, en een zaak moet men behandelen als een zaak.",
            "Dat is myn stelling, en daarnaar handel ik, ook als ik schryf.",
        ),
    ),
)

data class Chapter(val number: String, val title: String, val pages: String)

/**
 * Which chapter a reading position falls in.
 *
 * Derived from the percentage rather than stored, so the reader, the Listening
 * screen and the Contents sheet cannot disagree about where you are. Readium
 * replaces this with the book's real spine positions.
 */
fun chapterIndexFor(pct: Float): Int =
    (pct * Toc.size).toInt().coerceIn(0, Toc.lastIndex)

fun chapterFor(pct: Float): Chapter = Toc[chapterIndexFor(pct)]

/** The reading percentage a chapter starts at — used when jumping from Contents. */
fun chapterStart(index: Int): Float = index.toFloat() / Toc.size

/** Placeholder table of contents; Readium supplies the real one per book. */
val Toc = listOf(
    Chapter("1", "Makelaar in koffie", "0–14"),
    Chapter("2", "Sjaalman", "15–31"),
    Chapter("3", "De Droogstoppels", "32–48"),
    Chapter("4", "Een brief", "49–66"),
    Chapter("5", "Lebak", "67–92"),
    Chapter("6", "Havelaar in Rangkas-Betoeng", "93–128"),
)

/**
 * Voices are packages, not a setting. Dutch and English ship with the app; more can
 * be downloaded or added from a file.
 *
 * Nothing in the interface asks which language a book is in — the engine picks the
 * voice for the text it is handed (HANDOFF §6.1). The runtime behind them is never
 * named in the UI either (§6.2): it is identical for every voice, so it cannot
 * differentiate anything.
 *
 * This catalogue becomes a real manifest once Spike A settles which models ship.
 */
data class Voice(
    val id: String,
    val name: String,
    val lang: String,
    val detail: String,
    /** Quality, 1..5, shown as the little bar meter. */
    val quality: Int,
)

val BundledVoices = listOf(
    Voice("nl", "Dutch", "nl", "Bundled · 63 MB", 4),
    Voice("en", "English", "en", "Bundled · 63 MB", 4),
)

val VoiceCatalogue = listOf(
    Voice("nl-hq", "Dutch — high quality", "nl", "108 MB", 5),
    Voice("en-hq", "English — high quality", "en", "108 MB", 5),
    Voice("de", "German", "de", "63 MB", 4),
    Voice("fr", "French", "fr", "63 MB", 4),
)

val VoiceSamples = mapOf(
    "nl" to "Ik ben makelaar in koffi, en woon op de Lauriergracht, N° 37.",
    "en" to "Call me Ishmael. Some years ago, never mind how long precisely.",
    "de" to "Der Wanderer steht am Rand des Waldes und lauscht dem Regen.",
    "fr" to "Le voyageur s’arrête au bord du bois et écoute la pluie.",
)

// ─── page model ───────────────────────────────────────────────────────────

/** One sentence, plus where its words start in the page-wide word index. */
data class SentenceSpan(val firstWord: Int, val words: List<String>) {
    val lastWord: Int get() = firstWord + words.size - 1
}

/** A rendered page: paragraphs of sentences, with a flat word count for the TTS walker. */
class PageModel(val paragraphs: List<List<SentenceSpan>>, val totalWords: Int) {
    val sentences: List<SentenceSpan> = paragraphs.flatten()
}

fun pageModel(index: Int): PageModel {
    val page = BookPages.getOrElse(index) { BookPages.first() }
    var w = 0
    val paragraphs = page.map { sentences ->
        sentences.map { sentence ->
            val words = sentence.split(" ")
            SentenceSpan(w, words).also { w += words.size }
        }
    }
    return PageModel(paragraphs, w)
}
