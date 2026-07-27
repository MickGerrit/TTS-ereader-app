package nl.lector.data

/**
 * Chapters, voices and the page model used to live here alongside three hard-coded
 * pages of *Max Havelaar*. The sample text is gone: the reader renders the real
 * EPUB through Readium, and the speech engine reads the real content.
 *
 * What is left is the voice catalogue, which becomes a real manifest once Spike A
 * settles which models ship, and a placeholder table of contents until Readium's own
 * navigation document is wired to the Contents sheet.
 */
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
