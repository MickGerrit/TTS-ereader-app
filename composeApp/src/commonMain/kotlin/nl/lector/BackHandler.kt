package nl.lector

import androidx.compose.runtime.Composable

/** System back. Android's predictive-back gesture; a no-op where there is none. */
@Composable
expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)
