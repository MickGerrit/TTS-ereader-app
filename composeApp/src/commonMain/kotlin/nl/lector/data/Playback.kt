package nl.lector.data

/**
 * What is playing, in the terms the outside world needs it: a lock screen, a
 * notification, a car head unit. No app types, so the platform side can be a thin
 * translation into whatever that platform's media session wants.
 */
data class NowPlaying(
    val title: String,
    val author: String,
    val chapter: String?,
    val coverPath: String?,
    val playing: Boolean,
)

/** Everything the world outside the app can ask playback to do. */
enum class PlaybackCommand { Play, Pause, Next, Previous, Stop }

/**
 * The seam between playback and the operating system's idea of playback.
 *
 * The app tells it what is playing; it tells the app what the reader pressed,
 * wherever they pressed it — the notification, the lock screen, a headphone button
 * (PRD §6.6).
 */
interface PlaybackHost {
    /** Start or refresh the session and its notification. */
    fun show(now: NowPlaying)

    /** No session, no notification: nothing is playing and nothing pretends to be. */
    fun hide()

    /** Where transport events arrive. Called once, by the app, at startup. */
    fun onCommand(handler: (PlaybackCommand) -> Unit)
}

/** For previews, tests, and any platform with no media session wired up. */
class NoPlaybackHost : PlaybackHost {
    override fun show(now: NowPlaying) = Unit
    override fun hide() = Unit
    override fun onCommand(handler: (PlaybackCommand) -> Unit) = Unit
}
