package nl.lector.reader

import androidx.compose.runtime.Composable
import nl.lector.state.LectorState

/**
 * Spike B (TECHNICALPRD §12): does the real EPUB engine render our design?
 *
 * Deliberately a separate screen from [nl.lector.screen.ReaderScreen] so the working
 * app stays working while the answer is unknown, and so this is cheap to delete if
 * the answer is no. Platform-specific because Readium ships as parallel native
 * toolkits — the Swift one lands behind this same declaration.
 */
@Composable
expect fun ReaderEngineSpike(state: LectorState, onBack: () -> Unit)
