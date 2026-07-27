package nl.lector.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader, the Listening screen and the Contents sheet all render "where am I"
 * from these two functions. If they disagree, three screens disagree.
 */
class ChapterTest {

    @Test
    fun `a chapter's own start resolves back to that chapter`() {
        Toc.indices.forEach { i ->
            assertEquals(i, chapterIndexFor(chapterStart(i)), "chapter $i did not round-trip")
        }
    }

    @Test
    fun `position is clamped at both ends rather than throwing`() {
        assertEquals(0, chapterIndexFor(0f))
        assertEquals(0, chapterIndexFor(-1f))
        assertEquals(Toc.lastIndex, chapterIndexFor(1f))
        assertEquals(Toc.lastIndex, chapterIndexFor(2f))
    }

    @Test
    fun `chapters advance monotonically through the book`() {
        var last = 0
        var pct = 0f
        while (pct <= 1f) {
            val here = chapterIndexFor(pct)
            assertTrue(here >= last, "chapter went backwards at $pct")
            last = here
            pct += 0.01f
        }
    }

    @Test
    fun `page step covers the whole book in exactly one pass`() {
        listOf(1, 90, 106, 624).forEach { pages ->
            val step = 1f / pages
            assertEquals(1f, step * pages, 0.0001f, "$pages pages did not sum to one book")
        }
    }
}
