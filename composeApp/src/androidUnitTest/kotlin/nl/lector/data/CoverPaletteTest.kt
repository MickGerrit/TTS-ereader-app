package nl.lector.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Generated covers are what most of a real library looks like until Open Library
 * answers, so "they are all the same colour" is a visible defect, not a nitpick.
 */
class CoverPaletteTest {

    private fun book(title: String) =
        Book(id = title, title = title, author = "A", language = "EN", pages = 1, hasEmbeddedCover = false)

    private val titles = listOf(
        "Max Havelaar", "De kleine Johannes", "Moby-Dick", "Walden",
        "Frankenstein", "Van de koele meren des doods", "Walden Two",
        "The Odyssey", "Pride and Prejudice", "Ulysses", "Middlemarch", "Emma",
    )

    @Test
    fun `hues spread across the wheel rather than clustering`() {
        val quadrants = titles.map { (book(it).coverBackground.h / 90f).toInt() }.toSet()
        assertTrue(
            quadrants.size >= 3,
            "12 titles landed in only ${quadrants.size} quadrant(s) — covers will look identical",
        )
    }

    @Test
    fun `art marks are not all the same`() {
        val marks = titles.map { book(it).coverArt }.toSet()
        assertTrue(marks.size >= 3, "12 titles produced only ${marks.size} distinct marks")
    }

    @Test
    fun `palette is stable for the same title`() {
        assertEquals(book("Moby-Dick").coverBackground, book("Moby-Dick").coverBackground)
        assertEquals(book("Moby-Dick").coverArt, book("Moby-Dick").coverArt)
    }

    @Test
    fun `backgrounds stay dark and low-chroma so titles stay legible on them`() {
        titles.forEach {
            val bg = book(it).coverBackground
            assertTrue(bg.l in 0.20f..0.50f, "$it background lightness ${bg.l} is out of range")
            assertTrue(bg.c <= 0.10f, "$it background chroma ${bg.c} is too saturated")
        }
    }
}
