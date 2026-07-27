package nl.lector.reader

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Opening a book with Readium.
 *
 * [AssetRetriever] takes a `ContentResolver`, which means it reads a SAF
 * `content://` URI directly — the same URI the scanner already stores on each
 * [nl.lector.data.Book]. No copying the file into app storage first, so the
 * "your books stay in your own folder" promise (PRD §2) survives.
 */
sealed interface OpenResult {
    data object Loading : OpenResult
    data class Opened(val publication: Publication) : OpenResult
    data class Failed(val reason: String) : OpenResult
}

@Composable
fun rememberPublication(locator: String): OpenResult {
    val context = LocalContext.current
    return produceState<OpenResult>(OpenResult.Loading, locator) {
        value = openPublication(context, locator)
    }.value
}

private suspend fun openPublication(context: Context, locator: String): OpenResult =
    withContext(Dispatchers.IO) {
        val url = Uri.parse(locator).toAbsoluteUrl()
            ?: return@withContext OpenResult.Failed("Not a readable location: $locator")

        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val opener = PublicationOpener(
            DefaultPublicationParser(
                context = context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                // No PDF support in v1 (PRD §3.2), so no document factory to supply.
                pdfFactory = null,
            ),
        )

        val asset = assetRetriever.retrieve(url).getOrNull()
            ?: return@withContext OpenResult.Failed("Could not open the file. Has the folder permission been revoked?")

        val publication = opener.open(asset, allowUserInteraction = false).getOrNull()
            ?: return@withContext OpenResult.Failed("Not a readable EPUB.")

        OpenResult.Opened(publication)
    }
