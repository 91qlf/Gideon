package app.gideon.ui.feed

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gideon.data.NewsItem
import coil3.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Stable
class VideoController {
    var player: YouTubePlayer? = null
    var isPlaying by mutableStateOf(false)
    var started by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var position by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f)

    val progress: Float get() = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f

    fun toggle() {
        val p = player ?: return
        if (isPlaying) p.pause() else p.play()
    }

    fun reset() {
        player = null
        isPlaying = false
        started = false
        position = 0f
        duration = 0f
    }
}

/** Where the video sits on the page: full-bleed 9:16 for Shorts, 16:9 slightly above centre otherwise. */
@Composable
fun VideoFrame(item: NewsItem, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w: Dp = maxWidth
        if (item.isShort) {
            val h = minOf(maxHeight, w * 16f / 9f)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content(Modifier.size(w, h))
            }
        } else {
            Box(Modifier.fillMaxSize().padding(bottom = maxHeight * 0.18f), contentAlignment = Alignment.Center) {
                content(Modifier.size(w, w * 9f / 16f))
            }
        }
    }
}

@Composable
fun YouTubeLayer(item: NewsItem, active: Boolean, controller: VideoController, soundOn: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val sound by rememberUpdatedState(soundOn)
    val videoId = item.videoId ?: return

    LaunchedEffect(active) { if (!active) controller.reset() }

    if (active && !controller.failed) {
        VideoFrame(item) { frame ->
            AndroidView(
                factory = { ctx ->
                    YouTubePlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        enableAutomaticInitialization = false
                        lifecycleOwner.lifecycle.addObserver(this)
                        val options = IFramePlayerOptions.Builder(ctx)
                            .controls(0)
                            .rel(0)
                            .ivLoadPolicy(3)
                            .ccLoadPolicy(0)
                            .fullscreen(0)
                            .build()
                        initialize(object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                controller.player = youTubePlayer
                                if (!sound) youTubePlayer.mute()
                                youTubePlayer.loadVideo(videoId, 0f)
                            }

                            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                controller.isPlaying = state == PlayerConstants.PlayerState.PLAYING
                                if (state == PlayerConstants.PlayerState.PLAYING) controller.started = true
                                if (state == PlayerConstants.PlayerState.ENDED) {
                                    youTubePlayer.seekTo(0f)
                                    youTubePlayer.play()
                                }
                            }

                            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                                controller.position = second
                            }

                            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                                controller.duration = duration
                            }

                            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                controller.failed = true
                            }
                        }, true, options)
                    }
                },
                modifier = frame,
                onRelease = { view ->
                    lifecycleOwner.lifecycle.removeObserver(view)
                    view.release()
                },
            )
        }
    }

    LaunchedEffect(soundOn, controller.started) {
        val p = controller.player ?: return@LaunchedEffect
        if (soundOn) p.unMute() else p.mute()
    }

    // Sharp thumbnail on top of the player until the first frame plays.
    val coverAlpha by animateFloatAsState(
        if (active && controller.started && !controller.failed) 0f else 1f,
        tween(380),
        label = "cover",
    )
    if (coverAlpha > 0.01f) {
        VideoFrame(item) { frame ->
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = frame.graphicsLayer { alpha = coverAlpha },
            )
        }
    }
}
