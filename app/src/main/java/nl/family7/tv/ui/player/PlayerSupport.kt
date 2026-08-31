package nl.family7.tv.ui.player

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import nl.family7.tv.R

const val SEEK_BACK_MS = 10_000L
const val SEEK_FORWARD_MS = 30_000L

/**
 * ExoPlayer met de spoelstappen die de mediabediening en de afstandsbediening
 * gebruiken (de knoppen tonen deze waarden ook).
 */
fun buildPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(SEEK_BACK_MS)
        .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
        .build()
        .apply { playWhenReady = true }

/**
 * De officiele Media3 [PlayerView], met een eigen bedieningslayout op
 * 10-voet-formaat (zie res/layout/family7_*_controls.xml). PlayerControlView
 * blijft alle logica afhandelen: verborgen bediening komt in beeld bij de
 * eerste druk op OK of een richtingstoets, OK speelt en pauzeert, de bediening
 * verdwijnt na een paar seconden, en de mediatoetsen werken zoals gebruikelijk.
 *
 * @param layoutRes [R.layout.family7_player_view] of [R.layout.family7_live_player_view]
 */
@OptIn(UnstableApi::class)
fun buildPlayerView(
    context: Context,
    @LayoutRes layoutRes: Int,
    player: Player,
    onBack: () -> Unit,
    onControllerVisibilityChanged: (Boolean) -> Unit
): PlayerView {
    val view = LayoutInflater.from(context).inflate(layoutRes, null, false) as PlayerView
    view.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    view.setPlayer(player)
    view.findViewById<ImageButton>(R.id.family7_back)?.setOnClickListener { onBack() }

    val playPause = view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
    view.setControllerVisibilityListener(
        PlayerView.ControllerVisibilityListener { visibility ->
            val visible = visibility == View.VISIBLE
            // Bij het verschijnen meteen focus op afspelen/pauzeren, zodat de
            // D-pad direct bruikbaar is zonder eerst te moeten zoeken.
            if (visible) playPause?.requestFocus()
            onControllerVisibilityChanged(visible)
        }
    )
    return view
}

/** Vult de titelvelden in de bedieningslayout. */
fun PlayerView.setPlayerLabels(title: String, subtitle: String, liveTime: String? = null) {
    findViewById<TextView>(R.id.family7_title)?.text = title
    findViewById<TextView>(R.id.family7_subtitle)?.apply {
        text = subtitle
        visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
    }
    findViewById<TextView>(R.id.family7_live_time)?.apply {
        text = liveTime.orEmpty()
        visibility = if (liveTime.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}

/**
 * Koppelt een [MediaSession] aan de speler, zodat de systeem-mediabediening
 * ("Now playing", de mediabalk en de mediatoetsen van de afstandsbediening)
 * de weergave bestuurt en de juiste titel en omslag toont.
 */
@Composable
fun rememberMediaSession(sessionId: String, player: Player) {
    val view = LocalView.current
    DisposableEffect(sessionId, player) {
        val session = MediaSession.Builder(view.context.applicationContext, player)
            .setId(sessionId)
            .build()
        onDispose { session.release() }
    }
}

/** Houdt het scherm aan zolang er afgespeeld wordt. */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Pauzeert bij het verlaten van de voorgrond en hervat bij terugkeer. */
@Composable
fun PauseOnBackground(player: Player) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnStart = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> if (resumeOnStart) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
