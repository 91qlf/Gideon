package app.gideon.ui.feed

import android.content.Context
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import app.gideon.data.NewsItem
import coil3.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay

/** Gideon's own playback state + controls, whatever engine renders the video. */
@Stable
class VideoController {
    var isPlaying by mutableStateOf(false)
    var started by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var userPaused by mutableStateOf(false)
    var position by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f)

    internal var onPlay: (() -> Unit)? = null
    internal var onPause: (() -> Unit)? = null
    internal var onSeek: ((Float) -> Unit)? = null

    val progress: Float get() = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f

    fun toggle() {
        if (ended) {
            replay()
            return
        }
        if (isPlaying) {
            userPaused = true
            onPause?.invoke()
        } else {
            userPaused = false
            onPlay?.invoke()
        }
    }

    fun replay() {
        ended = false
        userPaused = false
        position = 0f
        onSeek?.invoke(0f)
        onPlay?.invoke()
    }

    fun seekTo(fraction: Float) {
        if (duration > 0f) {
            position = fraction * duration
            onSeek?.invoke(fraction * duration)
        }
    }

    fun reset() {
        isPlaying = false
        started = false
        ended = false
        userPaused = false
        position = 0f
        duration = 0f
        onPlay = null
        onPause = null
        onSeek = null
    }
}

// ------------------------------------------------------------------ YouTube engine pool

/**
 * A YouTube engine (WebView + IFrame API) that is created once and reused. Being warm, it
 * starts a video in a fraction of the time a fresh embed needs, and it can buffer the next
 * video in the background (muted, paused on its first frame).
 */
class PooledYouTube internal constructor(val view: YouTubePlayerView) {
    var player: YouTubePlayer? = null
        private set
    internal var videoId: String? = null
    internal var loadedVideo: String? = null
    internal var sink: VideoController? = null

    /** Only the engine of the page on screen may play; any other one is held paused. */
    internal var active = false
    private var pending: ((YouTubePlayer) -> Unit)? = null

    internal fun whenReady(block: (YouTubePlayer) -> Unit) {
        val p = player
        if (p != null) block(p) else pending = block
    }

    internal fun clear() {
        sink = null
        active = false
        pending = null
        player?.let {
            it.mute()
            it.pause()
        }
    }

    internal val listener = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
            pending?.invoke(youTubePlayer)
            pending = null
        }

        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            val s = sink
            if (s == null || !active) {
                // Background engine (idle or buffering the next video): keep it silent and still.
                if (state == PlayerConstants.PlayerState.PLAYING) youTubePlayer.pause()
                return
            }
            s.isPlaying = state == PlayerConstants.PlayerState.PLAYING
            when (state) {
                PlayerConstants.PlayerState.PLAYING -> {
                    s.started = true
                    s.ended = false
                }
                PlayerConstants.PlayerState.ENDED -> s.ended = true
                else -> Unit
            }
        }

        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
            if (active) sink?.position = second
        }

        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
            sink?.duration = duration
        }

        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
            sink?.failed = true
        }
    }
}

class YouTubePool(private val context: Context, private val lifecycle: Lifecycle) {
    private val idle = ArrayDeque<PooledYouTube>()
    private val all = mutableListOf<PooledYouTube>()

    /** Creates engines ahead of time so the first video starts fast too. */
    fun warmUp(count: Int) {
        while (all.size < count) idle.addLast(create())
    }

    fun acquire(): PooledYouTube = idle.removeFirstOrNull() ?: create()

    fun recycle(p: PooledYouTube) {
        p.clear()
        (p.view.parent as? ViewGroup)?.removeView(p.view)
        if (p !in idle) idle.addLast(p)
    }

    fun release() {
        all.forEach {
            lifecycle.removeObserver(it.view)
            it.view.release()
        }
        all.clear()
        idle.clear()
    }

    private fun create(): PooledYouTube {
        val view = YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val pooled = PooledYouTube(view)
        lifecycle.addObserver(view)
        val options = IFramePlayerOptions.Builder(context)
            .controls(0)
            .rel(0)
            .ivLoadPolicy(3)
            .ccLoadPolicy(0)
            .fullscreen(0)
            .build()
        view.initialize(pooled.listener, true, options)
        all += pooled
        return pooled
    }
}

val LocalYouTubePool = staticCompositionLocalOf<YouTubePool?> { null }

// ------------------------------------------------------------------ layout

/** Where the video sits: full-bleed when vertical, 16:9 band slightly above centre otherwise. */
@Composable
fun VideoFrame(aspect: Float, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w: Dp = maxWidth
        if (aspect < 1f) {
            val h = minOf(maxHeight, w / aspect)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content(Modifier.size(w, h))
            }
        } else {
            Box(Modifier.fillMaxSize().padding(bottom = maxHeight * 0.18f), contentAlignment = Alignment.Center) {
                content(Modifier.size(w, w / aspect))
            }
        }
    }
}

fun NewsItem.defaultAspect(): Float = if (isShort) 9f / 16f else 16f / 9f

