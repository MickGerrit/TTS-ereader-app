package nl.lector.data

/**
 * One entry from the open book's own navigation document.
 *
 * [progression] is where the entry starts in the whole publication, so the reader,
 * the Listening screen and the Contents sheet all answer "which chapter am I in"
 * from the same number. [locatorJson] is the engine's own position for the entry,
 * which is what a jump uses: landing on the chapter start rather than on a
 * percentage that happens to be nearby.
 */
data class Chapter(val title: String, val progression: Float, val locatorJson: String)

/**
 * Which entry a reading position falls in, or -1 when the book has no navigation
 * document (or has not been opened yet).
 *
 * ponytail: entries that share a start, several anchors into one XHTML file, all
 * resolve to the last of them, because a link's fragment has no progression until
 * the page is laid out. Right for one file per chapter, which is the common shape;
 * wrong for a whole book in one file. Match on the live Locator's href and
 * in-resource progression if that turns out to matter.
 */
fun List<Chapter>.indexAt(pct: Float): Int = indexOfLast { it.progression <= pct }

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
