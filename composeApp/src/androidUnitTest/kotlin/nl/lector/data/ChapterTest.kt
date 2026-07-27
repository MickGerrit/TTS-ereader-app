package nl.lector.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reader chrome, the Listening screen and the Contents sheet all render "where
 * am I" from [indexAt]. If it disagrees with itself, three screens disagree.
 */
class ChapterTest {

    private fun chapters(vararg starts: Float) =
        starts.mapIndexed { i, p -> Chapter("Chapter ${i + 1}", p, """{"href":"$i"}""") }

    private val book = chapters(0f, 0.12f, 0.4f, 0.75f)

    @Test
    fun `a chapter's own start resolves back to that chapter`() {
        book.indices.forEach { i ->
            assertEquals(i, book.indexAt(book[i].progression), "chapter $i did not round-trip")
        }
    }

    @Test
    fun `a position inside a chapter resolves to the chapter it started in`() {
        assertEquals(0, book.indexAt(0.05f))
        assertEquals(1, book.indexAt(0.39f))
        assertEquals(2, book.indexAt(0.74f))
        assertEquals(3, book.indexAt(1f))
    }

    @Test
    fun `a book with no navigation document reports no chapter rather than chapter one`() {
        assertEquals(-1, emptyList<Chapter>().indexAt(0.5f))
    }

    @Test
    fun `chapters never go backwards as the position advances`() {
        var last = -1
        var pct = 0f
        while (pct <= 1f) {
            val here = book.indexAt(pct)
            assert(here >= last) { "chapter went backwards at $pct" }
            last = here
            pct += 0.01f
        }
    }
}