/** Sharp thumbnail kept above the video until the first frame actually plays. */
@Composable
private fun Cover(item: NewsItem, aspect: Float, visible: Boolean) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "cover")
    if (alpha > 0.01f) {
        VideoFrame(aspect) { frame ->
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = frame.graphicsLayer { this.alpha = alpha },
            )
        }
    }
}

// ------------------------------------------------------------------ YouTube layer

@Composable
fun YouTubeLayer(item: NewsItem, active: Boolean, preload: Boolean, controller: VideoController, soundOn: Boolean) {
    val pool = LocalYouTubePool.current ?: return
    val videoId = item.videoId ?: return
    val wanted = active || preload
    var pooled by remember(item.id) { mutableStateOf<PooledYouTube?>(null) }
    val sound by rememberUpdatedState(soundOn)

    DisposableEffect(item.id, wanted) {
        if (!wanted) return@DisposableEffect onDispose { }
        val p = pool.acquire()
        p.sink = controller
        p.active = active
        pooled = p
        controller.onPlay = { p.player?.play() }
        controller.onPause = { p.player?.pause() }
        controller.onSeek = { s -> p.player?.seekTo(s) }
        onDispose {
            pool.recycle(p)
            if (pooled === p) pooled = null
            controller.reset()
        }
    }

    val p = pooled
    LaunchedEffect(p, active) {
        val engine = p ?: return@LaunchedEffect
        engine.active = active
        engine.videoId = videoId
        engine.whenReady { yt ->
            if (engine.videoId != videoId) return@whenReady
            if (engine.loadedVideo != videoId) {
                engine.loadedVideo = videoId
                if (active && sound) yt.unMute() else yt.mute()
                // Active: plays at once. Next page: buffers silently and is held on its first frame.
                yt.loadVideo(videoId, 0f)
            } else if (active) {
                if (sound) yt.unMute() else yt.mute()
                if (!controller.userPaused && !controller.ended) yt.play()
            } else {
                yt.pause()
            }
        }
    }

    LaunchedEffect(soundOn, p, active) {
        if (active) p?.player?.let { if (soundOn) it.unMute() else it.mute() }
    }

    if (p != null) {
        VideoFrame(item.defaultAspect()) { frame ->
            AndroidView(
                factory = { ctx -> FrameLayout(ctx) },
                update = { container ->
                    if (p.view.parent !== container) {
                        (p.view.parent as? ViewGroup)?.removeView(p.view)
                        container.addView(p.view)
                    }
                },
                modifier = frame,
            )
        }
    }
    // Our own cover hides YouTube's overlays whenever the video is not actually running.
    Cover(
        item,
        item.defaultAspect(),
        visible = !(active && controller.started) || controller.failed || controller.userPaused || controller.ended,
    )
}

// ------------------------------------------------------------------ native layer (ExoPlayer)

/** Direct MP4/HLS files are played by Media3 ExoPlayer: instant start, buffered ahead. */
@Composable
fun NativeVideoLayer(item: NewsItem, active: Boolean, preload: Boolean, controller: VideoController, soundOn: Boolean) {
    val context = LocalContext.current
    val url = item.streamUrl ?: return
    val wanted = active || preload
    var player by remember(item.id) { mutableStateOf<ExoPlayer?>(null) }
    var aspect by remember(item.id) { mutableFloatStateOf(item.defaultAspect()) }
    val isActive by rememberUpdatedState(active)

    DisposableEffect(item.id, wanted) {
        if (!wanted) return@DisposableEffect onDispose { }
        val exo = ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2_000, 20_000, 350, 1_000)
                    .build()
            )
            .build()
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.volume = 0f
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                controller.isPlaying = isPlaying
                if (isPlaying && isActive) controller.started = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                controller.failed = true
            }
        }
        exo.addListener(listener)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        controller.onPlay = { exo.play() }
        controller.onPause = { exo.pause() }
        controller.onSeek = { s -> exo.seekTo((s * 1000).toLong()) }
        player = exo
        onDispose {
            exo.removeListener(listener)
            exo.release()
            if (player === exo) player = null
            controller.reset()
        }
    }

    LaunchedEffect(player, active, soundOn) {
        val exo = player ?: return@LaunchedEffect
        exo.volume = if (active && soundOn) 1f else 0f
        exo.playWhenReady = active && !controller.userPaused
    }

    // Pause in the background, resume when the app comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val exo = player
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exo?.pause()
                Lifecycle.Event.ON_START -> if (isActive && !controller.userPaused) exo?.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, active) {
        val exo = player ?: return@LaunchedEffect
        while (active) {
            controller.position = exo.currentPosition / 1000f
            if (exo.duration > 0) controller.duration = exo.duration / 1000f
            delay(200)
        }
    }

    val exo = player
    if (exo != null) {
        VideoFrame(aspect) { frame ->
            key(exo) {
                AndroidView(
                    factory = { ctx -> TextureView(ctx).also { exo.setVideoTextureView(it) } },
                    modifier = frame,
                )
            }
        }
    }
    Cover(item, aspect, visible = !(active && controller.started) || controller.failed)
}
