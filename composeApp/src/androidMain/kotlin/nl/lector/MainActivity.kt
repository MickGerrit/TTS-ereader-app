package nl.lector

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import nl.lector.data.AndroidStorage
import nl.lector.data.OpenLibraryCovers
import nl.lector.data.SafLibrarySource
import nl.lector.data.SafSidecarWriter
import nl.lector.engine.AndroidPlaybackHost
import nl.lector.engine.AndroidTts
import nl.lector.state.AndroidPrefs
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** AppCompat, because Readium's navigator is a Fragment and needs a FragmentActivity. */
class MainActivity : AppCompatActivity() {

    private lateinit var tts: AndroidTts

    /**
     * The listening notification is the lock-screen player, so on Android 13 and up
     * it needs permission. Denying it costs the controls, not the audio: the
     * foreground service still runs.
     */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 1)
        }
    }

    override fun onDestroy() {
        // The speech engine holds a service connection; leaking it keeps the
        // process's audio focus alive after the activity is gone.
        tts.shutdown()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askForNotifications()
        val prefs = AndroidPrefs(applicationContext)
        val library = SafLibrarySource(applicationContext)
        tts = AndroidTts(applicationContext)
        setContent {
            App(
                prefs = prefs,
                library = library,
                sidecar = SafSidecarWriter(applicationContext),
                engine = tts,
                coverSource = OpenLibraryCovers(applicationContext),
                storage = AndroidStorage(applicationContext),
                playback = AndroidPlaybackHost(applicationContext),
                now = { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) },
            )
        }
    }
}
