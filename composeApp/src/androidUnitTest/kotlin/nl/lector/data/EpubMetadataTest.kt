package nl.lector.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scanner reads real files off the reader's disk, so the parser has to survive
 * the shapes real EPUBs actually come in — EPUB 2 and 3 declare covers differently,
 * and entry order is not guaranteed.
 */
class EpubMetadataTest {

    private fun epub(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray) = readEpubMetadata { bytes.inputStream() }

    private val container = """
        <?xml version="1.0"?>
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
          <rootfiles><rootfile full-path="OEBPS/content.opf"
            media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    private fun opf(extra: String = "", manifest: String = "") = """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Max Havelaar</dc:title>
            <dc:creator>Multatuli</dc:creator>
            <dc:language>nl</dc:language>
            $extra
          </metadata>
          <manifest>$manifest</manifest>
        </package>
    """.trimIndent()

    @Test
    fun `reads title author and language`() {
        val meta = read(
            epub(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to opf(),
            ),
        )
        assertEquals("Max Havelaar", meta?.title)
        assertEquals("Multatuli", meta?.author)
        assertEquals("nl", meta?.language)
    }

    @Test
    fun `epub 3 cover-image property counts as a cover`() {
        val meta = read(
            epub(
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to opf(
                    manifest = """<item id="c" href="c.jpg" properties="cover-image"
                        media-type="image/jpeg"/>""",
                ),
            ),
        )
        assertTrue(meta!!.hasCover)
    }

    @Test
    fun `epub 2 meta name=cover counts only when the id is in the manifest`() {
        fun build(manifest: String) = read(
            epub(
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to opf(
                    extra = """<meta name="cover" content="cover-img"/>""",
                    manifest = manifest,
                ),
            ),
        )
        assertTrue(build("""<item id="cover-img" href="c.jpg" media-type="image/jpeg"/>""")!!.hasCover)
        // A dangling pointer is not a cover.
        assertTrue(!build("""<item id="something-else" href="a.xhtml"/>""")!!.hasCover)
    }

    @Test
    fun `no cover declared means no cover`() {
        val meta = read(
            epub("META-INF/container.xml" to container, "OEBPS/content.opf" to opf()),
        )
        assertTrue(!meta!!.hasCover)
    }

    /** The OPF can appear before container.xml in the zip; order must not matter. */
    @Test
    fun `finds the package document regardless of entry order`() {
        val meta = read(
            epub(
                "OEBPS/content.opf" to opf(),
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to container,
            ),
        )
        assertEquals("Max Havelaar", meta?.title)
    }

    @Test
    fun `falls back to the only opf when container xml is missing`() {
        val meta = read(epub("OEBPS/content.opf" to opf()))
        assertEquals("Max Havelaar", meta?.title)
    }

    @Test
    fun `a file that is not a zip is skipped rather than thrown`() {
        assertNull(read("this is a plain text file, not an epub".toByteArray()))
    }

    @Test
    fun `a zip with no package document is skipped`() {
        assertNull(read(epub("readme.txt" to "hello")))
    }

    @Test
    fun `page estimate scales with content and never returns zero`() {
        assertEquals(1, estimatePages(0))
        assertEquals(1, estimatePages(500))
        assertEquals(268, estimatePages(268 * 2000L))
    }
}
