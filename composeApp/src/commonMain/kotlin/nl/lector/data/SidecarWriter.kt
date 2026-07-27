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

/**
 * Update an existing sidecar in place, keeping every field we do not own.
 *
 * A real KOReader sidecar carries bookmarks, highlights, the last page, the font it
 * was read at and a dozen other things. Replacing the file with our four fields
 * would delete all of it — someone else's data, on their device, gone. So this is a
 * text edit, not a rewrite: the two keys we own are substituted where they already
 * are, or inserted directly after `return {` when they are absent, and every other
 * byte of the file survives untouched.
 *
 * ponytail: substitution over parsing. It holds because both keys are top-level
 * scalars written one per line, which is how KOReader emits them. A sidecar that
 * nested `percent_finished` inside another table would fool it. Reach for a real
 * Lua reader if we ever need to understand the rest of the file.
 */
fun mergeSidecarLua(
    existing: String?,
    progression: Float,
    locator: String?,
    title: String,
): String {
    val current = existing?.takeIf { it.contains("return") && it.contains("{") }
        ?: return sidecarLua(progression, locator, title)

    var merged = current.replaceKey("percent_finished", luaNumber(progression.coerceIn(0f, 1f)))
    if (locator != null) {
        merged = merged.replaceKey("lector_locator", "\"${luaEscape(locator)}\"")
    }
    return merged
}

/** Substitute a top-level `["key"] = value`, or insert one if the file has none. */
private fun String.replaceKey(key: String, value: String): String {
    val assignment = Regex("""\["$key"\]\s*=\s*[^,\n]*""")
    if (assignment.containsMatchIn(this)) {
        return assignment.replaceFirst(this, """["$key"] = $value""")
    }
    // Insert at the head of the table rather than hunting for its closing brace: a
    // duplicate key later in a Lua constructor would win, and there is none, because
    // we only get here when the key is absent.
    val open = indexOf('{', startIndex = indexOf("return"))
    if (open < 0) return this
    return substring(0, open + 1) + "\n    [\"$key\"] = $value," + substring(open + 1)
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
internal fun luaNumber(v: Float): String {
    val scaled = (v * 1_000_000).toLong()
    val whole = scaled / 1_000_000
    val frac = (scaled % 1_000_000).toString().padStart(6, '0')
    return "$whole.$frac"
}

internal fun luaEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

/**
 * One speakable fragment of a book, with where it sits in the publication.
 *
 * The engine hands these over already split at sensible boundaries, which is what
 * makes the reading highlight possible: [locatorJson] is a real position the
 * renderer can decorate, not an index into a string we invented.
 */
data class SpokenSegment(val text: String, val locatorJson: String)
