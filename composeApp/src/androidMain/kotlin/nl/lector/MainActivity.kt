package nl.lector

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import nl.lector.data.SafLibrarySource
import nl.lector.data.SafSidecarWriter
import nl.lector.engine.AndroidTts
import nl.lector.state.AndroidPrefs
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** AppCompat, because Readium's navigator is a Fragment and needs a FragmentActivity. */
class MainActivity : AppCompatActivity() {

    private lateinit var tts: AndroidTts

    override fun onDestroy() {
        // The speech engine holds a service connection; leaking it keeps the
        // process's audio focus alive after the activity is gone.
        tts.shutdown()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = AndroidPrefs(applicationContext)
        val library = SafLibrarySource(applicationContext)
        tts = AndroidTts(applicationContext)
        setContent {
            App(
                prefs = prefs,
                library = library,
                sidecar = SafSidecarWriter(applicationContext),
                engine = tts,
                now = { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) },
            )
        }
    }
}
