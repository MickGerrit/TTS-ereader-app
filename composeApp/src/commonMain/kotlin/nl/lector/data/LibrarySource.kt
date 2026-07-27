package nl.lector.data

/** A folder the reader has granted access to. */
data class FolderGrant(val locator: String, val label: String)

/** What a scan turned up, plus enough detail for the import screen to nararate it. */
data class ScanResult(
    val books: List<Book>,
    val filesSeen: Int,
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
 * The real implementation walks a granted folder and reads each EPUB's own metadata;
 * [SampleLibrary] stands in for previews and tests. Nothing above this interface
 * knows which one it has.
 */
interface LibrarySource {
    /**
     * Walk the granted folder recursively for EPUBs, skipping obvious non-books.
     * [onProgress] reports files seen and books found so the import screen can show
     * the scan happening rather than a spinner.
     */
    suspend fun scan(
        grant: FolderGrant,
        onProgress: (filesSeen: Int, booksFound: Int) -> Unit = { _, _ -> },
    ): ScanResult
}

/**
 * Fixed books for previews, tests and the emulator before a folder is granted.
 *
 * Public-domain titles only, so the sample library is legal to ship and recognisable
 * while developing. This is the *only* place book content is authored in the app.
 */
class SampleLibrary : LibrarySource {
    override suspend fun scan(
        grant: FolderGrant,
        onProgress: (Int, Int) -> Unit,
    ): ScanResult {
        SampleBooks.forEachIndexed { i, _ -> onProgress((i + 1) * 8, i + 1) }
        return ScanResult(SampleBooks, filesSeen = 47)
    }
}

val SampleBooks = listOf(
    Book("havelaar", "Max Havelaar", "Multatuli", "NL", 268, true, 0f),
    Book("johannes", "De kleine Johannes", "Frederik van Eeden", "NL", 184, true, 0.62f),
    Book("moby", "Moby-Dick", "Herman Melville", "EN", 624, true, 0.18f),
    Book("walden", "Walden", "Henry D. Thoreau", "EN", 312, true, 1.0f),
    Book("koele", "Van de koele meren des doods", "Frederik van Eeden", "NL", 296, false, 0f),
    Book("franken", "Frankenstein", "Mary Shelley", "EN", 280, false, 0f),
)
