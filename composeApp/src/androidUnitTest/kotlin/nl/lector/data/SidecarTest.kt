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

    @Test
    fun `the table is syntactically closed`() {
        val text = lua(0.42f)
        assertTrue(text.trimStart().startsWith("--"))
        assertTrue("return {" in text)
        assertTrue(text.trimEnd().endsWith("}"))
    }
}
