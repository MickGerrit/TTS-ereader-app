package nl.lector.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import nl.lector.MainActivity
import nl.lector.R
import nl.lector.data.NowPlaying
import nl.lector.data.PlaybackCommand
import java.io.File

/**
 * The foreground service that makes listening survive leaving the app (Epic 4).
 *
 * It owns three things the app cannot own from a composition: the notification that
 * keeps the process alive with the screen off, the `MediaSession` that puts controls
 * on the lock screen and gives headphone buttons somewhere to land, and audio focus.
 *
 * ponytail: the speech loop itself still lives in the composition, and this service
 * is what stops Android from reclaiming it. That covers screen off, another app in
 * front, and the lock screen — everything the epic asks for. It does not cover the
 * activity being destroyed outright, which is the point at which the loop would have
 * to move in here too. Move it if a real device shows playback dying on you.
 */
class PlaybackService : Service() {

    private var session: MediaSession? = null
    private var focus: AudioFocusRequest? = null

    /** True when we paused because something else took the audio, not because you did. */
    private var pausedForFocus = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(Channel, "Listening", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The book being read aloud"
                setShowBadge(false)
            },
        )

        session = MediaSession(this, "Lector").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = send(PlaybackCommand.Play)
                override fun onPause() = send(PlaybackCommand.Pause)
                override fun onSkipToNext() = send(PlaybackCommand.Next)
                override fun onSkipToPrevious() = send(PlaybackCommand.Previous)
                override fun onStop() = send(PlaybackCommand.Stop)
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A command from one of our own notification buttons, rather than a refresh.
        intent?.getStringExtra(ExtraCommand)?.let { name ->
            runCatching { PlaybackCommand.valueOf(name) }.getOrNull()?.let(::send)
            return START_STICKY
        }

        val now = intent?.toNowPlaying() ?: return START_NOT_STICKY
        if (now.playing) requestFocus() else abandonFocus()

        session?.setMetadata(now.toMetadata())
        session?.setPlaybackState(now.toPlaybackState())

        ServiceCompat.startForeground(
            this, NotificationId, notification(now),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
        return START_STICKY
    }

    override fun onDestroy() {
        abandonFocus()
        session?.isActive = false
        session?.release()
        session = null
        super.onDestroy()
    }

    // ── audio focus ───────────────────────────────────────────────────────

    /**
     * ponytail: transient loss pauses rather than ducks, including the duck-allowed
     * case. Speech at half volume under a notification chime is not listenable, and
     * the engine's per-utterance volume only takes effect on the next sentence
     * anyway. Revisit when the neural engine lands, which can change gain mid-stream.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedForFocus = false
                send(PlaybackCommand.Pause)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                pausedForFocus = true
                send(PlaybackCommand.Pause)
            }

            AudioManager.AUDIOFOCUS_GAIN -> if (pausedForFocus) {
                pausedForFocus = false
                send(PlaybackCommand.Play)
            }
        }
    }

    private fun requestFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Spoken word, not music: this is what tells the system to pause
                    // us for a call and to route us like speech.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focus = request
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(request)
    }

    private fun abandonFocus() {
        val request = focus ?: return
        focus = null
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(request)
    }

    // ── notification ──────────────────────────────────────────────────────

    private fun notification(now: NowPlaying): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(this, Channel)
            .setContentTitle(now.title)
            .setContentText(listOfNotNull(now.author, now.chapter).joinToString(" · "))
            .setSmallIcon(R.drawable.ic_listening)
            .setContentIntent(open)
            .setOngoing(now.playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action("Previous chapter", PlaybackCommand.Previous))
            .addAction(
                if (now.playing) {
                    action("Pause", PlaybackCommand.Pause)
                } else {
                    action("Play", PlaybackCommand.Play)
                },
            )
            .addAction(action("Next chapter", PlaybackCommand.Next))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    // Which actions stay visible when the notification is collapsed.
                    .setShowActionsInCompactView(0, 1, 2),
            )

        now.coverPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                ?.let { builder.setLargeIcon(it) }
        }
        return builder.build()
    }

    private fun action(label: String, command: PlaybackCommand): Notification.Action =
        Notification.Action.Builder(
            null,
            label,
            PendingIntent.getService(
                this,
                command.ordinal,
                Intent(this, PlaybackService::class.java).putExtra(ExtraCommand, command.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()

    private fun NowPlaying.toMetadata(): MediaMetadata =
        MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, author)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, chapter ?: title)
            .apply {
                coverPath?.let { path ->
                    runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                        ?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
                }
            }
            .build()

    private fun NowPlaying.toPlaybackState(): PlaybackState =
        PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
            )
            .setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                1f,
            )
            .build()

    private fun send(command: PlaybackCommand) {
        commands?.invoke(command)
    }

    companion object {
        private const val Channel = "listening"
        private const val NotificationId = 1
        private const val ExtraCommand = "command"

        /**
         * Where transport events go. Same process as the app by construction — the
         * service exists to keep that process alive — so a plain reference is the
         * whole binding, no binder and no message plumbing.
         */
        var commands: ((PlaybackCommand) -> Unit)? = null

        fun intent(context: Context, now: NowPlaying): Intent =
            Intent(context, PlaybackService::class.java)
                .putExtra("title", now.title)
                .putExtra("author", now.author)
                .putExtra("chapter", now.chapter)
                .putExtra("cover", now.coverPath)
                .putExtra("playing", now.playing)

        private fun Intent.toNowPlaying(): NowPlaying? {
            val title = getStringExtra("title") ?: return null
            return NowPlaying(
                title = title,
                author = getStringExtra("author").orEmpty(),
                chapter = getStringExtra("chapter"),
                coverPath = getStringExtra("cover")?.takeIf { File(it).exists() },
                playing = getBooleanExtra("playing", false),
            )
        }
    }
}
