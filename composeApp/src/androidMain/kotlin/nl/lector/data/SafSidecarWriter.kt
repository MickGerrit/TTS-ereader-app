package nl.lector.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the sidecar through the Storage Access Framework.
 *
 * This is why the folder grant asks for write access: creating `<book>.sdr/` beside
 * the EPUB is the whole KOReader convention, and doing it through SAF means never
 * requesting all-files permission (PRD §6.1).
 */
class SafSidecarWriter(private val context: Context) : SidecarWriter {

    override suspend fun write(book: Book, progression: Float, locator: String?): String? =
        withContext(Dispatchers.IO) {
            val bookUri = runCatching { Uri.parse(book.locator) }.getOrNull()
                ?: return@withContext "No file location for ${book.title}."

            val parent = parentOf(bookUri)
                ?: return@withContext "Could not find the folder holding ${book.title}."

            val basename = displayName(bookUri)?.removeSuffix(".epub")?.removeSuffix(".EPUB")
                ?: book.title

            runCatching {
                val sidecarDir = findOrCreateDirectory(parent, "$basename.sdr")
                    ?: return@withContext "Could not create $basename.sdr — is the folder writable?"

                val file = findOrCreateFile(sidecarDir, "metadata.epub.lua")
                    ?: return@withContext "Could not create the sidecar file."

                // "wt" truncates. Without it a shorter document leaves trailing bytes
                // from the previous write and the Lua no longer parses.
                context.contentResolver.openOutputStream(file, "wt")?.use { out ->
                    out.write(sidecarLua(progression, locator, book.title).toByteArray())
                } ?: return@withContext "Could not open the sidecar for writing."

                null
            }.getOrElse { "Writing the sidecar failed: ${it.message}" }
        }

    private fun parentOf(child: Uri): Uri? {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(child) }.getOrNull()
            ?: return null
        val documentId = runCatching { DocumentsContract.getDocumentId(child) }.getOrNull()
            ?: return null
        val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = treeId)
        return DocumentsContract.buildDocumentUriUsingTree(child, parentId)
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** SAF has no "create if absent", so look first — creating twice makes `name (1)`. */
    private fun findOrCreateDirectory(parent: Uri, name: String): Uri? =
        findChild(parent, name) ?: runCatching {
            DocumentsContract.createDocument(
                context.contentResolver, parent,
                DocumentsContract.Document.MIME_TYPE_DIR, name,
            )
        }.getOrNull()

    /**
     * Create the file with the name KOReader expects, exactly.
     *
     * Not `text/plain`: the storage provider appends that MIME type's canonical
     * extension, producing `metadata.epub.lua.txt`, which KOReader will never look
     * for. `application/octet-stream` has no canonical extension, so the name we ask
     * for is the name on disk.
     */
    private fun findOrCreateFile(parent: Uri, name: String): Uri? =
        findChild(parent, name) ?: runCatching {
            DocumentsContract.createDocument(
                context.contentResolver, parent, "application/octet-stream", name,
            )?.takeIf { displayName(it) == name }
        }.getOrNull()

    private fun findChild(parent: Uri, name: String): Uri? {
        val parentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrNull()
            ?: return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
        return context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return@use DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0))
                }
            }
            null
        }
    }
}
