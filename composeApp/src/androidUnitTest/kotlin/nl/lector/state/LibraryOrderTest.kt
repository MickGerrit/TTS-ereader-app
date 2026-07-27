package nl.lector.state

import nl.lector.data.Book
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shelf's three orders, and what happens to books nobody has opened yet. */
class LibraryOrderTest {

    private fun book(id: String, title: String, author: String) =
        Book(id, title, author, "EN", 100, hasEmbeddedCover = false)

    private fun shelf() = LectorState(MemoryPrefs()).apply {
        books += listOf(
            book("a", "Walden", "Thoreau"),
            book("b", "Moby-Dick", "Melville"),
            book("c", "Frankenstein", "Shelley"),
        )
    }

    @Test
    fun `title order is title order, regardless of what the scan returned`() {
        val state = shelf().apply { sort = Sort.Title }
        assertEquals(listOf("Frankenstein", "Moby-Dick", "Walden"), state.sortedBooks().map { it.title })
    }

    @Test
    fun `author order sorts by author, not by the title shown under it`() {
        val state = shelf().apply { sort = Sort.Author }
        assertEquals(listOf("Melville", "Shelley", "Thoreau"), state.sortedBooks().map { it.author })
    }

    @Test
    fun `recent order puts the last opened first and the never opened last`() {
        val state = shelf().apply { sort = Sort.Recent }
        state.openBook("b")
        state.openBook("a")
        assertEquals(
            listOf("Walden", "Moby-Dick", "Frankenstein"),
            state.sortedBooks().map { it.title },
            "last opened first, then earlier, then the untouched book by title",
        )
    }

    @Test
    fun `reopening a book moves it back to the front`() {
        val state = shelf().apply { sort = Sort.Recent }
        state.openBook("a")
        state.openBook("b")
        state.openBook("a")
        assertEquals("Walden", state.sortedBooks().first().title)
    }

    @Test
    fun `search results keep the chosen order`() {
        val state = shelf().apply { sort = Sort.Title; query = "n" }
        assertEquals(listOf("Frankenstein", "Walden"), state.matchingBooks().map { it.title })
    }
}
