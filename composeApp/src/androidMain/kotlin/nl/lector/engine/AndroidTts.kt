package nl.lector.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Real speech, via the engine already on the device.
 *
 * This is not the end state — PRD §6.6 wants offline *neural* TTS, which is
 * `sherpa-onnx` and Spike A. It is the honest interim: it actually speaks, it
 * reports word boundaries so the highlight is driven by real audio rather than a
 * timer, and it exercises every playback path the neural engine will inherit.
 */
class AndroidTts(context: Context) : TtsEngine {

    private val ready = CompletableDeferred<Boolean>()

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    override suspend fun ensureReady(language: String?): String? {
        if (!ready.await()) return "No text-to-speech engine is installed on this device."
        return when (tts.setLanguage(localeFor(language))) {
            TextToSpeech.LANG_MISSING_DATA ->
                "Voice data for ${localeFor(language).displayLanguage} is not installed. " +
                    "Add it in the system text-to-speech settings."

            TextToSpeech.LANG_NOT_SUPPORTED ->
                "${localeFor(language).displayLanguage} is not supported by the installed " +
                    "speech engine."

            else -> null
        }
    }

    override suspend fun speak(
        words: List<String>,
        from: Int,
        rate: Float,
        language: String?,
        onWord: (Int) -> Unit,
    ): String? {
        if (!ready.await()) return "No text-to-speech engine is installed on this device."
        if (from >= words.size) return null
        tts.setLanguage(localeFor(language))
        tts.setSpeechRate(rate)

        // One utterance for the whole remaining page, plus the character offset each
        // word starts at — that is what turns the engine's range callbacks back into
        // word indices the reader can highlight.
        val text = StringBuilder()
        val wordStarts = IntArray(words.size - from)
        for (i in from until words.size) {
            if (text.isNotEmpty()) text.append(' ')
            wordStarts[i - from] = text.length
            text.append(words[i])
        }

        return suspendCancellableCoroutine { continuation ->
            fun finish(result: String?) {
                if (continuation.isActive) continuation.resume(result)
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    // binarySearch returns the insertion point negated when the offset
                    // falls inside a word rather than exactly on its first character.
                    val hit = wordStarts.binarySearch(start)
                    val index = if (hit >= 0) hit else -hit - 2
                    if (index >= 0) onWord(from + index)
                }

                override fun onDone(utteranceId: String?) = finish(null)

                @Deprecated("Required by the base class", ReplaceWith(""))
                override fun onError(utteranceId: String?) = finish(genericFailure(language))

                override fun onError(utteranceId: String?, errorCode: Int) =
                    finish(describe(errorCode, language))

                // A stop we asked for is not a failure.
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(null)
            })

            continuation.invokeOnCancellation { tts.stop() }

            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "lector-$from") !=
                TextToSpeech.SUCCESS
            ) {
                finish(genericFailure(language))
            }
        }
    }

    private fun genericFailure(language: String?) =
        "The speech engine could not read this book in " +
            "${localeFor(language).displayLanguage}."

    /**
     * Turn an engine error code into something worth showing.
     *
     * The network cases are the ones that actually happen: a voice that looks
     * installed but synthesises server-side is useless to an app whose whole promise
     * is working offline, and the reader deserves to be told that rather than
     * watching pages turn in silence.
     */
    private fun describe(errorCode: Int, language: String?): String {
        val lang = localeFor(language).displayLanguage
        return when (errorCode) {
            TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT ->
                "The installed $lang voice needs the network to speak. Install offline " +
                    "$lang voice data in the system text-to-speech settings."

            TextToSpeech.ERROR_NOT_INSTALLED_YET ->
                "The $lang voice is still downloading. Try again in a moment."

            TextToSpeech.ERROR_SYNTHESIS ->
                "The speech engine could not synthesise this passage in $lang."

            else -> genericFailure(language)
        }
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    /** The EPUB's `dc:language`, falling back to the device locale. */
    private fun localeFor(language: String?): Locale =
        language?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
}
