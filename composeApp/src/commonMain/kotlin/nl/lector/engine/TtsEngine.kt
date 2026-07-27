package nl.lector.engine

import kotlinx.coroutines.delay

/**
 * Speaking, and reporting which word is being spoken.
 *
 * [language] is the EPUB's own declared language, not a user setting: the interface
 * never asks which language a book is in, the engine is simply handed the text and
 * what the file says it is (HANDOFF §6.1). Swapping the platform engine for
 * `sherpa-onnx` (TECHNICALPRD §12, Spike A) changes nothing above this interface.
 */
interface TtsEngine {
    /**
     * Null when the engine can speak [language]; otherwise a sentence explaining why
     * not, fit to show the reader. Checked before playback starts so a missing voice
     * is a message rather than silence.
     */
    suspend fun ensureReady(language: String?): String?

    /**
     * Speak [words] from index [from], invoking [onWord] as each word begins.
     * Suspends until the last word finishes; cancel the coroutine to stop.
     *
     * Returns null on success, or a reason to show the reader. Synthesis can fail
     * mid-book — a voice that turns out to need the network, for one — and a caller
     * that cannot tell success from failure will happily "read" a whole book in
     * silence at page-turn speed.
     */
    suspend fun speak(
        words: List<String>,
        from: Int,
        rate: Float,
        language: String?,
        onWord: (Int) -> Unit,
    ): String?

    /** Release engine resources. */
    fun shutdown() = Unit
}

/**
 * Silent stand-in for previews and tests: walks the words on the prototype's timing
 * so word sync, page auto-advance and the transport can be exercised without audio.
 */
class SimulatedTts : TtsEngine {
    override suspend fun ensureReady(language: String?): String? = null

    override suspend fun speak(
        words: List<String>,
        from: Int,
        rate: Float,
        language: String?,
        onWord: (Int) -> Unit,
    ): String? {
        for (i in from until words.size) {
            onWord(i)
            delay(((150 + words[i].length * 46) / rate).toLong())
        }
        return null
    }
}
