package nl.lector.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * The real library: a recursive walk of the folder the reader granted, via the
 * Storage Access Framework.
 *
 * SAF is what lets the app write sibling `.sdr` sidecars without ever requesting
 * all-files access (PRD §6.1, §6.8). Queried through [DocumentsContract] rather than
 * `DocumentFile`, which issues one query per file and turns a large shelf into a
 * visibly slow scan.
 */
class SafLibrarySource(private val context: Context) : LibrarySource {

    override suspend fun scan(
        grant: FolderGrant,
        onProgress: (filesSeen: Int, booksFound: Int) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(grant.locator)
        val books = mutableListOf<Book>()
        var filesSeen = 0

        val queue = ArrayDeque<Pair<String, String>>()   // documentId to relative path
        queue += DocumentsContract.getTreeDocumentId(treeUri) to ""

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (parentId, parentPath) = queue.removeFirst()
            val entries = listChildren(treeUri, parentId)

            val sidecarDirs = entries.filter { it.isDirectory && it.name.endsWith(".sdr") }
                .associateBy { it.name.removeSuffix(".sdr") }

            entries.forEach { entry ->
                val path = if (parentPath.isEmpty()) entry.name else "$parentPath/${entry.name}"
                when {
                    entry.isDirectory -> {
                        // Skip hidden folders and KOReader's own sidecar directories.
                        if (!entry.name.startsWith(".") && !entry.name.endsWith(".sdr")) {
                            queue += entry.id to path
                        }
                    }

                    else -> {
                        filesSeen++
                        if (entry.name.endsWith(".epub", ignoreCase = true)) {
                            readBook(treeUri, entry, path, sidecarDirs)?.let { books += it }
                        }
                        onProgress(filesSeen, books.size)
                    }
                }
            }
        }

        ScanResult(books.sortedBy { it.title.lowercase() }, filesSeen)
    }

    private data class Entry(val id: String, val name: String, val isDirectory: Boolean)

    private fun listChildren(treeUri: Uri, parentId: String): List<Entry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        // A provider may return a row with no id or name; skip it
                        // rather than abandoning the rest of the directory.
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        add(
                            Entry(
                                id = id,
                                name = name,
                                isDirectory = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            ),
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    private fun readBook(
        treeUri: Uri,
        entry: Entry,
        path: String,
        sidecarDirs: Map<String, Entry>,
    ): Book? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id)
        val meta = readEpubMetadata { context.contentResolver.openInputStream(uri)!! } ?: return null

        val basename = entry.name.removeSuffix(".epub").removeSuffix(".EPUB")
        val progress = sidecarDirs[basename]
            ?.let { readSidecarProgress(treeUri, it) }
            ?: 0f

        return Book(
            // Path-derived, so progress survives a rescan but a moved file starts fresh.
            id = stableHash(path).toString(16),
            title = meta.title ?: basename,
            author = meta.author ?: "Unknown",
            language = meta.language?.take(2)?.uppercase().orEmpty(),
            pages = estimatePages(meta.contentBytes),
            hasEmbeddedCover = meta.hasCover,
            sidecarProgress = progress,
            locator = uri.toString(),
        )
    }

    /**
     * KOReader's `metadata.epub.lua` is a Lua table. We read one field out of it and
     * write one field back, which a regex covers honestly; a Lua parser would be a
     * dependency bought for a single number.
     *
     * ponytail: regex over one known key. Use a Lua reader if we ever need the
     * bookmarks or highlights out of this file too.
     */
    private fun readSidecarProgress(treeUri: Uri, sidecarDir: Entry): Float? {
        val meta = listChildren(treeUri, sidecarDir.id)
            .firstOrNull { it.name.endsWith(".lua") } ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, meta.id)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.reader().readText()
                Regex("""\["percent_finished"\]\s*=\s*([0-9.]+)""")
                    .find(text)?.groupValues?.get(1)?.toFloatOrNull()
            }
        }.getOrNull()
    }
}

/**
 * Rough page count from the markup size, until Readium paginates for real.
 *
 * ~2 KB of XHTML per printed page, which lands within a few percent on the public
 * domain EPUBs tested. It only feeds the "p. 63/268" readout, and Spike B replaces it.
 */
internal fun estimatePages(contentBytes: Long): Int =
    (contentBytes / 2000).toInt().coerceAtLeast(1)
