package nl.lector.data

/**
 * Writes reading progress back next to the book, KOReader-style.
 *
 * KOReader keeps `<book>.sdr/metadata.epub.lua` beside each file; syncing the folder
 * carries progress between devices with no server involved (PRD §6.8). Reading these
 * already works — see the scanner — and this is the other half.
 */
interface SidecarWriter {
    /**
     * @param progression 0..1, written as KOReader's `percent_finished`.
     * @param locator our engine-native position, kept in a namespaced field so
     *   KOReader ignores it and we do not pretend it means anything to crengine.
     * @return null on success, or a reason the write failed.
     */
    suspend fun write(book: Book, progression: Float, locator: String?): String?
}

/** Used by previews and tests; keeps the last write in memory. */
class NoOpSidecarWriter : SidecarWriter {
    var lastWrite: Triple<String, Float, String?>? = null
        private set

    override suspend fun write(book: Book, progression: Float, locator: String?): String? {
        lastWrite = Triple(book.id, progression, locator)
        return null
    }
}

/**
 * The Lua table KOReader expects.
 *
 * Deliberately minimal: the fields we actually own. KOReader merges rather than
 * replaces on its side, and writing keys we do not understand would risk clobbering
 * bookmarks and highlights we never read.
 *
 * `percent_finished` is the interoperable part and the only thing Settings promises
 * (HANDOFF §6.6). `lector_locator` is namespaced precisely so nobody mistakes it for
 * a position crengine could use.
 */
fun sidecarLua(progression: Float, locator: String?, title: String): String {
    val pct = progression.coerceIn(0f, 1f)
    return buildString {
        appendLine("-- Written by Lector. KOReader-compatible at percentage level.")
        appendLine("return {")
        appendLine("""    ["percent_finished"] = ${luaNumber(pct)},""")
        appendLine("""    ["title"] = "${luaEscape(title)}",""")
        if (locator != null) {
            appendLine("""    ["lector_locator"] = "${luaEscape(locator)}",""")
        }
        appendLine("}")
    }
}

/** Fixed notation: Lua reads `1.0E-4` as a syntax error in this position. */
private fun luaNumber(v: Float): String {
    val scaled = (v * 1_000_000).toLong()
    val whole = scaled / 1_000_000
    val frac = (scaled % 1_000_000).toString().padStart(6, '0')
    return "$whole.$frac"
}

private fun luaEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

/**
 * One speakable fragment of a book, with where it sits in the publication.
 *
 * The engine hands these over already split at sensible boundaries, which is what
 * makes the reading highlight possible: [locatorJson] is a real position the
 * renderer can decorate, not an index into a string we invented.
 */
data class SpokenSegment(val text: String, val locatorJson: String)
