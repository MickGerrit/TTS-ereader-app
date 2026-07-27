package nl.lector.data

/**
 * Looking up a cover for a book that carries none (PRD §6.7).
 *
 * The only outbound request the app makes, and only when the reader has turned the
 * covers toggle on. The interface lives here so `commonMain` stays free of an HTTP
 * client; the implementation is per platform.
 */
interface CoverSource {
    /** Sends title and author, nothing else. */
    suspend fun fetch(book: Book): CoverResult
}

sealed interface CoverResult {
    /** Cached on disk at [path], ready to render without another request. */
    data class Found(val path: String) : CoverResult

    /** No match, no connection, or a broken image. [reason] is shown to the reader. */
    data class Missing(val reason: String) : CoverResult
}

/** Used by previews and tests, and by any platform with no lookup wired up. */
class NoCoverSource : CoverSource {
    override suspend fun fetch(book: Book): CoverResult =
        CoverResult.Missing("Cover lookup is not available in this build.")
}
