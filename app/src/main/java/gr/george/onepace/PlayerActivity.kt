package gr.george.onepace

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import gr.george.onepace.data.Episode
import gr.george.onepace.data.Library
import gr.george.onepace.data.LibraryRepository
import gr.george.onepace.data.ProgressStore
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ID = "episode_id"
        const val EXTRA_FROM_START = "from_start"
        private const val SAVE_EVERY_MS = 5_000L
    }

    private var player: ExoPlayer? = null
    private var library: Library? = null
    private var currentId: String? = null

    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val periodicSave = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProgressStore.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            setShowSubtitleButton(true)
            setShowNextButton(true)
            setShowPreviousButton(true)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(pad * 2, pad, pad * 2, pad)
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }

        val root = FrameLayout(this).apply {
            addView(
                playerView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                titleView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                ),
            )
        }
        setContentView(root)

        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                titleView.visibility = visibility
                if (visibility != View.VISIBLE) hideSystemBars()
            }
        )
        hideSystemBars()

        val startId = intent.getStringExtra(EXTRA_ID)
        val fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false)

        lifecycleScope.launch {
            val lib = try {
                LibraryRepository.ensureLoaded(applicationContext)
            } catch (e: Exception) {
                null
            }
            if (lib == null || lib.allEpisodes.isEmpty()) {
                Toast.makeText(this@PlayerActivity, "Δεν βρέθηκαν επεισόδια", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            library = lib
            val idx = startId?.let { lib.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            startPlayer(lib, idx, fromStart)
        }
    }

    private fun startPlayer(lib: Library, startIndex: Int, fromStart: Boolean) {
        val p = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        // Υπότιτλοι ανοιχτοί by default (ελληνικά > αγγλικά > ό,τι υπάρχει)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguages("el", "en")
            .setSelectUndeterminedTextLanguage(true)
            .build()

        // Όλη η σειρά σαν playlist: στο τέλος κάθε επεισοδίου παίζει αυτόματα το επόμενο.
        val items = lib.allEpisodes.map { toMediaItem(it) }
        val startEp = lib.allEpisodes[startIndex]
        val startPos = if (fromStart) 0L else resumePosition(startEp.id)
        p.setMediaItems(items, startIndex, startPos)

        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    currentId?.let { ProgressStore.markWatched(listOf(it)) }
                }
                currentId = mediaItem?.mediaId
                updateTitle()
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    val resume = mediaItem?.mediaId?.let { resumePosition(it) } ?: 0L
                    if (resume > 0) p.seekTo(resume)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentId?.let { ProgressStore.markWatched(listOf(it)) }
                    finish()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress()
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Σφάλμα αναπαραγωγής: ${error.errorCodeName}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        })

        currentId = startEp.id
        player = p
        playerView.player = p
        updateTitle()
        p.prepare()
        p.playWhenReady = true

        handler.removeCallbacks(periodicSave)
        handler.postDelayed(periodicSave, SAVE_EVERY_MS)
    }

    private fun toMediaItem(ep: Episode): MediaItem {
        val subs = ep.subtitles.mapIndexed { i, s ->
            MediaItem.SubtitleConfiguration.Builder(s.uri)
                .setMimeType(s.mimeType)
                .setLanguage(s.language)
                .setLabel(s.label)
                .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        return MediaItem.Builder()
            .setUri(ep.uri)
            .setMediaId(ep.id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ep.title).build())
            .setSubtitleConfigurations(subs)
            .build()
    }

    private fun resumePosition(id: String): Long =
        ProgressStore.get(id)?.resumePositionMs ?: 0L

    private fun saveProgress() {
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId ?: return
        val duration = p.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        ProgressStore.save(id, p.currentPosition, duration)
    }

    private fun updateTitle() {
        val ep = library?.find(currentId)
        titleView.text = if (ep != null) "${ep.arc}  ·  ${ep.title}" else ""
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        player?.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodicSave)
        saveProgress()
        player?.release()
        player = null
        super.onDestroy()
    }
}
