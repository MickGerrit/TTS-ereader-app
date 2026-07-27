package nl.lector.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
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
        known: Map<String, Book>,
        onProgress: (filesSeen: Int, booksFound: Int) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val treeUri = runCatching { Uri.parse(grant.locator) }.getOrNull()
            ?: return@withContext ScanResult(
                emptyList(), 0,
                error = "`${grant.label}` is no longer a folder Lector can open. Pick it again.",
            )
        val books = mutableListOf<Book>()
        var filesSeen = 0
        var reused = 0

        val queue = ArrayDeque<Pair<String, String>>()   // documentId to relative path
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext ScanResult(
                emptyList(), 0,
                error = "`${grant.label}` is no longer a folder Lector can open. Pick it again.",
            )
        queue += rootId to ""

        // The first query is the one that tells us whether the grant still holds. A
        // revoked permission or a folder that has been deleted both surface here, and
        // an empty shelf with no explanation is the worst way to report either.
        if (listChildren(treeUri, rootId) == null) {
            return@withContext ScanResult(
                emptyList(), 0,
                error = "Lector cannot read `${grant.label}`. The permission may have been " +
                    "revoked, or the folder may be gone. Choose the folder again in Settings.",
            )
        }

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (parentId, parentPath) = queue.removeFirst()
            // A folder that has become unreadable mid-walk is skipped, not fatal:
            // the rest of the shelf is still worth having.
            val entries = listChildren(treeUri, parentId).orEmpty()

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
                            val before = books.size
                            readBook(treeUri, entry, path, sidecarDirs, known)?.let { books += it }
                            if (books.size > before && books.last().stamp == known[books.last().id]?.stamp) {
                                reused++
                            }
                        }
                        onProgress(filesSeen, books.size)
                    }
                }
            }
        }

        ScanResult(books.sortedBy { it.title.lowercase() }, filesSeen, reused = reused)
    }

    private data class Entry(
        val id: String,
        val name: String,
        val isDirectory: Boolean,
        /** Size and modification time, joined. Empty when the provider withholds them. */
        val stamp: String = "",
    )

    /** Null when the folder could not be read at all, as opposed to being empty. */
    private fun listChildren(treeUri: Uri, parentId: String): List<Entry>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
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
                                stamp = "${c.getLong(3)}:${c.getLong(4)}",
                            ),
                        )
                    }
                }
            }
        }.getOrNull()
    }

    private fun readBook(
        treeUri: Uri,
        entry: Entry,
        path: String,
        sidecarDirs: Map<String, Entry>,
        known: Map<String, Book>,
    ): Book? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id)
        val id = stableHash(path).toString(16)
        val basename = entry.name.removeSuffix(".epub").removeSuffix(".EPUB")

        // Progress is read every time: it is one small file, and it is the whole
        // point of the sidecar that another device may have moved it.
        val progress = sidecarDirs[basename]
            ?.let { readSidecarProgress(treeUri, it) }
            ?: 0f

        // Unchanged file, cover still on disk: nothing in the zip can have moved, so
        // do not open it again (story 7.4). Every scan reparsing every book, cover
        // extraction included, is what made a large shelf slow (7.3).
        val previous = known[id]
        if (previous != null && entry.stamp.isNotEmpty() && previous.stamp == entry.stamp &&
            (previous.coverImagePath == null || File(previous.coverImagePath).exists())
        ) {
            return previous.copy(sidecarProgress = progress, locator = uri.toString())
        }

        val meta = readEpubMetadata { context.contentResolver.openInputStream(uri)!! } ?: return null

        // Either the book's own artwork, or one Open Library sent for it earlier —
        // both live in the same cache file, so a fetched cover survives a rescan.
        val coverPath = meta.coverEntry?.let { cacheCover(uri, it, id) } ?: cachedCover(id)

        return Book(
            // Path-derived, so progress survives a rescan but a moved file starts fresh.
            id = id,
            title = meta.title ?: basename,
            author = meta.author ?: "Unknown",
            language = meta.language?.take(2)?.uppercase().orEmpty(),
            pages = estimatePages(meta.contentBytes),
            // Only claim a cover if we actually got the bytes out.
            hasEmbeddedCover = meta.hasCover && coverPath != null,
            sidecarProgress = progress,
            locator = uri.toString(),
            coverImagePath = coverPath,
            stamp = entry.stamp,
        )
    }

    /**
     * Extract the cover once and keep it in the app cache.
     *
     * Reading it out of the zip on every recomposition would be absurd, and the cache
     * is disposable: a cleared cache just means the next scan extracts it again.
     */
    private fun cachedCover(id: String): String? =
        File(File(context.cacheDir, "covers"), "$id.img")
            .takeIf { it.exists() && it.length() > 0 }?.absolutePath

    private fun cacheCover(uri: Uri, entry: String, id: String): String? {
        val dir = File(context.cacheDir, "covers").apply { mkdirs() }
        val file = File(dir, "$id.img")
        if (file.exists() && file.length() > 0) return file.absolutePath

        val bytes = extractCoverImage({ context.contentResolver.openInputStream(uri)!! }, entry)
            ?: return null
        return runCatching {
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
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
            ?.firstOrNull { it.name.endsWith(".lua") } ?: return null
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
