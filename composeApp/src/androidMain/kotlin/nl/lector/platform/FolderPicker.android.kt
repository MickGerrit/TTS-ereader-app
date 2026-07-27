package nl.lector.platform

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import nl.lector.data.FolderGrant

@Composable
actual fun rememberFolderPicker(onPicked: (FolderGrant) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Persist across reboots, and take write access so sidecars can be written
        // back next to each book.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        onPicked(FolderGrant(locator = uri.toString(), label = folderLabel(uri)))
    }
    return { launcher.launch(null) }
}

/** "Internal storage / Books" out of a tree document id like `primary:Books`. */
private fun folderLabel(uri: Uri): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return uri.lastPathSegment.orEmpty()
    val (volume, path) = id.split(':', limit = 2).let {
        it.firstOrNull().orEmpty() to it.getOrNull(1).orEmpty()
    }
    val volumeLabel = if (volume.equals("primary", true)) "Internal storage" else volume
    return if (path.isBlank()) volumeLabel else "$volumeLabel / ${path.replace('/', '/')}"
}
