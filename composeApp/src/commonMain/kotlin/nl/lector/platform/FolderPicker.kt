package nl.lector.platform

import androidx.compose.runtime.Composable
import nl.lector.data.FolderGrant

/**
 * Opens the platform's own folder picker and persists the grant.
 *
 * The prototype draws a folder tree because a browser has no picker to call. A real
 * app must not: SAF (and `UIDocumentPicker` on iOS) owns that UI, and drawing our
 * own would be both a lie and a permission we could not obtain.
 */
@Composable
expect fun rememberFolderPicker(onPicked: (FolderGrant) -> Unit): () -> Unit
