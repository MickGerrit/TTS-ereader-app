package nl.lector.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The only network code in the app (PRD §6.7).
 *
 * Two plain GETs against Open Library's public API: one search for the book's cover
 * id, one for the image itself. `HttpURLConnection` rather than an HTTP client
 * dependency, because two requests do not justify one.
 *
 * The covers land in the same cache directory the scanner extracts embedded covers
 * to, under the same name, so a fetched cover survives a rescan and a cleared cache
 * simply means the placeholder comes back.
 */
class OpenLibraryCovers(private val context: Context) : CoverSource {

    override suspend fun fetch(book: Book): CoverResult = withContext(Dispatchers.IO) {
        val query = buildString {
            append("https://openlibrary.org/search.json?limit=1&fields=cover_i&title=")
            append(encode(book.title))
            // "Unknown" is the scanner's placeholder, not an author; sending it would
            // only narrow the search to nothing.
            if (book.author.isNotBlank() && book.author != "Unknown") {
                append("&author=").append(encode(book.author))
            }
        }

        val json = get(query)
            ?: return@withContext CoverResult.Missing(
                "Could not reach Open Library. Check the connection; the cover stays generated.",
            )

        val coverId = runCatching {
            JSONObject(String(json)).optJSONArray("docs")
                ?.optJSONObject(0)?.optInt("cover_i", 0) ?: 0
        }.getOrDefault(0)

        if (coverId <= 0) {
            return@withContext CoverResult.Missing(
                "Open Library has no cover for *${book.title}*.",
            )
        }

        val image = get("https://covers.openlibrary.org/b/id/$coverId-L.jpg")
        // Open Library answers a missing image with a 1-pixel placeholder rather
        // than a 404, so size is what tells us whether we actually got artwork.
        if (image == null || image.size < 2000) {
            return@withContext CoverResult.Missing(
                "Open Library returned no usable image for *${book.title}*.",
            )
        }

        val dir = File(context.cacheDir, "covers").apply { mkdirs() }
        val file = File(dir, "${book.id}.img")
        runCatching { file.writeBytes(image) }.getOrElse {
            return@withContext CoverResult.Missing("Could not write the cover to the cache.")
        }
        CoverResult.Found(file.absolutePath)
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Null on any failure: no connection, a redirect loop, a server error. */
    private fun get(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Lector/0.1 (ereader; contact via app store listing)")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
