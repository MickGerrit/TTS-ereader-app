package nl.lector.data

/** A folder the reader has granted access to. */
data class FolderGrant(val locator: String, val label: String)

/** What a scan turned up, plus enough detail for the import screen to nararate it. */
data class ScanResult(
    val books: List<Book>,
    val filesSeen: Int,
    /**
     * Why the folder could not be read: the grant was revoked, the card was
     * unmounted, the folder was deleted. Null on a scan that reached the files,
     * including one that found nothing.
     */
    val error: String? = null,
    /** Files whose stamp was unchanged, so they were not reparsed. */
    val reused: Int = 0,
) {
    val withEmbeddedCover: Int get() = books.count { it.hasEmbeddedCover }
    val withoutCover: Int get() = books.size - withEmbeddedCover

    /** "3 NL, 3 EN" — the language split, most common first. */
    val languageSummary: String
        get() = books.groupingBy { it.language.ifBlank { "??" } }.eachCount()
            .entries.sortedByDescending { it.value }
            .joinToString(", ") { "${it.value} ${it.key}" }
}

/**
 * Where the library comes from.
 *
 * The implementation walks a granted folder and reads each EPUB's own metadata. No
 * book content is authored in the app: the library is whatever is in the folder.
 */
interface LibrarySource {
    /**
     * Walk the granted folder recursively for EPUBs, skipping obvious non-books.
     * [onProgress] reports files seen and books found so the import screen can show
     * the scan happening rather than a spinner.
     *
     * @param known what the last scan found, by book id. A file whose stamp is
     *   unchanged is taken from here rather than opened again — the difference
     *   between a rescan of a large shelf costing seconds and costing minutes.
     */
    suspend fun scan(
        grant: FolderGrant,
        known: Map<String, Book> = emptyMap(),
        onProgress: (filesSeen: Int, booksFound: Int) -> Unit = { _, _ -> },
    ): ScanResult
}

/** Finds nothing. For previews and tests, where no folder has been granted. */
class NoLibrary : LibrarySource {
    override suspend fun scan(
        grant: FolderGrant,
        known: Map<String, Book>,
        onProgress: (Int, Int) -> Unit,
    ) = ScanResult(emptyList(), filesSeen = 0)
}
