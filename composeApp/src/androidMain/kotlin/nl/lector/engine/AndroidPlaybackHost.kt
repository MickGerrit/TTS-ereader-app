package nl.lector.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import nl.lector.data.NowPlaying
import nl.lector.data.PlaybackCommand
import nl.lector.data.PlaybackHost

/** Drives [PlaybackService] from the app's shared code. */
class AndroidPlaybackHost(private val context: Context) : PlaybackHost {

    override fun show(now: NowPlaying) {
        ContextCompat.startForegroundService(context, PlaybackService.intent(context, now))
    }

    override fun hide() {
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    override fun onCommand(handler: (PlaybackCommand) -> Unit) {
        PlaybackService.commands = handler
    }
}
