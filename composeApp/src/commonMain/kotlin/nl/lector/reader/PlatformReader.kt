package nl.lector.reader

import androidx.compose.runtime.Composable
import nl.lector.state.LectorState

/**
 * The reader, backed by the platform's EPUB engine.
 *
 * Readium ships as parallel native toolkits, so this is the seam: the Android actual
 * drives `readium-navigator`, the iOS one will drive the Swift toolkit, and both wrap
 * the same shared [nl.lector.screen.ReaderScreen] chrome.
 */
@Composable
expect fun PlatformReader(
    state: LectorState,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onAppearance: () -> Unit,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
)

/** Page turns, driven from the transport on the Listening screen. */
interface ReaderController {
    fun goForward()
    fun goBackward()

    /** Jump to a position the engine itself produced, for the Contents sheet. */
    fun goTo(locatorJson: String)

    /**
     * Jump to a fraction of the whole book. Only for positions that arrived as a
     * percentage and nothing better — a KOReader sidecar, which is percentage-level
     * by definition (HANDOFF §6.6).
     */
    fun goToProgression(progression: Float)
}
