package nl.lector.data

import android.util.Log
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

private const val Tag = "LectorScan"

/**
 * What an EPUB says about itself.
 *
 * Read straight out of the package document. This is deliberately *not* Readium:
 * the library view needs a title, an author and whether a cover exists, and a single
 * pass over the zip answers all three in a few milliseconds. Readium comes in when we
 * need to render and paginate (TECHNICALPRD §12, Spike B), and can replace this
 * without the library screen noticing.
 *
 * Parsed with JAXP rather than `android.util.Xml` so the same code runs in a plain
 * JVM unit test — see `EpubMetadataTest`.
 */
data class EpubMetadata(
    val title: String?,
    val author: String?,
    val language: String?,
    val hasCover: Boolean,
    /** Bytes of markup in the spine, for the page estimate. */
    val contentBytes: Long,
)

/**
 * Single streaming pass over the zip.
 *
 * EPUB requires the package document to be findable via `META-INF/container.xml` but
 * does not require any particular entry order, so a stream that has already gone past
 * the OPF cannot go back. Rather than open the file twice, this collects every
 * candidate OPF on the way through and then picks the one container.xml named.
 */
fun readEpubMetadata(open: () -> InputStream): EpubMetadata? {
    var rootfilePath: String? = null
    val opfCandidates = LinkedHashMap<String, ByteArray>()
    var contentBytes = 0L

    try {
        ZipInputStream(open().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name.equals("META-INF/container.xml", ignoreCase = true) ->
                        rootfilePath = parseContainer(zip.readBytes())

                    name.endsWith(".opf", ignoreCase = true) ->
                        opfCandidates[name] = zip.readBytes()

                    name.endsWith(".xhtml", true) || name.endsWith(".html", true) ||
                        name.endsWith(".htm", true) -> contentBytes += entry.size.coerceAtLeast(0)
                }
            }
        }
    } catch (e: Exception) {
        // Not a readable EPUB. The scanner skips it, but a silently empty library is
        // the worst possible symptom, so say why in the log.
        Log.w(Tag, "not a readable zip", e)
        return null
    }

    val opf = rootfilePath?.let { opfCandidates[it] }
        ?: opfCandidates.values.firstOrNull()
        ?: run {
            Log.w(Tag, "no package document found (candidates=${opfCandidates.keys})")
            return null
        }

    return runCatching { parseOpf(opf, contentBytes) }
        .onFailure { Log.w(Tag, "could not parse package document", it) }
        .getOrNull()
}

/**
 * These files come from the reader's own folder, but "their own files" is not the
 * same as "trusted input" — an EPUB is a zip anyone can author. External entities
 * stay off so a crafted package document cannot read the rest of the device.
 */
private fun parse(bytes: ByteArray): Document =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        // Android's parser and the JVM's do not expose the same feature set, and an
        // unsupported one throws. Hardening is best-effort; failing to disable a
        // feature must not stop the file being read.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
    }.newDocumentBuilder().parse(bytes.inputStream())

/** `<rootfile full-path="OEBPS/content.opf" .../>` */
private fun parseContainer(bytes: ByteArray): String? = runCatching {
    parse(bytes).getElementsByTagName("rootfile").item(0)
        ?.let { (it as Element).getAttribute("full-path") }
        ?.ifBlank { null }
}.getOrNull()

private fun parseOpf(bytes: ByteArray, contentBytes: Long): EpubMetadata {
    val doc = parse(bytes)

    // Namespace-unaware parsing, so `dc:title` and a default-namespaced `title`
    // both have to be looked for by literal tag name.
    fun firstText(local: String): String? =
        listOf("dc:$local", local)
            .firstNotNullOfOrNull { doc.getElementsByTagName(it).item(0)?.textContent }
            ?.trim()?.ifBlank { null }

    val items = doc.getElementsByTagName("item")
    val manifestIds = mutableSetOf<String>()
    var hasCoverProperty = false
    for (i in 0 until items.length) {
        val item = items.item(i) as? Element ?: continue
        item.getAttribute("id")?.ifBlank { null }?.let { manifestIds += it }
        // EPUB 3 declares the cover on the manifest item itself.
        if (item.getAttribute("properties").orEmpty().contains("cover-image")) {
            hasCoverProperty = true
        }
    }

    // EPUB 2 instead points at a manifest id from <meta name="cover" content="…">.
    val metas = doc.getElementsByTagName("meta")
    var coverMetaId: String? = null
    for (i in 0 until metas.length) {
        val meta = metas.item(i) as? Element ?: continue
        if (meta.getAttribute("name").equals("cover", ignoreCase = true)) {
            coverMetaId = meta.getAttribute("content")?.ifBlank { null }
        }
    }

    return EpubMetadata(
        title = firstText("title"),
        author = firstText("creator"),
        language = firstText("language"),
        hasCover = hasCoverProperty || (coverMetaId != null && coverMetaId in manifestIds),
        contentBytes = contentBytes,
    )
}
