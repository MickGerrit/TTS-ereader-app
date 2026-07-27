package nl.lector.data

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The sidecar is read by *another program* — KOReader, on another device. A file that
 * does not parse there loses reading progress silently, which is the one failure this
 * feature exists to prevent.
 */
class SidecarTest {

    private fun lua(progression: Float, locator: String? = null, title: String = "Max Havelaar") =
        sidecarLua(progression, locator, title)

    @Test
    fun `percent_finished is written as a plain decimal Lua can parse`() {
        // Not 1.0E-4: Lua accepts it, but KOReader's own writer never emits it and
        // small progressions are exactly where a float formatter reaches for it.
        val text = lua(0.0001f)
        assertTrue("""["percent_finished"] = 0.000100""" in text, text)
        assertTrue("E" !in text && "e-" !in text, "scientific notation leaked in: $text")
    }

    @Test
    fun `progression is clamped into range`() {
        assertTrue("""= 1.000000""" in lua(2f))
        assertTrue("""= 0.000000""" in lua(-1f))
    }

    @Test
    fun `our own position is namespaced so nobody mistakes it for KOReader's`() {
        val text = lua(0.5f, locator = """{"href":"ch1.xhtml"}""")
        assertTrue("lector_locator" in text)
        // The interoperable field stays exactly what KOReader expects.
        assertTrue("""["percent_finished"] = 0.500000""" in text)
    }

    @Test
    fun `quotes and backslashes in a title cannot break the file`() {
        val text = lua(0.5f, title = """He said "hi" \ then left""")
        assertTrue("""\"hi\"""" in text, text)
        assertTrue("""\\""" in text, text)
        // Every opening quote on the title line has a matching close.
        val titleLine = text.lines().first { "[\"title\"]" in it }
        val unescaped = Regex("""(?<!\\)"""").findAll(titleLine).count()
        assertTrue(unescaped == 4, "unbalanced quoting in: $titleLine")
    }

    /**
     * A real KOReader sidecar, trimmed. The fields below are someone else's work:
     * losing them is the failure mode this whole epic is careful about.
     */
    private val koreader = """
        -- we can read Lua syntax here!
        return {
            ["bookmarks"] = {
                [1] = {
                    ["notes"] = "Chapter 4",
                    ["page"] = "/body/DocFragment[6]/body/p[3].0",
                },
            },
            ["doc_pages"] = 268,
            ["highlight"] = {},
            ["percent_finished"] = 0.180000,
            ["stats"] = {
                ["title"] = "Max Havelaar",
            },
        }
    """.trimIndent()

    @Test
    fun `merging keeps every field KOReader wrote`() {
        val merged = mergeSidecarLua(koreader, 0.62f, null, "Max Havelaar")
        assertTrue("bookmarks" in merged, "bookmarks were dropped")
        assertTrue("""["notes"] = "Chapter 4"""" in merged, "a bookmark's note was dropped")
        assertTrue("""["doc_pages"] = 268""" in merged)
        assertTrue("""["stats"]""" in merged)
    }

    @Test
    fun `merging updates the one field we own`() {
        val merged = mergeSidecarLua(koreader, 0.62f, null, "Max Havelaar")
        assertTrue("""["percent_finished"] = 0.620000""" in merged, merged)
        assertTrue("0.180000" !in merged, "the old progress survived: $merged")
    }

    @Test
    fun `our locator is added to a file that has none, once`() {
        val once = mergeSidecarLua(koreader, 0.62f, """{"href":"ch1"}""", "Max Havelaar")
        val twice = mergeSidecarLua(once, 0.7f, """{"href":"ch2"}""", "Max Havelaar")
        assertTrue(twice.split("lector_locator").size == 2, "duplicated key: $twice")
        assertTrue("ch2" in twice && "ch1" !in twice)
        assertTrue("""["percent_finished"] = 0.700000""" in twice)
        assertTrue("bookmarks" in twice)
    }

    @Test
    fun `a missing or unreadable sidecar falls back to writing our own`() {
        assertTrue("percent_finished" in mergeSidecarLua(null, 0.5f, null, "T"))
        assertTrue("percent_finished" in mergeSidecarLua("", 0.5f, null, "T"))
        assertTrue("percent_finished" in mergeSidecarLua("not lua at all", 0.5f, null, "T"))
    }

    @Test
    fun `the table is syntactically closed`() {
        val text = lua(0.42f)
        assertTrue(text.trimStart().startsWith("--"))
        assertTrue("return {" in text)
        assertTrue(text.trimEnd().endsWith("}"))
    }
}
